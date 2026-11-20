package com.zaeem.browser.engine

import android.content.Intent
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/** What the engine needs from the UI layer (implemented by MainActivity). */
interface BrowserHost {
    fun toast(msg: String)
    fun onTabsChanged()
    fun askPermission(origin: String, kind: PermKind, cb: (Decision) -> Unit)
    fun ensureRuntimePermissions(perms: List<String>, cb: (Boolean) -> Unit)
    fun chooseFile(intent: Intent, cb: ValueCallback<Array<android.net.Uri>>)
    fun showCustomView(v: View, cb: WebChromeClient.CustomViewCallback)
    fun hideCustomView()
    fun confirmDownload(url: String, ua: String?, cd: String?, mime: String?, length: Long)
    fun showLinkPreview(url: String, tab: Tab)
    fun showTlsBlocked(host: String, reason: String)
    fun askHttpFallback(host: String, onContinue: () -> Unit)
}
