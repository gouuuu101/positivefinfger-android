package com.positivefinger.android

import java.text.Normalizer

/** Direct port of the iOS phrase rules; no text is sent to a server. */
object PositiveEngine {
    private val words = listOf("ありがとう", "ありがとうございます", "感謝", "嬉しい", "うれしい", "楽しい", "たのしい", "最高", "大好き", "好き", "おめでとう", "頑張った", "がんばった", "素晴らしい", "すごい", "幸せ", "助かった", "よかった", "いいね", "応援してる", "大丈夫", "できた")
    private val negations = listOf("じゃない", "ではない", "でない", "くない", "しくない", "とは思わない", "と思わない", "と言えな", "って言えな", "といえな", "とは言えな", "ではなかった", "じゃなかった", "くなかった", "しくなかった", "ない", "なかった")
    fun analyze(text: String): List<String> {
        var remaining = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val matched = mutableListOf<String>()
        for (word in words.sortedWith(compareByDescending<String> { it.length }.thenBy { it })) {
            var start = 0
            var found = false
            while (true) {
                val index = remaining.indexOf(word, start)
                if (index < 0) break
                val tail = remaining.substring(index + word.length)
                if (negations.none { tail.startsWith(it) }) found = true
                start = index + word.length
            }
            if (found) { matched.add(word); remaining = remaining.replace(word, "\uFFFC") }
        }
        return matched
    }
    fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).filterNot { it.isWhitespace() }
}
