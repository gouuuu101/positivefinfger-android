package com.positivefinger.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var store: ActivityStore
    private lateinit var cloud: CloudClient
    private lateinit var page: LinearLayout
    private var tab = 0
    private var events = false
    private var signup = false
    private var providers = emptyList<String>()
    private var message: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if(cloud.signedIn && !cloud.busy) cloud.run({sync()}) { result ->
                message = result
                // Preserve an in-progress form or open dialog while refreshing other screens.
                if(tab != 4 && profile().optBoolean("nickname_set") && !isFinishing) render()
            }
            handler.postDelayed(this,15_000)
        }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ActivityStore.get(this); cloud = CloudClient.get(this)
        tab = savedInstanceState?.getInt("tab") ?: 0
        render()
        if(intent?.data != null) handleCallback(intent) else cloud.run({providers().also { providers = it }; if(signedIn) sync() else null}) { message=it; render() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleCallback(intent) }
    private fun handleCallback(intent: Intent) {
        val uri = intent.data ?: return
        intent.data = null
        cloud.run({callback(uri)}) { message=it; render() }
    }
    override fun onResume() { super.onResume(); handler.postDelayed(poll,1000) }
    override fun onPause() { handler.removeCallbacks(poll); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("tab",tab); super.onSaveInstanceState(outState) }
    private fun background(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat(); setStroke(dp(2),Pop.ink)
    }
    private fun label(text: String, size: Float = 16f, bold: Boolean = false, color: Int = Pop.ink) = TextView(this).apply {
        this.text=text; textSize=size; setTextColor(color)
        if(bold) typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
        setPadding(0,dp(5),0,dp(5))
    }
    private fun LinearLayout.add(view: View, height: Int = -2) {
        addView(view,LinearLayout.LayoutParams(-1,if(height>=0) dp(height) else height).apply { bottomMargin=dp(12) })
    }
    private fun button(text: String, color: Int = Pop.purple, enabled: Boolean = true, action: () -> Unit) = Button(this).apply {
        this.text=text; textSize=15f; isAllCaps=false; minHeight=dp(50)
        setTextColor(if(color==Pop.purple) Color.WHITE else Pop.ink)
        background=background(color,14); isEnabled=enabled && !cloud.busy; alpha=if(isEnabled) 1f else .45f
        setOnClickListener { action() }
    }
    private fun panel(color: Int = Color.WHITE, block: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(16),dp(18),dp(16)); background=background(color); block()
    }
    private fun field(hint: String, secret: Boolean = false) = EditText(this).apply {
        this.hint=hint; textSize=16f; minHeight=dp(52); setSingleLine(true)
        inputType=if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        if(secret) importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_YES
    }
    private fun state() = cloud.state ?: JSONObject()
    private fun wallet() = state().optJSONObject("wallet") ?: JSONObject()
    private fun profile() = state().optJSONObject("profile") ?: JSONObject()
    private fun inventory() = store.snapshot().optJSONObject("inventory") ?: JSONObject()
    private fun jstDay() = SimpleDateFormat("yyyy-MM-dd",Locale.US).apply { timeZone=TimeZone.getTimeZone("Asia/Tokyo") }.format(Date())
    private fun earned() = if(wallet().optString("earned_day")==jstDay()) wallet().optInt("daily_earned") else 0
    private fun perform(action: String, item: String? = null) {
        cloud.run({action(action,item)}) { result -> message=result; render(); if(result!=null) dialog("お知らせ",result) }
        render()
    }
    private fun dialog(title: String, text: String) { if(!isFinishing) AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("閉じる",null).show() }
    private fun render() {
        if(isFinishing || isDestroyed) return
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(if(store.snapshot().optString("background")=="sky") 0xFFE4F4FF.toInt() else Pop.paper) }
        // Android 15 enforces edge-to-edge: protect controls from system bars and IME.
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom); insets
        }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(20),dp(22),dp(24)) }
        scroll.addView(page); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root); root.requestApplyInsets()
        if(!cloud.signedIn) { welcome(); return }
        if(cloud.state==null) {
            page.add(label("アカウントを確認しています",26f,true)); message?.let { page.add(label(it)) }
            page.add(button("もう一度接続する") { cloud.run({sync()}) { message=it; render() }; render() })
            page.add(button("別のアカウントでログイン",Color.WHITE) { logout() }); return
        }
        if(!profile().optBoolean("nickname_set")) { profileForm(true); return }
        when(tab) { 0->home(); 1->play(); 2->collection(); 3->shop(); 4->settingsPage() }
        message?.let { page.add(label(it,13f,false,Pop.purple)) }
        val nav=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(dp(4),dp(8),dp(4),dp(8)); setBackgroundColor(Color.WHITE) }
        listOf("⌂\nホーム","✦\nあそぶ","▦\nずかん","♧\n交換所","⚙\n設定").forEachIndexed { index, title ->
            nav.addView(TextView(this).apply {
                text=title; textSize=12f; gravity=Gravity.CENTER; minHeight=dp(54)
                setTextColor(if(tab==index) Pop.purple else Pop.ink); contentDescription=title.substringAfter('\n')
                setOnClickListener { tab=index; message=null; render() }
            },LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(nav)
    }
    private fun welcome() {
        page.add(label("POSITIVE FINGER",14f,true,Pop.purple))
        page.add(label("いつものことばに、\n小さなごほうび。",32f,true))
        page.add(CompanionView(this,0),180)
        page.add(button(if(signup) "新規登録 → ログインへ" else "ログイン → 新規登録へ",Pop.yellow) { signup=!signup; message=null; render() })
        val email=field("メールアドレス").apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val password=field("パスワード",true)
        page.add(email); page.add(password)
        page.add(button(if(signup) "アカウントを作成" else "ログイン") {
            val e=email.text.toString(); val p=password.text.toString()
            if(e.isBlank() || p.isBlank()) { dialog("入力を確認","メールアドレスとパスワードを入力してください"); return@button }
            cloud.run({authenticate(e,p,signup)}) { message=it; render() }; render()
        })
        providers.forEach { provider ->
            val title=when(provider) { "apple"->"Apple"; "google"->"Google"; else->"LINE" }
            page.add(button("${title}で続ける",Color.WHITE) {
                cloud.run({val url=oauth(provider); Handler(Looper.getMainLooper()).post { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) }; null}) { if(it!=null) { message=it; render() } }
            })
        }
        message?.let { page.add(label(it,14f,false,Pop.purple)) }
        page.add(label("ご利用にはアカウントが必要です。ポイントや相棒を共有します。入力した文章は保存・送信しません。",12f))
        page.add(button("キーボードの追加・使い方",Color.WHITE) { setup() })
    }
    private fun home() {
        page.add(label("POSITIVE\nFINGER!",18f,true))
        page.add(button("お知らせ",Color.WHITE) { announcements() })
        page.add(label("こんにちは、${profile().optString("display_name")}さん",14f))
        page.add(label("ことばが、\nごほうびに。",34f,true))
        val equipped=store.snapshot().optString("equipped","momo")
        val index=ActivityStore.characters.indexOf(equipped).coerceAtLeast(0)
        page.add(panel(Pop.colors[index]) {
            add(label("きょうの相棒    NO. 0${index+1}",12f,true))
            add(CompanionView(this@MainActivity,index),190)
            add(label(Pop.names[index],26f,true)); add(label(Pop.notes[index],14f,true))
        })
        if(profile().optString("title")=="wordfriend") page.add(label("ことばの友",14f,true,Pop.purple))
        page.add(panel { add(label("つかえるポイント",13f,true)); add(label("${wallet().optInt("balance")} pt",42f,true)) })
        page.add(panel(Pop.yellow) {
            add(label("きょうのことば貯金     ${earned()} / 100 pt",17f,true))
            add(ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; progress=earned() })
            add(label("前向きな表現を確定すると +5 pt。\n無理せず、いつもの会話で。",14f))
        })
        val checked=wallet().optString("checkin_day")==jstDay()
        page.add(panel {
            add(label("連続チェックイン ${wallet().optInt("streak")} 日",18f,true))
            add(button(if(checked) "今日も来てくれてありがとう・受取済み" else "今日のごほうび +10 pt",Pop.yellow,!checked) { perform("checkin") })
            add(label("きょうのミッション",18f,true))
            val missions=state().optJSONArray("missions") ?: JSONArray()
            listOf("first_word" to 1,"five_words" to 5).forEach { (id,target) ->
                val claimed=(0 until missions.length()).any { missions.getString(it)==id }
                add(label("ことばを${target}回届けよう  ${minOf(earned()/5,target)} / $target",14f))
                add(button(if(claimed) "受取済み" else "+${if(target==1) 10 else 20} pt",Color.WHITE,!claimed && earned()/5>=target) { perform("mission",id) })
            }
        })
        page.add(button("まずはキーボードを使ってみよう",Color.WHITE) { setup() })
        page.add(button("みんなのことばランキング",Color.WHITE) { ranking() })
        page.add(button("みんなのクエスト →",Pop.colors[3]) { tab=1; events=true; render() })
        page.add(button("ポイント交換所 →",Pop.pink) { tab=3; render() })
        page.add(label("アプリ内専用ポイントです。現金・ギフト券への交換はまだありません。",12f))
    }
    private fun play() {
        page.add(button(if(events) "イベント ／ ガチャへ切り替える" else "ガチャ ／ イベントへ切り替える",Color.WHITE) { events=!events; render() })
        if(events) { liveEvents(); return }
        page.add(label("次は、だれに\n会えるかな？",32f,true))
        page.add(panel(Pop.pink) {
            add(label("相棒カプセル                         全6種",18f,true))
            add(CompanionView(this@MainActivity,2),220)
            add(label("GOOD WORDS, GOOD FRIENDS",12f,true))
        })
        page.add(button("1回まわす                 25 pt",Pop.purple,wallet().optInt("balance")>=25) { perform("gacha") })
        page.add(label("いま ${wallet().optInt("balance")} pt ／ 1回 25 pt",14f,true))
        page.add(label("全6種・各1/6の確率。重複あり。出会った相棒はずかんに残ります。ポイントの購入はありません。",13f))
    }
    private fun collection() {
        val inv=inventory(); val unlocked=ActivityStore.characters.count { it=="momo" || inv.optInt(it)>0 }
        page.add(label("相棒ずかん     $unlocked / 6",30f,true))
        page.add(label("集めて、選んで。ホームでいっしょに。",14f))
        for(i in 0..2) {
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            for(j in 0..1) {
                val index=i*2+j; val id=ActivityStore.characters[index]; val owned=id=="momo" || inv.optInt(id)>0
                val card=panel(if(owned) Pop.colors[index] else 0xFFE9E7ED.toInt()) {
                    add(label("NO. 0${index+1}",12f,true)); if(owned) add(CompanionView(this@MainActivity,index),110) else add(label("？",64f,true))
                    add(label(if(owned) Pop.names[index] else "？？？",18f,true))
                    add(label(if(id=="momo") "はじめの相棒" else if(owned) "${inv.optInt(id)}回出会った" else "カプセルで出会おう",11f))
                    if(owned) add(button(if(store.snapshot().optString("equipped","momo")==id) "選択中" else "相棒にする",Color.WHITE) {
                        store.equip(id); render()
                    })
                }
                row.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),0,dp(3),0) })
            }
            page.add(row)
        }
    }
    private fun shop() {
        page.add(label("がんばった分、\nすきなものに。",32f,true))
        page.add(label("つかえるポイント   ${wallet().optInt("balance")} pt",20f,true))
        listOf(Triple("wordfriend","称号：ことばの友",50),Triple("sky","背景：ソーダの空",100)).forEach { (id,title,cost) ->
            val owned=inventory().optInt(id)>0
            page.add(panel(if(id=="sky") Pop.colors[1] else Pop.yellow) {
                add(label(title,22f,true)); add(label(if(id=="sky") "ホームをさわやかな空の色に。" else "いつものことばが、あなたのしるしに。",14f))
                add(button(if(owned) "交換済み" else "$cost ptで交換する",Color.WHITE,!owned && wallet().optInt("balance")>=cost) {
                    AlertDialog.Builder(this@MainActivity).setTitle("ポイントを使って交換しますか？").setMessage("$title · $cost pt")
                        .setPositiveButton("交換する") { _,_->perform("exchange",id) }.setNegativeButton("キャンセル",null).show()
                })
            })
        }
        page.add(label("ギフト券・他社ポイントへの交換：準備中",18f,true))
        page.add(label("今のポイントはアプリ内専用で、現金やギフト券への交換はできません。",13f))
    }
    private fun liveEvents() {
        page.add(label("ひとりの一歩が、\nみんなの力に。",32f,true))
        val list=state().optJSONArray("events") ?: JSONArray()
        val members=state().optJSONArray("memberships") ?: JSONArray()
        if(list.length()==0) page.add(label("次のイベントを準備しています。お知らせを楽しみに！"))
        for(i in 0 until list.length()) {
            val event=list.getJSONObject(i); val id=event.getString("id")
            val member=(0 until members.length()).map { members.getJSONObject(it) }.find { it.optString("event_id")==id }
            page.add(panel(Pop.colors[3]) {
                add(label(event.getString("title"),23f,true)); add(label(event.optString("description")))
                add(label("みんなで ${event.optInt("progress")} / ${event.optInt("goal")} 回",18f,true))
                add(label("あなたの貢献 ${member?.optInt("contribution") ?: 0} 回 · 達成報酬 ${event.optInt("reward")} pt",13f))
                add(button(if(member==null) "イベントに参加する" else if(member.optBoolean("claimed")) "報酬受取済み" else "達成報酬を受け取る",Color.WHITE,
                    member==null || (!member.optBoolean("claimed") && event.optInt("progress")>=event.optInt("goal"))) {
                    perform(if(member==null) "join_event" else "claim_event",id)
                })
            })
        }
    }
    private fun settingsPage() {
        page.add(label("設定",32f,true))
        page.add(button("マイページ・着せ替え",Color.WHITE) { profileDialog() })
        page.add(button("ランキング",Color.WHITE) { ranking() })
        page.add(button("お知らせ",Color.WHITE) { announcements() })
        page.add(button("キーボードの追加・使い方",Color.WHITE) { setup() })
        listOf(Triple("sound","タイプ音",false),Triple("haptic","キーの振動",false),Triple("feedback","検出時のメッセージ",true)).forEach { (key,title,default) ->
            page.add(Switch(this).apply { text=title; minHeight=dp(48); isChecked=store.flag(key,default); setOnCheckedChangeListener { _,value->store.setFlag(key,value) } })
        }
        page.add(label("これまでの検出   ${store.snapshot().optInt("detections")} 回",19f,true))
        val counts=store.snapshot().optJSONObject("counts") ?: JSONObject()
        page.add(label("最近7日間のことば",17f,true))
        repeat(7) { index ->
            val date=Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR,index-6) }.timeInMillis
            val day=ActivityStore.day(date); page.add(label("$day    ${counts.optInt(day)} 回",13f))
        }
        page.add(label("ポイントのしくみ",20f,true))
        page.add(label("対象の表現を確定すると5 pt。1日100 ptまで。ガチャは25 pt、全6種が同じ確率で出ます。入力の確定を検出し、送信操作は判定しません。",13f))
        page.add(label("プライバシー",20f,true))
        page.add(label("入力文は保存・送信しません。端末内で語句を検出し、回数・設定と重複判定用ハッシュを保存します。ログイン時は利用者ID・獲得イベント・ポイント・相棒をサーバーで共有します。パスワード欄は解析しません。語句の検出は気持ちの評価ではありません。",13f))
        page.add(button("同期し直す",Color.WHITE) { cloud.run({sync()}) { message=it; render() }; render() })
        page.add(button("ログアウト",Color.WHITE) { AlertDialog.Builder(this).setMessage("ログアウトしますか？").setPositiveButton("ログアウト") { _,_->logout() }.setNegativeButton("キャンセル",null).show() })
        page.add(button("オープンソースライセンス",Color.WHITE) {
            val notices=listOf("LICENSE","THIRD-PARTY-NOTICES.md")+assets.list("sumire/licenses/sources").orEmpty().filter { it.endsWith(".txt") }.map { "licenses/sources/$it" }
            val content=notices.joinToString("\n\n────────\n") { file->"$file\n"+assets.open("sumire/$file").bufferedReader().use { it.readText() } }
            val view=label(content,12f).apply { setTextIsSelectable(true); setPadding(dp(20),dp(16),dp(20),dp(16)) }
            AlertDialog.Builder(this).setTitle("オープンソースライセンス").setView(ScrollView(this).apply { addView(view) }).setPositiveButton("閉じる",null).show()
        })
        page.add(label("POSITIVE FINGER / ANDROID",11f,true,Pop.purple))
    }
    private fun logout() { cloud.run({logout()}) { message=it; tab=0; render() } }
    private fun profileForm(first: Boolean, target: LinearLayout = page, done: (() -> Unit)? = null) {
        target.add(label(if(first) "はじめまして！" else "マイページ",28f,true))
        val name=field("ニックネーム 2〜16文字").apply { setText(profile().optString("display_name")) }
        target.add(name)
        val ranking=Switch(this).apply { text="ランキングに参加する"; isChecked=profile().optBoolean("leaderboard_opt_in"); minHeight=dp(48) }
        target.add(ranking); target.add(label("参加すると、ニックネームと累計のことばポイントが他の利用者に表示されます。",12f))
        val sky=Switch(this).apply { text="背景：ソーダの空"; isEnabled=inventory().optInt("sky")>0; isChecked=profile().optString("background")=="sky"; minHeight=dp(48) }
        val title=Switch(this).apply { text="称号：ことばの友"; isEnabled=inventory().optInt("wordfriend")>0; isChecked=profile().optString("title")=="wordfriend"; minHeight=dp(48) }
        target.add(sky); target.add(title)
        target.add(button(if(first) "この名前ではじめる" else "変更を保存") {
            val n=name.text.toString().trim(); val r=ranking.isChecked; val b=if(sky.isChecked) "sky" else "paper"; val t=if(title.isChecked) "wordfriend" else "none"
            cloud.run({profile(n,r,b,t)}) { message=it; done?.invoke(); render() }
        })
        if(first) { message?.let { target.add(label(it)) }; target.add(button("ログアウト",Color.WHITE) { logout() }) }
    }
    private fun profileDialog() {
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(10),dp(20),dp(10)) }
        val dialog=AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(content) }).setNegativeButton("閉じる",null).create()
        profileForm(false,content) { dialog.dismiss() }; dialog.show()
    }
    private fun setup() {
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(10),dp(20),dp(10)) }
        content.add(label("いつもの入力で、\nポイントをためよう。",26f,true))
        content.add(label("1. キーボード設定でPositiveFingerを有効にする。\n2. 入力方法の一覧からPositiveFingerを選ぶ。\n3. かなを入力して確定。英字・数字にも切り替えられます。",15f))
        content.add(button("キーボード設定を開く") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        content.add(button("キーボードを選択",Color.WHITE) { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })
        content.add(field("ここで入力を試せます"))
        content.add(label("日本語変換にはスミレの辞書処理とMozc由来のシステム辞書を使用します。辞書は端末内で動作します。iOS版azooKeyとは変換結果が異なります。",12f))
        AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(content) }).setPositiveButton("閉じる",null).show()
    }
    private fun announcements() {
        val a=state().optJSONArray("announcements") ?: JSONArray()
        val text=(0 until a.length()).joinToString("\n\n") { a.getJSONObject(it).let { n->"${n.optString("kind")}  ${n.optString("title")}\n${n.optString("body")}" } }
        dialog("お知らせ",text.ifEmpty { "まだお知らせはありません。" })
    }
    private fun ranking() {
        var entries=JSONObject()
        cloud.run({entries=leaderboard(); null}) { error ->
            if(error!=null) { dialog("ランキング",error); return@run }
            val a=entries.optJSONArray("entries") ?: JSONArray()
            val text=(0 until a.length()).joinToString("\n\n") { a.getJSONObject(it).let { e->"${e.optInt("place")}位  ${e.optString("display_name")} ${if(e.optBoolean("is_me")) "（あなた）" else ""}   ${e.optInt("points")} pt" } }
            dialog("ことばが集まるランキング", "言葉で獲得した累計ポイント順。使っても減りません。\n\n"+text.ifEmpty { "まだ参加者がいません。" })
        }
    }
}
