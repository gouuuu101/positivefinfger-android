package com.positivefinger.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Same Supabase RPC contract as PositiveCloud.swift. Only aggregate event UUIDs leave the IME. */
class CloudClient private constructor(private val context: Context) {
    companion object {
        private const val BASE = "https://ctyzaihzltcncspujmnb.supabase.co"
        // Publishable client key, also present in iOS. Authorization is enforced by the server.
        private const val KEY = "sb_publishable_iMRH0X5OmCc3yy7BkNMAgg_HTKChfT1"
        @Volatile private var instance: CloudClient? = null
        fun get(context: Context): CloudClient = instance ?: synchronized(this) {
            instance ?: CloudClient(context.applicationContext).also { instance = it }
        }
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val store = ActivityStore.get(context)
    private val secure = SecureSession(context)
    private var session: JSONObject? = null
    @Volatile var state: JSONObject? = null; private set
    @Volatile var busy = false; private set
    @Volatile var signedIn = false; private set
    private var generation = 0
    init {
        try { session = secure.read()?.let { JSONObject(it) }; signedIn = session != null
            store.account(session?.getJSONObject("user")?.getString("id"))
        } catch (_: Exception) { secure.clear(); session = null; signedIn = false; store.account(null) }
    }
    private class Rejected(val status: Int, message: String): Exception(message)
    private fun request(path: String, body: JSONObject? = JSONObject(), authorized: Boolean = true): JSONObject {
        if (authorized) refresh()
        val connection = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            if (authorized) connection.setRequestProperty("Authorization", "Bearer " + (session ?: error("ログインしてください")).getString("access_token"))
            if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body.toString().toByteArray()) } }
            val status = connection.responseCode
            val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val objectValue = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (status !in 200..299) throw Rejected(status, objectValue.optString("msg", objectValue.optString("message", "通信に失敗しました ($status)")))
            return objectValue
        } finally { connection.disconnect() }
    }
    private fun save(value: JSONObject) {
        if (!value.has("expires_at")) value.put("expires_at", System.currentTimeMillis() / 1000 + value.optLong("expires_in", 3600))
        secure.write(value.toString()); session = value; signedIn = true
        store.account(value.getJSONObject("user").getString("id"))
    }
    private fun refresh() {
        val old = session ?: error("ログインしてください")
        if (old.optLong("expires_at") > System.currentTimeMillis() / 1000 + 90) return
        try { save(request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", old.getString("refresh_token")), false)) }
        catch (e: Rejected) { if (e.status == 400 || e.status == 401) clearSession(); throw e }
    }
    private fun clearSession() {
        generation++; session = null; signedIn = false; state = null; secure.clear(); store.account(null)
    }
    fun run(task: CloudClient.() -> String?, done: (String?) -> Unit) {
        if (busy) return
        busy = true
        executor.execute {
            val result = try { task() } catch (e: Exception) { e.localizedMessage ?: "通信に失敗しました" }
            handler.post { busy = false; done(result) }
        }
    }
    fun authenticate(email: String, password: String, signup: Boolean): String? {
        val value = request(if (signup) "/auth/v1/signup" else "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password), false)
        if (!value.has("access_token")) return "確認メールを送りました。メールを確認してからログインしてください。"
        save(value); sync(); return null
    }
    fun logout(): String? { clearSession(); return null }
    fun sync(): String? {
        if (!signedIn) return null
        val user = session!!.getJSONObject("user").getString("id")
        val currentGeneration = generation
        for (id in store.queued().take(30)) {
            val result = request("/rest/v1/rpc/pf_action", JSONObject().put("p_action", "expression").put("p_request", id))
            if (result.has("awarded") && !result.optBoolean("awarded") && !result.optBoolean("capped")) break
            store.acknowledge(id)
        }
        val next = request("/rest/v1/rpc/pf_bootstrap")
        check(next.getJSONObject("profile").getString("id") == user && currentGeneration == generation) { "アカウントが変更されました" }
        state = next; store.applyCloud(next)
        return null
    }
    fun action(action: String, item: String? = null): String? {
        val user = session?.getJSONObject("user")?.getString("id") ?: error("ログインしてください")
        val key = "pending.$user"
        val intent = "$action:${item ?: ""}"
        val previous = store.text(key)
        val pending = if (previous.isEmpty()) JSONObject().put("intent", intent).put("id", UUID.randomUUID().toString()) else JSONObject(previous)
        check(pending.getString("intent") == intent) { "前の操作の結果が未確認です。同じ操作をもう一度押してください。" }
        store.text(key, pending.toString(), true)
        val body = JSONObject().put("p_action", action).put("p_request", pending.getString("id"))
        if (item != null) body.put("p_item", item)
        val result = try { request("/rest/v1/rpc/pf_action", body) } catch (e: Rejected) {
            // Only definitive application rejections are cleared; timeouts/5xx retain the UUID.
            if (e.status in listOf(400, 401, 403, 404, 409, 422)) store.text(key, "", true)
            throw e
        }
        store.text(key, "", true); sync()
        val prize = result.optString("item")
        return if (action == "gacha" && prize.isNotEmpty()) "${Pop.names[ActivityStore.characters.indexOf(prize).coerceAtLeast(0)]}がやってきた！ずかんから相棒に選べるよ。" else null
    }
    fun profile(name: String, ranking: Boolean, background: String, title: String): String? {
        check(name.codePointCount(0, name.length) in 2..16) { "ニックネームは2〜16文字にしてください" }
        request("/rest/v1/rpc/pf_update_profile", JSONObject().put("p_name", name.trim()).put("p_ranking", ranking).put("p_background", background).put("p_title", title))
        sync(); return "保存しました"
    }
    fun leaderboard(): JSONObject = request("/rest/v1/rpc/pf_leaderboard")
    fun providers(): List<String> {
        val external = request("/auth/v1/settings", null, false).optJSONObject("external") ?: JSONObject()
        return listOf("apple", "google", "custom:line").filter { external.optBoolean(it) }
    }
    fun oauth(provider: String): String {
        val bytes = ByteArray(32); SecureRandom().nextBytes(bytes)
        fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val verifier = encode(bytes)
        secure.writeOAuth(JSONObject().put("verifier", verifier).put("created", System.currentTimeMillis()).toString())
        val challenge = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        return Uri.parse("$BASE/auth/v1/authorize").buildUpon().appendQueryParameter("provider", provider)
            .appendQueryParameter("redirect_to", "positivefinger://auth/callback")
            .appendQueryParameter("code_challenge", challenge).appendQueryParameter("code_challenge_method", "s256").build().toString()
    }
    fun callback(uri: Uri): String? {
        require(uri.scheme == "positivefinger" && uri.host == "auth" && uri.path == "/callback")
        val saved = JSONObject(secure.readOAuth() ?: error("ログインをやり直してください"))
        check(System.currentTimeMillis() - saved.getLong("created") in 0..600_000) { "ログインの有効期限が切れました" }
        val code = uri.getQueryParameter("code") ?: error("ログイン結果を確認できませんでした")
        save(request("/auth/v1/token?grant_type=pkce", JSONObject().put("auth_code", code).put("code_verifier", saved.getString("verifier")), false))
        secure.writeOAuth(null); sync(); return null
    }
}

/** Android Keystore AES/GCM protects refresh tokens at rest; backups are disabled. */
private class SecureSession(context: Context) {
    private val prefs = context.getSharedPreferences("secure-session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey("pf-session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("pf-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    private fun put(name: String, value: String?) {
        if (value == null) { check(prefs.edit().remove(name).commit()); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray())
        check(prefs.edit().putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
    private fun get(name: String): String? {
        val value = prefs.getString(name, null) ?: return null
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }
    fun write(value: String) = put("session", value)
    fun read() = get("session")
    fun clear() { check(prefs.edit().clear().commit()) }
    fun writeOAuth(value: String?) = put("oauth", value)
    fun readOAuth() = get("oauth")
}
