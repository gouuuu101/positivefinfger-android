package com.positivefinger.android

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import kotlin.math.abs
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Local kana flick composition + QWERTY/symbol input. Never reads the surrounding editor text. */
class PositiveFingerKeyboardService : InputMethodService() {
    private lateinit var store: ActivityStore
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var candidates: LinearLayout
    private var mode = "かな"
    private var shifted = false
    private var composing = ""
    private var english = StringBuilder()
    private var privateField = false
    private var numericField = false
    private val converterExecutor=Executors.newSingleThreadExecutor()
    @Volatile private var converter: SumireConverter? = null
    private var query: Future<*>? = null
    private var conversionGeneration=0
    private var destroyed=false
    private var convertedCandidates=emptyList<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val clearStatus = Runnable { if(::status.isInitialized) status.text="POSITIVE FINGER" }
    override fun onCreate() {
        super.onCreate(); store=ActivityStore.get(this)
        converterExecutor.execute {
            try {
                converter=SumireConverter.load { assets.open(it) }
                handler.post { if(!destroyed && ::candidates.isInitialized) requestConversion() }
            } catch (_: Exception) {
                handler.post { if(!destroyed && ::status.isInitialized) status.text="変換辞書を読み込めませんでした。かな入力は利用できます" }
            }
        }
    }
    private fun dp(n: Int) = (n*resources.displayMetrics.density).toInt()
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute,restarting)
        // Do not carry text, candidate state, or a password across fields or applications.
        conversionGeneration++; query?.cancel(true); convertedCandidates=emptyList(); composing=""; english.clear(); shifted=false
        val type=attribute?.inputType ?: 0
        val clazz=type and InputType.TYPE_MASK_CLASS
        val variation=type and InputType.TYPE_MASK_VARIATION
        privateField=(clazz==InputType.TYPE_CLASS_TEXT && variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (clazz==InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
            ((attribute?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        numericField=clazz==InputType.TYPE_CLASS_NUMBER || clazz==InputType.TYPE_CLASS_PHONE || clazz==InputType.TYPE_CLASS_DATETIME
        if(numericField) mode="123" else if(privateField) mode="ABC" else mode="かな"
        if(::root.isInitialized) setInputView(onCreateInputView())
    }
    override fun onFinishInput() {
        // Finish any visible composing text without awarding an unconfirmed expression.
        currentInputConnection?.finishComposingText()
        conversionGeneration++; query?.cancel(true); convertedCandidates=emptyList(); composing=""; english.clear(); handler.removeCallbacks(clearStatus)
        super.onFinishInput()
    }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd)
        if(composing.isNotEmpty() && (newSelStart!=candidatesEnd || newSelEnd!=candidatesEnd)) {
            composing=""; english.clear(); currentInputConnection?.finishComposingText(); if(::candidates.isInitialized) refreshCandidates()
        }
    }
    override fun onCreateInputView(): View {
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(5),dp(4),dp(6)); setBackgroundColor(Pop.paper)
        }
        status=TextView(this).apply { text="POSITIVE FINGER"; textSize=11f; setTextColor(Pop.purple); gravity=Gravity.CENTER; minHeight=dp(25) }
        root.addView(status)
        candidates=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(candidates) },LinearLayout.LayoutParams(-1,dp(44)))
        refreshCandidates()
        if(mode=="かな") kanaKeys() else alphabetKeys()
        return root
    }
    private fun row() = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }.also { root.addView(it,LinearLayout.LayoutParams(-1,dp(54))) }
    private fun key(row: LinearLayout, title: String, weight: Float = 1f, action: () -> Unit): Button {
        val button=Button(this).apply {
            text=title; textSize=if(title.length>4) 11f else 17f; isAllCaps=false; setTextColor(Pop.ink)
            minWidth=0; minimumWidth=0; minHeight=0; setPadding(0,0,0,0)
            background=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(9).toFloat(); setStroke(dp(1),Pop.ink) }
            setOnClickListener { feedback(this); action() }
        }
        row.addView(button,LinearLayout.LayoutParams(0,-1,weight).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
        return button
    }
    private fun feedback(view: View) {
        if(store.flag("haptic")) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if(store.flag("sound")) (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK)
    }
    private fun kanaKeys() {
        val rows=listOf(listOf("あいうえお","かきくけこ","さしすせそ"),listOf("たちつてと","なにぬねの","はひふへほ"),listOf("まみむめも","や（ゆ）よ","らりるれろ"),listOf("小゛゜","わをんー〜","、。？！"))
        for((index,groups) in rows.withIndex()) {
            val row=row()
            for(group in groups) {
                if(group=="小゛゜") { key(row,group) { if(composing.isNotEmpty()) { composing=composing.dropLast(1)+KanaDictionary.variant(composing.last()); updateComposition() } }; continue }
                val chars=when(group) { "や（ゆ）よ"->listOf("や","（","ゆ","）","よ"); else->group.map { it.toString() } }
                val button=key(row,chars.first()) { appendKana(chars.first()) }
                button.contentDescription=chars.joinToString("、")+"。左・上・右・下へフリック"
                var downX=0f; var downY=0f
                button.setOnTouchListener { v,event ->
                    when(event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { downX=event.x; downY=event.y; v.isPressed=true; true }
                        MotionEvent.ACTION_UP -> {
                            v.isPressed=false
                            val dx=event.x-downX; val dy=event.y-downY
                            val direction=if(maxOf(abs(dx),abs(dy))<dp(20)) 0 else if(abs(dx)>abs(dy)) { if(dx<0) 1 else 3 } else { if(dy<0) 2 else 4 }
                            if(direction==0) v.performClick() else { feedback(v); appendKana(chars.getOrElse(direction) { chars.first() }) }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> { v.isPressed=false; true }
                        else->true
                    }
                }
            }
            when(index) { 0->key(row,"⌫") { delete() }; 1->key(row,"空白") { if(composing.isNotEmpty()) commit(composing) else currentInputConnection?.commitText(" ",1) }; 2->key(row,"確定") { enter() }; else->key(row,"🌐") { switchKeyboard() } }
        }
        val bottom=row()
        key(bottom,"ABC / 123") { changeMode("ABC") }
        key(bottom,"←") { move(-1) }; key(bottom,"→") { move(1) }
        key(bottom,"閉じる") { commitWithoutReward(); requestHideSelf(0) }
    }
    private fun alphabetKeys() {
        val rows=if(mode=="123") listOf("1234567890","@#¥%&*-+=/","()「」!?、。:") else listOf("qwertyuiop","asdfghjkl","zxcvbnm")
        rows.forEachIndexed { index,letters ->
            val row=row()
            if(index==2 && mode=="ABC") key(row,if(shifted) "⬆" else "⇧",1.3f) { shifted=!shifted; setInputView(onCreateInputView()) }
            for(letter in letters) key(row,if(shifted) letter.uppercase() else letter.toString()) {
                val value=if(shifted) letter.uppercase() else letter.toString()
                currentInputConnection?.commitText(value,1)
                if(!privateField && !numericField) { english.append(value); if(english.length>256) english.delete(0,english.length-256) }
                if(shifted) { shifted=false; setInputView(onCreateInputView()) }
            }
            if(index==2) key(row,"⌫",1.3f) { delete() }
        }
        val bottom=row()
        key(bottom,"かな") { changeMode("かな") }
        key(bottom,if(mode=="123") "ABC" else "123") { changeMode(if(mode=="123") "ABC" else "123") }
        key(bottom,"🌐") { switchKeyboard() }
        key(bottom,"空白",2.5f) { currentInputConnection?.commitText(" ",1); if(!privateField && !numericField && english.length<256) english.append(' ') }
        key(bottom,"↵",1.4f) { enter() }
    }
    private fun appendKana(text: String) {
        if(privateField || numericField) { currentInputConnection?.commitText(text,1); return }
        if(composing.length>=128) commit(composing)
        composing+=text; updateComposition()
    }
    private fun updateComposition() {
        currentInputConnection?.setComposingText(composing,1)
        requestConversion()
    }
    private fun requestConversion() {
        conversionGeneration++; val generation=conversionGeneration
        query?.cancel(true); convertedCandidates=emptyList(); refreshCandidates()
        val value=composing
        val engine=converter ?: return
        if(value.isEmpty() || privateField || numericField || destroyed) return
        query=converterExecutor.submit {
            val result=try { engine.candidates(value) } catch (_: Exception) { emptyList() }
            handler.post {
                if(!destroyed && generation==conversionGeneration && value==composing && !privateField) {
                    convertedCandidates=result; refreshCandidates()
                }
            }
        }
    }
    private fun refreshCandidates() {
        candidates.removeAllViews()
        if(privateField || numericField || composing.isEmpty()) {
            candidates.addView(TextView(this).apply { text=if(privateField) "保護された入力欄：表現検出オフ" else if(mode=="かな") "かなをタップ・フリックして入力" else "English / Symbols"; textSize=12f; setPadding(dp(8),dp(10),dp(8),0); setTextColor(Pop.ink) }); return
        }
        (convertedCandidates.ifEmpty { KanaDictionary.candidates(composing) }).forEach { candidate ->
            candidates.addView(Button(this).apply {
                text=candidate; textSize=16f; isAllCaps=false; setTextColor(Pop.ink)
                setOnClickListener { feedback(this); commit(candidate) }
            },LinearLayout.LayoutParams(-2,dp(44)))
        }
    }
    private fun commit(value: String) {
        val connection=currentInputConnection ?: return
        if(!connection.commitText(value,1)) return
        connection.finishComposingText(); conversionGeneration++; query?.cancel(true); convertedCandidates=emptyList(); composing=""; english.clear(); refreshCandidates()
        detect(value)
    }
    private fun detect(value: String) {
        if(privateField || numericField || value.isBlank()) return
        try {
            val detected=store.record(value)
            if(detected) {
                status.text="前向きなことばを見つけたよ ✦"
                handler.removeCallbacks(clearStatus); handler.postDelayed(clearStatus,2500)
            }
            val cloud=CloudClient.get(this)
            if(cloud.signedIn && store.queued().isNotEmpty() && !cloud.busy) cloud.run({sync()}) { /* Retry on app foreground if offline; never persist input text. */ }
        } catch (_: Exception) { status.text="保存できませんでした。アプリを開いて確認してください" }
    }
    private fun delete() {
        val connection=currentInputConnection ?: return
        if(composing.isNotEmpty()) { composing=composing.dropLast(1); updateComposition(); return }
        // Do not inspect selections or surrounding text, especially in protected fields.
        val selected=if(!privateField) connection.getSelectedText(0) else null
        if(!selected.isNullOrEmpty()) { connection.commitText("",1); english.clear() }
        else { connection.deleteSurroundingTextInCodePoints(1,0); if(english.isNotEmpty()) english.delete(english.offsetByCodePoints(english.length,-1),english.length) }
    }
    private fun enter() {
        if(composing.isNotEmpty()) { commit(composing); return }
        val action=(currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val connection=currentInputConnection ?: return
        val noAction=((currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0
        val success=if(action in listOf(EditorInfo.IME_ACTION_NONE,EditorInfo.IME_ACTION_UNSPECIFIED) || noAction) connection.commitText("\n",1) else connection.performEditorAction(action)
        if(success) { detect(english.toString()); english.clear() }
    }
    private fun commitWithoutReward() { conversionGeneration++; query?.cancel(true); convertedCandidates=emptyList(); currentInputConnection?.finishComposingText(); composing=""; english.clear() }
    private fun changeMode(next: String) { if(composing.isNotEmpty()) commit(composing); english.clear(); mode=next; setInputView(onCreateInputView()) }
    private fun switchKeyboard() { commitWithoutReward(); if(!switchToNextInputMethod(false)) (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker() }
    private fun move(direction: Int) {
        commitWithoutReward()
        val code=if(direction<0) android.view.KeyEvent.KEYCODE_DPAD_LEFT else android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,code))
        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,code))
    }
    override fun onDestroy() { destroyed=true; conversionGeneration++; query?.cancel(true); converterExecutor.shutdownNow(); handler.removeCallbacksAndMessages(null); composing=""; english.clear(); super.onDestroy() }
}
