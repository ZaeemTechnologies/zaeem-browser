# Testing report
- Automated: `core/src/test/kotlin/CoreTests.kt`, 60 checks, all passing (`./gradlew :core:runCoreTests`, also run in CI via `check`).
- Compile check: app sources compile against Android API stubs/android.jar with Kotlin; full Gradle/AGP build not run here (SDK download blocked).
- Node harness: injected fingerprint/WebRTC scripts behaved correctly against a mock navigator (not a real browser).
- **Not done:** device/emulator run, UI tests, instrumented tests, lint, leak test, traffic capture, fingerprint-site comparison, Keystore/biometric behaviour on real hardware, profile-deletion behaviour across WebView versions.
Test plan before calling anything "tested": install on a physical API 29+ and API 35 device; walk all 23 screens; run erase/panic/session flows and inspect `/data/data` via a debug build; capture traffic with a proxy; test amiunique/coveryourtracks.
