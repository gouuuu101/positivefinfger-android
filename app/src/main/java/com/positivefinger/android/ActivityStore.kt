package com.positivefinger.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Activity and IME intentionally share the default application process and this lock. */
class ActivityStore internal constructor(private val prefs: android.content.SharedPreferences) {
    companion object {
        @Volatile private var instance: ActivityStore? = null
        fun get(context: Context): ActivityStore = instance ?: synchronized(this) {
            instance ?: ActivityStore(context.applicationContext.getSharedPreferences("positivefinger", Context.MODE_PRIVATE)).also { instance = it }
        }
        val characters = listOf("momo", "soda", "lemon", "mint", "lilac", "peach")
        fun day(now: Long = System.currentTimeMillis()) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
    }
    @Synchronized fun flag(key: String, fallback: Boolean = false) = prefs.getBoolean(key, fallback)
    @Synchronized fun setFlag(key: String, value: Boolean) { check(prefs.edit().putBoolean(key, value).commit()) }
    @Synchronized fun text(key: String, fallback: String = "") = prefs.getString(key, fallback) ?: fallback
    @Synchronized fun text(key: String, value: String, write: Boolean) {
        if (write) check(prefs.edit().putString(key, value).commit())
    }
    @Synchronized fun snapshot(): JSONObject = JSONObject(prefs.getString("activity", "{}") ?: "{}")
    @Synchronized private fun edit(block: (JSONObject) -> Unit) {
        val state = snapshot(); block(state)
        check(prefs.edit().putString("activity", state.toString()).commit()) { "データを保存できませんでした" }
    }
    @Synchronized fun account(id: String?) {
        edit { s -> if (s.optString("account") != (id ?: "")) {
            // Never transfer offline or another account's rewards into a cloud wallet.
            val count = s.optInt("detections"); val daily = s.optJSONObject("counts") ?: JSONObject()
            val keys = s.keys().asSequence().toList(); keys.forEach { s.remove(it) }
            s.put("account", id ?: "").put("detections", count).put("counts", daily)
        } }
    }
    @Synchronized fun equip(id: String) = edit { s ->
        require(id == "momo" || (s.optJSONObject("inventory")?.optInt(id, 0) ?: 0) > 0) { "まだ出会っていない相棒です" }
        s.put("equipped", id)
    }
    @Synchronized fun applyCloud(value: JSONObject) = edit { s ->
        val inventory = JSONObject()
        val items = value.optJSONArray("inventory") ?: JSONArray()
        for (i in 0 until items.length()) { val v = items.getJSONObject(i); inventory.put(v.getString("item_id"), v.getInt("quantity")) }
        s.put("inventory", inventory).put("balance", value.getJSONObject("wallet").getInt("balance"))
        s.put("background", value.getJSONObject("profile").optString("background", "paper"))
        s.put("title", value.getJSONObject("profile").optString("title", "none"))
        val equipped = s.optString("equipped", "momo")
        if (equipped != "momo" && inventory.optInt(equipped) == 0) s.put("equipped", "momo")
    }
    @Synchronized fun queued(): List<String> {
        val a = snapshot().optJSONArray("queue") ?: JSONArray()
        return (0 until a.length()).map { a.getString(it) }
    }
    @Synchronized fun acknowledge(id: String) = edit { s ->
        val a = s.optJSONArray("queue") ?: JSONArray()
        s.put("queue", JSONArray((0 until a.length()).map { a.getString(it) }.filter { it != id }))
    }
    /** Called once for an actual committed expression, never on every composing keystroke. */
    @Synchronized fun record(text: String, now: Long = System.currentTimeMillis()): Boolean {
        if (PositiveEngine.analyze(text).isEmpty()) return false
        var feedback = false
        edit { s ->
            val normalized = PositiveEngine.normalize(text)
            if (normalized.isEmpty()) return@edit
            val salt = s.optString("salt").ifEmpty { UUID.randomUUID().toString().also { s.put("salt", it) } }
            val hash = MessageDigest.getInstance("SHA-256").digest((salt + normalized).toByteArray()).joinToString("") { "%02x".format(it) }
            val recent = s.optJSONObject("recent") ?: JSONObject()
            recent.keys().asSequence().toList().forEach { if (now - recent.optLong(it) !in 0 until 30_000) recent.remove(it) }
            if (recent.has(hash)) return@edit
            recent.put(hash, now); s.put("recent", recent)
            s.put("detections", s.optInt("detections") + 1)
            val counts = s.optJSONObject("counts") ?: JSONObject()
            counts.put(day(now), counts.optInt(day(now)) + 1)
            counts.keys().asSequence().sortedDescending().drop(30).toList().forEach { counts.remove(it) }
            s.put("counts", counts)
            if (s.optString("day") != day(now)) s.put("day", day(now)).put("earned", 0)
            val queue = s.optJSONArray("queue") ?: JSONArray()
            if (s.optString("account").isNotEmpty() && s.optInt("earned") < 100 && queue.length() < 100 &&
                (s.optLong("lastReward") == 0L || now - s.optLong("lastReward") >= 30_000)) {
                queue.put(UUID.randomUUID().toString()); s.put("queue", queue)
                s.put("earned", s.optInt("earned") + 5).put("lastReward", now)
            }
            if (flag("feedback", true) && (s.optLong("lastFeedback") == 0L || now - s.optLong("lastFeedback") >= 30_000)) {
                s.put("lastFeedback", now); feedback = true
            }
        }
        return feedback
    }
}
