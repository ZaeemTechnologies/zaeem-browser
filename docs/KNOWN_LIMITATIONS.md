# Known limitations
1. Never run on a device; expect bugs.
2. No APK was built here; build through GitHub Actions.
3. WebView: weak site isolation, limited extension/API surface, version depends on the system.
4. Fingerprint protection is partial and detectable.
5. No app-level DoH; only guidance for system Private DNS.
6. No Tor, QR scanner, or reader mode.
7. Cookie-block counts are not available from the engine.
8. Profile deletion is deferred to next launch; storage is not securely overwritten (flash).
9. Biometric is a UI gate only.
10. Cleartext HTTP is possible after an explicit user prompt.
11. FLAG_SECURE cannot stop a camera.
12. Tracker list is a small seed (140 domains), not a full list.
