package com.positivefinger.android

import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

/** A lightweight QWERTY IME. Text is sent directly to the focused editor via InputConnection. */
class PositiveFingerKeyboardService : InputMethodService() {
    private var shifted = false
    private val rows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"),
        listOf("?123", ",", "🌐", "SPACE", ".", "ENTER")
    )

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setBackgroundColor(Color.rgb(224, 229, 237))
        }
        rows.forEachIndexed { rowIndex, keys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            keys.forEach { key ->
                val label = displayLabel(key)
                val button = Button(this).apply {
                    text = label
                    textSize = if (key.length > 1) 12f else 18f
                    isAllCaps = false
                    typeface = Typeface.DEFAULT
                    setTextColor(Color.rgb(30, 41, 59))
                    setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        if (key in listOf("SHIFT", "DEL", "?123", "🌐", "ENTER")) Color.rgb(190, 202, 220) else Color.WHITE
                    ))
                    setPadding(0, 0, 0, 0)
                    minHeight = dp(46)
                    minimumHeight = dp(42)
                    setOnClickListener { handleKey(key) }
                }
                val weight = when (key) {
                    "SPACE" -> 4.0f
                    "🌐", ",", "." -> 1.0f
                    "SHIFT", "DEL", "?123", "ENTER" -> 1.5f
                    else -> 1.0f
                }
                row.addView(button, LinearLayout.LayoutParams(0, dp(if (rowIndex == 3) 48 else 48), weight).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }
        return root
    }

    private fun displayLabel(key: String): String = when (key) {
        "SPACE" -> "English / Space"
        "SHIFT" -> if (shifted) "⬆" else "⇧"
        "DEL" -> "⌫"
        "ENTER" -> "↵"
        "🌐" -> "🌐"
        else -> if (shifted && key.length == 1) key.uppercase() else key
    }

    private fun handleKey(key: String) {
        val connection = currentInputConnection ?: return
        when (key) {
            "SHIFT" -> {
                shifted = !shifted
                setInputView(onCreateInputView())
            }
            "DEL" -> {
                val selected = connection.getSelectedText(0)
                if (!selected.isNullOrEmpty()) connection.commitText("", 1)
                else connection.deleteSurroundingText(1, 0)
            }
            "SPACE" -> connection.commitText(" ", 1)
            "ENTER" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) connection.performEditorAction(action)
                else connection.commitText("\n", 1)
            }
            "🌐" -> request切换()
            "?123" -> connection.commitText("?123", 1)
            else -> {
                connection.commitText(if (shifted) key.uppercase() else key, 1)
                if (shifted) {
                    shifted = false
                    setInputView(onCreateInputView())
                }
            }
        }
    }

    private fun request切换() {
        switchToNextInputMethod(false)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
