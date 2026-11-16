package com.kamusisyarat.offline
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class OfflineBridge(private val context: Context, private val webView: WebView) {
    companion object { private const val BASE = "https://kamus-isyarat.bravewarrior011.workers.dev" }
    private val executor = Executors.newSingleThreadExecutor()

    @JavascriptInterface fun localCatalog(): String =
        readText(File(context.filesDir, "offline/catalog.json"), "{\"items\":[]}")

    @JavascriptInterface fun loadDictionary(id: String): String {
        val safe = safePart(id) ?: return "{\"error\":\"ID kamus tidak valid.\"}"
        return readText(File(context.filesDir, "offline/${safe}/manifest.json"), "{\"error\":\"Kamus belum diunduh.\"}")
    }

    @JavascriptInterface fun mediaUrl(id: String, fileName: String): String {
        val safeId = safePart(id) ?: return ""
        val safeName = safeFile(fileName) ?: return ""
        return "https://appassets.androidplatform.net/offline/${safeId}/media/${URLEncoder.encode(safeName, "UTF-8").replace("+", "%20")}"
    }

    @JavascriptInterface fun syncCatalog(callbackName: String) {
        executor.execute {
            try {
                val json = httpGet("${BASE}/api/catalog")
                val dir = File(context.filesDir, "offline").apply { mkdirs() }
                writeText(File(dir, "catalog.json"), json)
                callback(callbackName, true, "Daftar kamus diperbarui.")
            } catch (e: Exception) { callback(callbackName, false, e.message ?: "Gagal memperbarui daftar kamus.") }
        }
    }

    @JavascriptInterface fun syncDictionary(id: String, callbackName: String) {
        executor.execute {
            try {
                val safeId = safePart(id) ?: throw Exception("ID kamus tidak valid.")
                val remote = JSONObject(httpGet("${BASE}/api/dictionary/${URLEncoder.encode(id, "UTF-8")}"))
                val name = remote.optString("name", id)
                val manifest = remote.getJSONObject("manifest")
                val words = manifest.optJSONObject("kata") ?: throw Exception("Manifest kamus tidak memiliki bagian kata.")
                val root = File(context.filesDir, "offline/${safeId}")
                val mediaDir = File(root, "media").apply { mkdirs() }
                val names = linkedSetOf<String>()
                val keys = words.keys()
                while (keys.hasNext()) {
                    val item = words.getJSONObject(keys.next())
                    listOf("gambar", "video").forEach { field ->
                        val v = item.optString(field, "").trim()
                        if (v.isNotEmpty() && !looksLikeDriveId(v)) safeFile(v)?.let { names.add(it) }
                    }
                }
                for (nameFile in names) {
                    val target = File(mediaDir, nameFile)
                    val remoteSize = remoteSize(id, nameFile)
                    if (!target.exists() || (remoteSize >= 0 && remoteSize != target.length())) {
                        download("${BASE}/api/media/by-name/${URLEncoder.encode(id, "UTF-8")}/${URLEncoder.encode(nameFile, "UTF-8")}", target)
                    }
                }
                writeText(File(root, "manifest.json"), JSONObject().put("id", id).put("name", name).put("manifest", manifest).toString())
                writeText(File(root, "media.json"), JSONObject(names.associateWith { File(mediaDir, it).length() }).toString())
                callback(callbackName, true, "Kamus \"${name}\" berhasil diperbarui.")
            } catch (e: Exception) { callback(callbackName, false, e.message ?: "Gagal mengunduh kamus.") }
        }
    }

    private fun callback(name: String, ok: Boolean, message: String) {
        val js = "${name}(${ok},${JSONObject.quote(message)})"
        webView.post { webView.evaluateJavascript(js, null) }
    }
    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 120000; c.requestMethod = "GET"
        return try { c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } } finally { c.disconnect() }
    }
    private fun remoteSize(id: String, fileName: String): Long {
        val c = URL("${BASE}/api/media/by-name/${URLEncoder.encode(id, "UTF-8")}/${URLEncoder.encode(fileName, "UTF-8")}").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000; c.requestMethod = "HEAD"
        return try { c.getHeaderFieldLong("Content-Length", -1L) } finally { c.disconnect() }
    }
    private fun download(url: String, target: File) {
        target.parentFile?.mkdirs()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 180000; c.requestMethod = "GET"
        try { c.inputStream.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } } }
        finally { c.disconnect() }
    }
    private fun readText(file: File, fallback: String) = if (file.exists()) file.readText(Charsets.UTF_8) else fallback
    private fun writeText(file: File, text: String) { file.parentFile?.mkdirs(); file.writeText(text, Charsets.UTF_8) }
    private fun safePart(v: String): String? = if (v.matches(Regex("[A-Za-z0-9_-]{1,100}"))) v else null
    private fun safeFile(v: String): String? { val x=v.trim(); return if (x.isEmpty()||x.contains("/")||x.contains("\\")||x=="."||x=="..") null else x }
    private fun looksLikeDriveId(v: String) = v.matches(Regex("[A-Za-z0-9_-]{20,}"))
}
