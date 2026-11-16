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
import java.util.concurrent.atomic.AtomicInteger

class OfflineBridge(private val context: Context, private val webView: WebView) {
    companion object {
        private const val BASE = "https://kamus-isyarat.bravewarrior011.workers.dev"
        private const val MAX_PARALLEL = 4
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mediaExecutor = Executors.newFixedThreadPool(MAX_PARALLEL)

    private val offlineRoot: File
        get() = File(context.filesDir, "offline")

    @JavascriptInterface
    fun localCatalog(): String {
        val online = readJsonObject(File(offlineRoot, "catalog.json"), JSONObject().put("items", org.json.JSONArray()))
        val all = online.optJSONArray("items") ?: org.json.JSONArray()
        val downloaded = org.json.JSONArray()

        for (i in 0 until all.length()) {
            val item = all.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val manifestFile = File(offlineRoot, safePart(id)?.let { "\$it/manifest.json" } ?: "")
            if (manifestFile.isFile) downloaded.put(item)
        }

        return JSONObject().put("items", downloaded).toString()
    }

    @JavascriptInterface
    fun onlineCatalog(): String =
        readText(File(offlineRoot, "catalog.json"), JSONObject().put("items", org.json.JSONArray()).toString())

    @JavascriptInterface
    fun loadDictionary(id: String): String {
        val safe = safePart(id) ?: return JSONObject().put("error", "ID kamus tidak valid.").toString()
        return readText(
            File(offlineRoot, "\$safe/manifest.json"),
            JSONObject().put("error", "Kamus belum diunduh.").toString()
        )
    }

    @JavascriptInterface
    fun mediaUrl(id: String, fileName: String): String {
        val safeId = safePart(id) ?: return ""
        val safeName = safeFile(fileName) ?: return ""
        val local = File(offlineRoot, "\$safeId/media/\$safeName")
        if (local.isFile && local.length() > 0L) {
            return "https://appassets.androidplatform.net/offline/\$safeId/media/\${encodePathPart(safeName)}"
        }
        return ""
    }

    @JavascriptInterface
    fun syncCatalog(callbackName: String) {
        executor.execute {
            try {
                val json = httpGetWithRetry("\$BASE/api/catalog")
                writeText(File(offlineRoot, "catalog.json"), json)
                callback(callbackName, true, "Daftar kamus diperbarui.")
            } catch (e: Exception) {
                callback(callbackName, false, e.message ?: "Gagal memperbarui daftar kamus.")
            }
        }
    }

    @JavascriptInterface
    fun syncDictionary(id: String, callbackName: String) {
        executor.execute {
            try {
                val safeId = safePart(id) ?: throw Exception("ID kamus tidak valid.")
                val remote = JSONObject(httpGetWithRetry("\$BASE/api/dictionary/\${encodePathPart(id)}"))
                val name = remote.optString("name", id)
                val manifest = remote.getJSONObject("manifest")
                val words = manifest.optJSONObject("kata")
                    ?: throw Exception("Manifest kamus tidak memiliki bagian kata.")

                val root = File(offlineRoot, safeId)
                val mediaDir = File(root, "media").apply { mkdirs() }
                val oldMeta = readJsonObject(File(root, "media.json"), JSONObject())
                val newMeta = JSONObject()

                val refs = linkedSetOf<String>()
                val keys = words.keys()
                while (keys.hasNext()) {
                    val item = words.optJSONObject(keys.next()) ?: continue
                    listOf("gambar", "video").forEach { field ->
                        val value = item.optString(field, "").trim()
                        if (value.isNotEmpty()) {
                            safeFile(value)?.let { refs.add(it) }
                        }
                    }
                }

                val downloaded = AtomicInteger(0)
                val skipped = AtomicInteger(0)
                val total = refs.size

                val futures = refs.map { ref ->
                    mediaExecutor.submit {
                        try {
                            val target = File(mediaDir, ref)
                            val old = oldMeta.optJSONObject(ref) ?: JSONObject()
                            val meta = remoteMeta(id, ref)

                            if (meta == null) {
                                target.delete()
                                skipped.incrementAndGet()
                                return@submit
                            }

                            val remoteSize = meta.optLong("size", -1L)
                            val remoteTag = meta.optString("etag", "")
                            val sameSize = remoteSize >= 0L && target.isFile && target.length() == remoteSize
                            val sameTag = remoteTag.isNotEmpty() && remoteTag == old.optString("etag", "")

                            if (target.isFile && target.length() > 0L && (sameTag || sameSize)) {
                                newMeta.put(ref, meta)
                                return@submit
                            }

                            downloadWithRetry(mediaUrlFor(id, ref), target)

                            if (target.isFile && target.length() > 0L) {
                                if (remoteSize >= 0L && target.length() != remoteSize) {
                                    target.delete()
                                    skipped.incrementAndGet()
                                    return@submit
                                }
                                newMeta.put(ref, meta)
                                downloaded.incrementAndGet()
                            } else {
                                target.delete()
                                skipped.incrementAndGet()
                            }
                        } catch (_: Exception) {
                            File(mediaDir, ref).delete()
                            skipped.incrementAndGet()
                        }
                    }
                }

                futures.forEach { it.get() }

                writeText(
                    File(root, "manifest.json"),
                    JSONObject()
                        .put("id", id)
                        .put("name", name)
                        .put("manifest", manifest)
                        .toString()
                )
                writeText(File(root, "media.json"), newMeta.toString())

                val missing = skipped.get()
                val message = when {
                    total == 0 -> "Kamus \"\$name\" berhasil disimpan. Tidak ada file media."
                    missing == 0 -> "Kamus \"\$name\" berhasil diunduh/diperbarui. \$total file media tersedia."
                    else -> "Kamus \"\$name\" tersimpan. \$missing file media tidak ditemukan/gagal dan akan dicoba lagi saat Update."
                }

                callback(callbackName, true, message)
            } catch (e: Exception) {
                callback(callbackName, false, e.message ?: "Gagal mengunduh kamus.")
            }
        }
    }

    private fun remoteMeta(dictionaryId: String, fileName: String): JSONObject? {
        val connection = (URL(mediaUrlFor(dictionaryId, fileName)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 30000
            requestMethod = "HEAD"
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            JSONObject()
                .put("size", connection.getHeaderFieldLong("Content-Length", -1L))
                .put("etag", connection.getHeaderField("ETag") ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun mediaUrlFor(dictionaryId: String, fileName: String): String {
        return if (looksLikeDriveId(fileName)) {
            "\$BASE/api/media/\${encodePathPart(fileName)}"
        } else {
            "\$BASE/api/media/by-name/\${encodePathPart(dictionaryId)}/\${encodePathPart(fileName)}"
        }
    }

    private fun callback(name: String, ok: Boolean, message: String) {
        val js = "\$name(\$ok,\${JSONObject.quote(message)})"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun httpGetWithRetry(url: String): String {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                return httpGet(url)
            } catch (e: Exception) {
                last = e
                Thread.sleep(1000L * (attempt + 1))
            }
        }
        throw last ?: Exception("Gagal terhubung ke server.")
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            requestMethod = "GET"
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw Exception("Server mengembalikan HTTP \$code.")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadWithRetry(url: String, target: File) {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                download(url, target)
                return
            } catch (e: Exception) {
                last = e
                target.delete()
                Thread.sleep(1000L * (attempt + 1))
            }
        }
        throw last ?: Exception("Gagal mengunduh file.")
    }

    private fun download(url: String, target: File) {
        target.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 120000
            requestMethod = "GET"
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw Exception("Media tidak tersedia (HTTP \$code).")

            FileOutputStream(target).use { output ->
                connection.inputStream.use { input -> input.copyTo(output, 64 * 1024) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readJsonObject(file: File, fallback: JSONObject): JSONObject {
        return if (file.isFile) {
            try { JSONObject(file.readText(Charsets.UTF_8)) } catch (_: Exception) { fallback }
        } else fallback
    }

    private fun readText(file: File, fallback: String): String =
        if (file.isFile) file.readText(Charsets.UTF_8) else fallback

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    private fun encodePathPart(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun safePart(value: String): String? =
        if (value.matches(Regex("[A-Za-z0-9_-]{1,100}"))) value else null

    private fun safeFile(value: String): String? {
        val x = value.trim()
        return if (x.isEmpty() || x.contains("/") || x.contains("\\") || x == "." || x == "..") null else x
    }

    private fun looksLikeDriveId(value: String): Boolean =
        value.matches(Regex("[A-Za-z0-9_-]{20,}"))
}
