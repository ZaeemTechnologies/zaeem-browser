# Feature status matrix

Labels: **IT** = Implemented and tested (automated test passes) · **IC** = Implemented, compiled, NOT tested on a device · **P** = Partially implemented · **D** = Designed, not implemented · **NF** = Not feasible with this engine/budget.

Important: only the pure-Kotlin core logic has automated tests (60 checks, JVM). Everything that touches Android APIs compiled against the Android SDK stubs but has **never run on a device or emulator**. Treat all IC items as unverified.

| Feature | Status | Notes |
|---|---|---|
| PBKDF2 PIN/password hashing, constant-time compare | IT | Platform JCA, no custom crypto |
| Lockout back-off policy | IT | 5 free tries, then 30 s doubling to 1 h |
| Timed-session clock logic | IT | |
| URL resolution (blocks javascript:/file:/data:) | IT | |
| Tracker domain matcher (hosts/plain/ABP formats) | IT | |
| Sensitive-data detector (card/OTP/key/secret) | IT | |
| Privacy presets Balanced/Strict/Maximum | IT (config) / IC (applied) | |
| App Lock (PIN, password) | IC | Keystore-wrapped record |
| Biometric unlock | IC | Platform BiometricPrompt, UI gate only |
| Zero history / no persistent history store | IC | |
| One-Tap Erase (quick/complete) | IC | Complete needs process restart to finish profile deletion |
| Panic button, shortcut, shake-to-panic | IC | |
| Self-destructing timed sessions | IC | Ephemeral profiles deleted at next launch |
| Private Spaces (isolated profiles) | IC | WebView Profiles API; needs a WebView that supports it |
| Tracker blocking | IC | shouldInterceptRequest, 140-domain seed list |
| HTTPS-first, cert errors never bypassable, mixed content blocked | IC | |
| Anti-fingerprinting (canvas/WebGL/audio noise, spoofing, WebRTC off) | P | Injected JS; detectable and incomplete by nature |
| Permission Guardian | IC | |
| Vault (Keystore AES-256-GCM) | IC | |
| Clipboard Guard | IC | |
| No-Screenshot mode | IC | FLAG_SECURE; does not stop a camera |
| Secure Downloads | IC | HTTPS-only, private storage, MediaStore export |
| Privacy Dashboard / Tracker Radar / Audit | P | Cookie-block counts are not exposed by the engine |
| Secure DNS | P | Reads system Private DNS and guides the user; WebView cannot use its own DoH |
| Update monitor | P | Reports WebView version; no auto-update |
| Tapjacking filter, memory trimming | IC | |
| All 23 screens | IC | Visually unreviewed on a device |
| Tor / onion routing | NF | |
| QR scanner | D | |
| Reader mode | D | |
| Own engine (Gecko/Chromium fork) | NF | See ARCHITECTURE |
| Prebuilt APK | D | Not built here; use GitHub Actions (BUILD.md) |
