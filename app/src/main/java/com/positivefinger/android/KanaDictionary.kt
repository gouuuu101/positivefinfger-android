package com.positivefinger.android

/** Small offline starter dictionary, not an azooKey/Mozc replacement. */
object KanaDictionary {
    private val entries = mapOf(
        "ありがとう" to listOf("ありがとう", "有難う"), "ありがとうございます" to listOf("ありがとうございます", "有難うございます"),
        "かんしゃ" to listOf("感謝"), "うれしい" to listOf("嬉しい", "うれしい"), "たのしい" to listOf("楽しい", "たのしい"),
        "さいこう" to listOf("最高"), "だいすき" to listOf("大好き"), "すき" to listOf("好き"),
        "おめでとう" to listOf("おめでとう"), "がんばった" to listOf("頑張った", "がんばった"),
        "すばらしい" to listOf("素晴らしい"), "すごい" to listOf("すごい", "凄い"), "しあわせ" to listOf("幸せ"),
        "たすかった" to listOf("助かった"), "よかった" to listOf("よかった", "良かった"), "いいね" to listOf("いいね"),
        "おうえんしてる" to listOf("応援してる"), "だいじょうぶ" to listOf("大丈夫"), "できた" to listOf("できた", "出来た"),
        "きょう" to listOf("今日"), "あした" to listOf("明日"), "きのう" to listOf("昨日"), "わたし" to listOf("私"),
        "ともだち" to listOf("友達"), "ことば" to listOf("言葉"), "にほん" to listOf("日本"), "にほんご" to listOf("日本語"),
        "おはよう" to listOf("おはよう"), "こんにちは" to listOf("こんにちは"), "こんばんは" to listOf("こんばんは")
    )
    fun candidates(kana: String): List<String> = if(kana.isEmpty()) emptyList() else
        ((entries[kana] ?: emptyList()) + kana + kana.map { if(it in 'ぁ'..'ゖ') (it.code+0x60).toChar() else it }.joinToString("")).distinct()
    fun variant(kana: Char): Char {
        val cycles = listOf("かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ", "ただ", "ちぢ", "つづっ", "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ", "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ", "やゃ", "ゆゅ", "よょ", "わゎ")
        val cycle = cycles.find { kana in it } ?: return kana
        return cycle[(cycle.indexOf(kana)+1)%cycle.length]
    }
}
