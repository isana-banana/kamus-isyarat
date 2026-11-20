package com.kamusisyarat.speechpractice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AudioReference(
    val id: String,
    val label: String,
    val fileName: String
)

class AudioReferenceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("audio_references", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "audio_references").apply { mkdirs() }

    fun list(target: String): List<AudioReference> {
        val array = JSONArray(prefs.getString(targetKey(target), "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(AudioReference(o.getString("id"), o.getString("label"), o.getString("fileName")))
            }
        }
    }

    fun file(ref: AudioReference): File = File(dir, ref.fileName)

    fun add(target: String, fileName: String): AudioReference {
        val id = System.currentTimeMillis().toString()
        val ref = AudioReference(id, "Contoh " + (list(target).size + 1), fileName)
        val array = JSONArray(prefs.getString(targetKey(target), "[]"))
        array.put(JSONObject().apply {
            put("id", ref.id)
            put("label", ref.label)
            put("fileName", ref.fileName)
        })
        prefs.edit().putString(targetKey(target), array.toString()).apply()
        return ref
    }

    fun delete(target: String, id: String) {
        val old = list(target)
        val kept = old.filterNot { it.id == id }
        val array = JSONArray()
        kept.forEachIndexed { index, ref ->
            array.put(JSONObject().apply {
                put("id", ref.id)
                put("label", "Contoh " + (index + 1))
                put("fileName", ref.fileName)
            })
        }
        prefs.edit().putString(targetKey(target), array.toString()).apply()
    }

    fun createFile(): File = File(dir, "ref_" + System.currentTimeMillis() + ".m4a")

    private fun targetKey(target: String): String =
        "target_" + target.trim().lowercase().hashCode().toString()
}
