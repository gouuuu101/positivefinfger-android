package com.positivefinger.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val ink = Color.rgb(25, 35, 55)
    private val blue = Color.rgb(37, 99, 235)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(248, 250, 252)
        window.navigationBarColor = Color.rgb(248, 250, 252)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(248, 250, 252))
        }
        val scroll = ScrollView(this).apply { addView(page) }
        setContentView(scroll)

        page.addView(TextView(this).apply {
            text = "PositiveFinger"
            textSize = 30f
            setTextColor(ink)
            typeface = Typeface.DEFAULT_BOLD
        })
        page.addView(TextView(this).apply {
            text = "Android キーボード"
            textSize = 17f
            setTextColor(blue)
            setPadding(0, dp(4), 0, dp(20))
        })
        page.addView(TextView(this).apply {
            text = "キーボードを使う"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
        })
        page.addView(TextView(this).apply {
            text = "1. 下のボタンから PositiveFinger キーボードを有効にします。\n2. 入力方法の一覧で PositiveFinger を選びます。\n3. テキスト欄をタップして入力を試せます。\n\n日本語入力が必要な場合は、キーボード切替ボタンから普段お使いの日本語 IME に切り替えてください。"
            textSize = 15f
            setTextColor(Color.rgb(75, 85, 99))
            setLineSpacing(dp(5).toFloat(), 1f)
            setPadding(0, dp(12), 0, dp(18))
        })
        page.addView(actionButton("キーボード設定を開く") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        page.addView(actionButton("キーボードを選択") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }.apply { setMargins(top = 10) })

        page.addView(TextView(this).apply {
            text = "入力テスト"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
            setPadding(0, dp(28), 0, dp(10))
        })
        page.addView(EditText(this).apply {
            hint = "ここをタップして入力"
            textSize = 16f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.WHITE)
        })
        page.addView(TextView(this).apply {
            text = "この Android 版は、提供された iOS プロジェクトをもとにした Kotlin 版です。元の iOS キーボードは画面切替ボタンのみの実装だったため、Android 版では基本的な QWERTY 入力キーを追加しています。"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(24), 0, dp(8))
        })
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue))
        setOnClickListener { action() }
    }

    private fun Button.setMargins(top: Int) = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
