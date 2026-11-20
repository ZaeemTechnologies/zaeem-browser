import com.zaeem.browser.core.*
import java.security.SecureRandom

var failures = 0
var passed = 0
fun check(name: String, cond: Boolean) { if (cond) passed++ else { failures++; println("FAIL: $name") } }

fun main() {
    // --- SecretHasher
    val rec = SecretHasher.create("1234".toCharArray(), SecureRandom(), 20_000)
    check("pin verifies", SecretHasher.verify("1234".toCharArray(), rec))
    check("wrong pin rejected", !SecretHasher.verify("1235".toCharArray(), rec))
    val dec = SecretRecord.decode(rec.encode())
    check("record roundtrip", dec != null && SecretHasher.verify("1234".toCharArray(), dec))
    check("stored form has no plaintext", !rec.encode().contains("1234:") && rec.encode().startsWith("v1:20000:"))
    check("two hashes differ by salt", SecretHasher.create("1234".toCharArray(), SecureRandom(), 20_000).encode() != rec.encode())
    check("garbage record rejected", SecretRecord.decode("v1:5:zz:zz") == null && SecretRecord.decode("x") == null)
    check("pin validation", SecretHasher.isValidPin("0000") && !SecretHasher.isValidPin("12") && !SecretHasher.isValidPin("12a4"))
    check("password validation", SecretHasher.isValidPassword("correct horse") && !SecretHasher.isValidPassword("short"))

    // --- Lockout
    check("no lockout first 4", (0..4).all { LockoutPolicy.delayMillis(it) == 0L })
    check("lockout 5th = 30s", LockoutPolicy.delayMillis(5) == 30_000L)
    check("lockout doubles", LockoutPolicy.delayMillis(6) == 60_000L)
    check("lockout capped 1h", LockoutPolicy.delayMillis(99) == 3_600_000L)
    check("remaining counts down", LockoutPolicy.remainingMillis(5, 1000, 11_000) == 20_000L)
    check("remaining never negative", LockoutPolicy.remainingMillis(5, 0, 999_999) == 0L)

    // --- LockPolicy
    check("lock immediately when timeout 0", LockPolicy.shouldLock(true, 100, 101, 0))
    check("no lock before timeout", !LockPolicy.shouldLock(true, 0, 59_999, 60_000))
    check("lock at timeout", LockPolicy.shouldLock(true, 0, 60_000, 60_000))
    check("never lock when disabled", !LockPolicy.shouldLock(false, 0, 10_000_000, 0))
    check("never when timeout -1", !LockPolicy.shouldLock(true, 0, 10_000_000, -1))

    // --- Sessions
    val s = TimedSession(1_000, 15 * 60_000L)
    check("session remaining", s.remaining(1_000) == 900_000L)
    check("session expires", s.expired(1_000 + 900_000L) && !s.expired(1_000 + 899_999L))
    check("session roundtrip", TimedSession.decode(s.encode()) == s)
    check("session decode junk", TimedSession.decode("abc") == null && TimedSession.decode(null) == null && TimedSession.decode("1:0") == null)
    check("format mm:ss", SessionDurations.format(900_000) == "15:00")
    check("format h:mm:ss", SessionDurations.format(3_725_000) == "1:02:05")
    check("custom minutes bounds", SessionDurations.parseCustomMinutes("45") == 45 && SessionDurations.parseCustomMinutes("0") == null && SessionDurations.parseCustomMinutes("1441") == null && SessionDurations.parseCustomMinutes("x") == null)

    // --- UrlUtils
    val ddg = "https://duckduckgo.com/?q=%s"
    check("domain -> https", UrlUtils.resolve("example.com", ddg, true) == "https://example.com")
    check("domain http when httpsFirst off", UrlUtils.resolve("example.com", ddg, false) == "http://example.com")
    check("explicit url kept", UrlUtils.resolve("https://a.org/x?y=1", ddg, true) == "https://a.org/x?y=1")
    check("words -> search", UrlUtils.resolve("best privacy browser", ddg, true) == "https://duckduckgo.com/?q=best+privacy+browser")
    check("javascript: is never navigated", UrlUtils.resolve("javascript:alert(1)", ddg, true).startsWith("https://duckduckgo.com/"))
    check("file: is never navigated", UrlUtils.resolve("file:///etc/passwd", ddg, true).startsWith("https://duckduckgo.com/"))
    check("data: is never navigated", UrlUtils.resolve("data:text/html,hi", ddg, true).startsWith("https://duckduckgo.com/"))
    check("localhost stays http", UrlUtils.resolve("localhost:8080", ddg, true) == "http://localhost:8080")
    check("userinfo trick -> search", UrlUtils.resolve("google.com@evil.com", ddg, true).startsWith("https://duckduckgo.com/"))
    check("about:blank kept", UrlUtils.resolve("about:blank", ddg, true) == "about:blank")
    check("empty -> blank", UrlUtils.resolve("  ", ddg, true) == "about:blank")
    check("upgrade http", UrlUtils.upgradeToHttps("http://a.com/p?q=1") == "https://a.com/p?q=1" && UrlUtils.upgradeToHttps("https://a.com") == null)
    check("host strips www", UrlUtils.host("https://www.Example.com/x") == "example.com")
    check("punycode flagged", UrlUtils.isPunycode("xn--pple-43d.com") && !UrlUtils.isPunycode("apple.com"))

    // --- TrackerMatcher
    val parsed = TrackerMatcher.parse("""
        # comment
        0.0.0.0 ads.example.com
        127.0.0.1 localhost
        ||tracker.net^
        ||cdn.evil.org^${'$'}third-party
        plain-domain.io
        @@||allowed.com^
        ! abp comment
        some/path/rule
    """.trimIndent())
    check("parse hosts+abp+plain", parsed == setOf("ads.example.com", "tracker.net", "cdn.evil.org", "plain-domain.io"))
    val m = TrackerMatcher(parsed)
    check("exact match", m.isBlocked("tracker.net"))
    check("subdomain match", m.isBlocked("a.b.tracker.net"))
    check("parent not blocked by child entry", !m.isBlocked("example.com"))
    check("unrelated not blocked", !m.isBlocked("nottracker.net"))
    check("lookalike suffix not blocked", !m.isBlocked("tracker.net.evil.io"))
    m.allowSite("www.shop.com")
    check("allow-list overrides", !m.isBlocked("tracker.net", "shop.com") && m.isBlocked("tracker.net", "other.com"))
    check("third-party detection", m.isThirdParty("cdn.x.com", "www.y.com") && !m.isThirdParty("static.y.com", "www.y.com"))

    // --- Presets
    val bal = PrivacyConfig.forPreset(PrivacyPreset.BALANCED)
    val str = PrivacyConfig.forPreset(PrivacyPreset.STRICT)
    val max = PrivacyConfig.forPreset(PrivacyPreset.MAXIMUM)
    check("balanced is https-first + tracker blocking", bal.httpsFirst && bal.blockTrackers && bal.blockThirdPartyCookies)
    check("strict > balanced", str.blockWebRtc && str.noScreenshots && str.fingerprint == FingerprintLevel.STRICT && !bal.blockWebRtc)
    check("max keeps strict protections", max.blockWebRtc && max.noScreenshots && max.clearAfterBackgroundMinutes == 1)

    // --- Erase
    check("quick subset of complete", EraseMode.COMPLETE.scopes.containsAll(EraseMode.QUICK.scopes))
    check("complete has all scopes", EraseMode.COMPLETE.scopes.size == EraseScope.values().size)

    // --- SensitiveDetector
    check("visa test number", SensitiveDetector.classify("4111 1111 1111 1111") == SensitiveKind.CARD)
    check("bad luhn not card", SensitiveDetector.classify("4111 1111 1111 1112") != SensitiveKind.CARD)
    check("otp", SensitiveDetector.classify("482913") == SensitiveKind.OTP)
    check("private key", SensitiveDetector.classify("-----BEGIN OPENSSH PRIVATE KEY-----\nabc") == SensitiveKind.PRIVATE_KEY)
    check("password-like", SensitiveDetector.classify("Tr0ub4dor&3xx") == SensitiveKind.SECRET)
    check("plain sentence not sensitive", SensitiveDetector.classify("hello world, how are you") == null)
    check("url not sensitive", SensitiveDetector.classify("https://Example.com/a1B2c3?x=1") == null)

    println("core tests: $passed passed, $failures failed")
    if (failures > 0) kotlin.system.exitProcess(1)
}
