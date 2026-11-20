package com.zaeem.browser.core

enum class FingerprintLevel { OFF, STANDARD, STRICT }
enum class PrivacyPreset { BALANCED, STRICT, MAXIMUM }

data class PrivacyConfig(
    val httpsFirst: Boolean,
    val blockTrackers: Boolean,
    val blockThirdPartyCookies: Boolean,
    val fingerprint: FingerprintLevel,
    val blockWebRtc: Boolean,
    val safeBrowsing: Boolean,
    val disableAutofill: Boolean,
    val blockPopups: Boolean,
    val clearOnExit: Boolean,
    val clearAfterBackgroundMinutes: Int,   // 0 = off
    val noScreenshots: Boolean,
    val clipboardGuard: Boolean,
    val blockMicCameraByDefault: Boolean,
    val blockLocationByDefault: Boolean,
) {
    companion object {
        fun forPreset(p: PrivacyPreset): PrivacyConfig = when (p) {
            PrivacyPreset.BALANCED -> PrivacyConfig(
                httpsFirst = true, blockTrackers = true, blockThirdPartyCookies = true,
                fingerprint = FingerprintLevel.STANDARD, blockWebRtc = false, safeBrowsing = true,
                disableAutofill = true, blockPopups = true, clearOnExit = true,
                clearAfterBackgroundMinutes = 0, noScreenshots = false, clipboardGuard = true,
                blockMicCameraByDefault = false, blockLocationByDefault = false)
            PrivacyPreset.STRICT -> forPreset(PrivacyPreset.BALANCED).copy(
                fingerprint = FingerprintLevel.STRICT, blockWebRtc = true,
                clearAfterBackgroundMinutes = 15, noScreenshots = true,
                blockMicCameraByDefault = true, blockLocationByDefault = true)
            PrivacyPreset.MAXIMUM -> forPreset(PrivacyPreset.STRICT).copy(
                safeBrowsing = false, // avoids the hash-prefix lookups that go to Google; trade-off documented
                clearAfterBackgroundMinutes = 1)
        }
    }
}
