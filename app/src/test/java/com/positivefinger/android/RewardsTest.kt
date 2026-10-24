package com.positivefinger.android

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class RewardsTest {
    @Test fun longPhraseDoesNotDoubleCountAndNegationIsExcluded() {
        assertEquals(listOf("ありがとうございます"),PositiveEngine.analyze("ありがとうございます！"))
        assertTrue(PositiveEngine.analyze("好きじゃない。ありがとうと言えなかった").isEmpty())
        assertEquals(listOf("最高"),PositiveEngine.analyze("最高じゃないけど、今日は最高！"))
    }
    @Test fun unicodeAndWhitespaceNormalization() {
        assertEquals("ABCありがとう",PositiveEngine.normalize("ＡＢＣ \n ありがとう"))
    }
    @Test fun rewardCooldownAndDuplicateSurviveStoreRecreation() {
        val prefs=MemoryPreferences(); val a=ActivityStore(prefs); a.account("user-a")
        val now=1_800_000_000_000L
        assertTrue(a.record("ありがとう",now))
        val b=ActivityStore(prefs)
        assertFalse(b.record("ありがとう",now+1))
        assertEquals(1,b.snapshot().getInt("detections")); assertEquals(1,b.queued().size)
        b.record("最高",now+10_000)
        assertEquals(2,b.snapshot().getInt("detections")); assertEquals(1,b.queued().size)
        b.record("ありがとう",now+30_000)
        assertEquals(2,b.queued().size)
    }
    @Test fun dailyCapAndDayRollover() {
        val store=ActivityStore(MemoryPreferences()); store.account("u")
        val now=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.US).parse("2026-10-24 12:00")!!.time
        repeat(21) { store.record("ありがとう",now+it*31_000) }
        assertEquals(20,store.queued().size)
        assertEquals(100,store.snapshot().getInt("earned"))
        store.record("ありがとう",now+86_400_000)
        assertEquals(21,store.queued().size); assertEquals(5,store.snapshot().getInt("earned"))
    }
    @Test fun accountSwitchDropsPendingRewardsAndInventory() {
        val store=ActivityStore(MemoryPreferences()); store.account("a")
        store.record("ありがとう",1_800_000_000_000L)
        store.account("b")
        assertTrue(store.queued().isEmpty()); assertEquals(0,store.snapshot().optInt("balance"))
        assertEquals("momo",store.snapshot().optString("equipped","momo"))
        assertEquals(1,store.snapshot().optInt("detections"))
    }
    @Test fun acknowledgementOnlyRemovesItsEvent() {
        val store=ActivityStore(MemoryPreferences()); store.account("u")
        store.record("ありがとう",1_800_000_000_000L); store.record("最高",1_800_000_031_000L)
        val ids=store.queued(); store.acknowledge(ids[0]); assertEquals(listOf(ids[1]),store.queued())
    }
    @Test fun inputTextIsNotPersistedAndFeedbackCanBeDisabled() {
        val prefs=MemoryPreferences(); val store=ActivityStore(prefs); store.account("u"); store.setFlag("feedback",false)
        assertFalse(store.record("ありがとう、ひみつの文章",1_800_000_000_000L))
        assertEquals(1,store.queued().size)
        assertFalse(prefs.data.toString().contains("ひみつ")); assertFalse(prefs.data.toString().contains("ありがとう"))
    }
    @Test fun offlineInputDoesNotCreateSpendableCloudBalance() {
        val store=ActivityStore(MemoryPreferences()); store.record("ありがとう",1_800_000_000_000L)
        assertEquals(0,store.snapshot().optInt("balance")); assertTrue(store.queued().isEmpty())
    }
    @Test fun kanaVoicingAndCandidateFallback() {
        assertEquals('ぱ',KanaDictionary.variant('ば')); assertEquals('っ',KanaDictionary.variant('づ'))
        assertTrue(KanaDictionary.candidates("かんしゃ").contains("感謝"))
        assertTrue(KanaDictionary.candidates("てすと").contains("テスト"))
    }
    @Test(expected=IllegalArgumentException::class) fun lockedCompanionCannotBeEquipped() {
        ActivityStore(MemoryPreferences()).equip("soda")
    }
}

private class MemoryPreferences : SharedPreferences {
    val data=mutableMapOf<String,Any?>()
    override fun getAll(): MutableMap<String,*> = data
    override fun getString(key: String?, def: String?) = data[key] as? String ?: def
    @Suppress("UNCHECKED_CAST") override fun getStringSet(key: String?, def: MutableSet<String>?) = data[key] as? MutableSet<String> ?: def
    override fun getInt(key: String?, def: Int) = data[key] as? Int ?: def
    override fun getLong(key: String?, def: Long) = data[key] as? Long ?: def
    override fun getFloat(key: String?, def: Float) = data[key] as? Float ?: def
    override fun getBoolean(key: String?, def: Boolean) = data[key] as? Boolean ?: def
    override fun contains(key: String?) = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes=mutableMapOf<String,Any?>(); private var clear=false
        private fun put(k: String?,v: Any?): SharedPreferences.Editor { changes[k!!]=v; return this }
        override fun putString(k: String?,v: String?) = put(k,v)
        override fun putStringSet(k: String?,v: MutableSet<String>?) = put(k,v)
        override fun putInt(k: String?,v: Int) = put(k,v)
        override fun putLong(k: String?,v: Long) = put(k,v)
        override fun putFloat(k: String?,v: Float) = put(k,v)
        override fun putBoolean(k: String?,v: Boolean) = put(k,v)
        override fun remove(k: String?) = put(k,null)
        override fun clear(): SharedPreferences.Editor { clear=true; return this }
        override fun commit(): Boolean { if(clear) data.clear(); changes.forEach { (k,v)->if(v==null) data.remove(k) else data[k]=v }; return true }
        override fun apply() { commit() }
    }
}
