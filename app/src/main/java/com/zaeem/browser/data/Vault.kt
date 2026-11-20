package com.zaeem.browser.data

import android.content.Context
import com.zaeem.browser.security.KeystoreBox
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Bookmark(val folder: String, val title: String, val url: String)

/** Bookmarks encrypted at rest (AES-256-GCM, Keystore-bound key). Unlock state is runtime-only. */
class Vault(ctx: Context) {
    private val file = File(ctx.filesDir, "vault.bin")
    @Volatile var unlocked = false
    private var cache: MutableList<Bookmark> = mutableListOf()

    fun lock() { unlocked = false; cache = mutableListOf() }

    fun unlock(): Boolean {
        cache = mutableListOf()
        if (file.exists()) {
            val plain = KeystoreBox.decrypt(file.readText()) ?: return false
            runCatching {
                val a = JSONArray(String(plain))
                for (i in 0 until a.length()) a.getJSONObject(i).let { cache.add(Bookmark(it.getString("f"), it.getString("t"), it.getString("u"))) }
            }.onFailure { return false }
        }
        unlocked = true; return true
    }

    fun list(): List<Bookmark> = if (unlocked) cache.toList() else emptyList()

    fun add(b: Bookmark) { if (!unlocked) return; cache.add(b); save() }
    fun remove(b: Bookmark) { if (!unlocked) return; cache.removeAll { it.url == b.url && it.title == b.title && it.folder == b.folder }; save() }

    private fun save() {
        val a = JSONArray(); cache.forEach { a.put(JSONObject().put("f", it.folder).put("t", it.title).put("u", it.url)) }
        file.writeText(KeystoreBox.encrypt(a.toString().toByteArray()))
    }

    fun wipe() { file.delete(); lock() }
    fun exists() = file.exists()
}
