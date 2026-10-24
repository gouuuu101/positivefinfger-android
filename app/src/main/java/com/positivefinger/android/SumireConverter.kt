package com.positivefinger.android

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import java.io.InputStream
import java.io.ObjectInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Sumire's MIT LOUDS dictionary reader with its bundled Mozc system dictionary.
 * The bounded beam decoder below is PositiveFinger-specific, not Sumire's full IME engine.
 * No user dictionary/learning history or optional third-party lexicons are enabled.
 */
class SumireConverter private constructor(
    private val readings: LOUDSWithTermId,
    private val words: LOUDS,
    private val tokens: TokenArray,
    private val matrix: ConnectionMatrix.CostTable
) {
    private val readingIndex=SuccinctBitVector(readings.LBS)
    private val leafIndex=SuccinctBitVector(readings.isLeaf)
    private val wordIndex=SuccinctBitVector(words.LBS)
    private val tokenIndex=SuccinctBitVector(tokens.bitvector)
    private data class Word(val length: Int,val surface: String,val left: Int,val right: Int,val cost: Int)
    private data class Path(val text: String,val right: Int,val cost: Long)
    companion object {
        fun load(open: (String) -> InputStream): SumireConverter {
            fun zipped(path: String): InputStream = ZipInputStream(open("sumire/$path")).also { check(it.nextEntry != null) }
            val readings=ObjectInputStream(zipped("system/yomi.dat.zip")).use { LOUDSWithTermId().readExternalNotCompress(it) }
            val words=ObjectInputStream(zipped("system/tango.dat.zip")).use { LOUDS().readExternalNotCompress(it) }
            val tokens=TokenArray().apply {
                ObjectInputStream(zipped("system/token.dat.zip")).use { readExternal(it) }
                ObjectInputStream(open("sumire/pos_table.dat")).use { readPOSTable(it) }
            }
            val bytes=zipped("connectionId.dat.zip").use { it.readBytes() }
            check(bytes.size%2==0)
            val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer()
            val costs=ShortArray(buffer.remaining()); buffer.get(costs)
            check(!readings.LBS.isEmpty && !words.LBS.isEmpty && tokens.leftIds.isNotEmpty()) { "辞書の読み込みに失敗しました" }
            return SumireConverter(readings,words,tokens,ConnectionMatrix.fromShortArray(costs))
        }
    }
    /** Worker-thread only. Bounds keep per-keystroke work finite, even on pasted/long input. */
    fun candidates(kana: String): List<String> {
        if(kana.isBlank() || kana.length>64) return KanaDictionary.candidates(kana)
        val paths=Array(kana.length+1) { mutableListOf<Path>() }
        paths[0].add(Path("",0,0))
        for(position in kana.indices) {
            if(Thread.currentThread().isInterrupted) return emptyList()
            val current=paths[position].sortedBy { it.cost }.distinctBy { it.text to it.right }.take(16)
            if(current.isEmpty()) continue
            val suffix=kana.substring(position)
            val options=mutableListOf<Word>()
            for(reading in readings.commonPrefixSearch(suffix,readingIndex)) {
                val node=readings.getNodeIndex(reading,readingIndex)
                val term=readings.getTermId(node,leafIndex)
                if(term<0) continue
                tokens.getListDictionaryByYomiTermId(term,tokenIndex).sortedBy { it.wordCost }.take(16).forEach { token ->
                    val surface=when(token.nodeId) {
                        -2->reading
                        -1->reading.map { if(it in 'ぁ'..'ゖ') (it.code+0x60).toChar() else it }.joinToString("")
                        else->words.getLetter(token.nodeId,wordIndex)
                    }
                    val pos=token.posTableIndex.toInt()
                    if(pos in tokens.leftIds.indices) options.add(Word(reading.length,surface,tokens.leftIds[pos].toInt(),tokens.rightIds[pos].toInt(),token.wordCost.toInt()))
                }
            }
            // Unknown characters remain typable, rather than dropping an unmatched suffix.
            options.add(Word(1,kana[position].toString(),0,0,12000))
            for(word in options) {
                val end=position+word.length
                if(end>kana.length) continue
                for(previous in current) {
                    paths[end].add(Path(previous.text+word.surface,word.right,previous.cost+word.cost+matrix.cost(previous.right,word.left)))
                }
                if(paths[end].size>512) {
                    val best=paths[end].sortedBy { it.cost }.distinctBy { it.text to it.right }.take(32)
                    paths[end].clear(); paths[end].addAll(best)
                }
            }
        }
        return (paths.last().sortedBy { it.cost+matrix.cost(it.right,0) }.map { it.text }.distinct().take(12) + KanaDictionary.candidates(kana)).distinct()
    }
}
