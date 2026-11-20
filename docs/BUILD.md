# Build and install
**Via GitHub Actions (no local SDK):** create a repo, push this folder, open Actions → run "build". Download the APK artifact. Optional: add secrets `ZAEEM_KEYSTORE` (base64 keystore), `ZAEEM_KS_PASS`, `ZAEEM_KEY_ALIAS` for a stable signing key; otherwise the debug key is used. Tag `v1.0.0` to attach the APK to a release.
**Locally:** JDK 17, Android SDK 35, `./gradlew :core:runCoreTests assembleRelease`.
**Install:** enable "install unknown apps" for your file manager, open the APK. Android 10+ (API 29) required.
