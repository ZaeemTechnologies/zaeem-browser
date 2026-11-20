package com.zaeem.browser.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.URLUtil
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class DownloadItem(val id: Int, val name: String, val url: String, val file: File) {
    @Volatile var total = -1L
    @Volatile var done = 0L
    @Volatile var state = "Queued"          // Queued, Downloading, Completed, Failed, Canceled
    @Volatile var cancel = false
    val risky: Boolean get() = name.lowercase().substringAfterLast('.', "") in RISKY
    companion object { val RISKY = setOf("apk", "exe", "bat", "cmd", "msi", "scr", "jar", "sh", "dex", "xapk", "apks") }
}

/** HTTPS-only downloader into app-private storage. Nothing is opened automatically; export is user-initiated. */
class Downloader(private val ctx: Context) {
    val items = CopyOnWriteArrayList<DownloadItem>()
    private val ids = AtomicInteger()
    private val main = Handler(Looper.getMainLooper())
    var onChange: (() -> Unit)? = null
    private val dir get() = File(ctx.filesDir, "downloads").also { it.mkdirs() }

    fun sanitize(n: String): String = n.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim('.', ' ').ifEmpty { "download" }.take(100)

    fun start(url: String, ua: String?, cookie: String?, disposition: String?, mime: String?): DownloadItem? {
        if (!url.startsWith("https://")) return null           // insecure downloads are refused outright
        val name = sanitize(URLUtil.guessFileName(url, disposition, mime))
        var f = File(dir, name); var n = 1
        while (f.exists()) { f = File(dir, "${n++}_$name") }
        val it = DownloadItem(ids.incrementAndGet(), f.name, url, f)
        items.add(it)
        Thread {
            it.state = "Downloading"; notifyChange()
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 20000; c.readTimeout = 30000
                ua?.let { u -> c.setRequestProperty("User-Agent", u) }
                cookie?.let { ck -> c.setRequestProperty("Cookie", ck) }
                if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode}")
                it.total = c.contentLengthLong
                c.inputStream.use { ins -> f.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024); var r: Int
                    while (ins.read(buf).also { r = it } >= 0) {
                        if (it.cancel) throw InterruptedException()
                        out.write(buf, 0, r); it.done += r; if (it.done % (256 * 1024) < 32 * 1024) notifyChange()
                    }
                } }
                it.state = "Completed"
            } catch (e: InterruptedException) { it.state = "Canceled"; f.delete() }
            catch (e: Exception) { it.state = "Failed"; f.delete() }
            notifyChange()
        }.start()
        return it
    }

    private fun notifyChange() { main.post { onChange?.invoke() } }

    fun remove(it: DownloadItem) { it.cancel = true; it.file.delete(); items.remove(it); notifyChange() }

    fun clearAll() { items.forEach { it.cancel = true }; dir.listFiles()?.forEach { it.delete() }; items.clear(); notifyChange() }

    /** User-initiated: copies into the public Downloads/ZAEEM folder via MediaStore (no storage permission needed). */
    fun export(it: DownloadItem): Uri? {
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(it.name.substringAfterLast('.', "")) ?: "application/octet-stream"
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, it.name); put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/ZAEEM")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { out -> it.file.inputStream().use { i -> i.copyTo(out) } }
        return uri
    }
}
