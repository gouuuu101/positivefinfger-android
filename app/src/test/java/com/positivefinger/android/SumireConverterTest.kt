package com.positivefinger.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SumireConverterTest {
    @Test fun bundledDictionaryConvertsWordsAndSentencesOffline() {
        val assets=File(System.getProperty("sumire.assets","src/main/assets"))
        val engine=SumireConverter.load { File(assets,it).inputStream() }
        val words=engine.candidates("かんしゃ")
        assertTrue("Expected 感謝 in $words",words.contains("感謝"))
        val sentence=engine.candidates("きょうはともだちにありがとう")
        assertTrue("Expected sentence conversion in $sentence",sentence.any { "今日" in it && "友達" in it && "ありがとう" in it })
        assertTrue(engine.candidates("ほげほげ").contains("ほげほげ"))
        assertTrue(engine.candidates("").isEmpty())
    }
}
