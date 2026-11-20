package com.zaeem.browser.core

enum class EraseScope(val label: String) {
    TABS("Tabs"),
    HISTORY("History (if any)"),
    COOKIES("Cookies & site data"),
    CACHE("Cache"),
    SEARCH("Search suggestions"),
    DOWNLOADS("Downloads"),
    SESSION_PERMISSIONS("Session permissions"),
}

enum class EraseMode(val label: String, val scopes: Set<EraseScope>) {
    QUICK("Quick Erase", setOf(EraseScope.TABS, EraseScope.COOKIES, EraseScope.CACHE, EraseScope.SESSION_PERMISSIONS)),
    COMPLETE("Complete Cleanup", EraseScope.values().toSet()),
    CUSTOM("Custom Cleanup", emptySet()),
}

class EraseReport(val done: List<String>, val failed: List<String>, val bytesBefore: Long, val bytesAfter: Long) {
    val ok get() = failed.isEmpty()
}
