# Dependencies and licenses
| Item | Version | License | Note |
|---|---|---|---|
| androidx.webkit | 1.13.0 (pinned) | Apache-2.0 | Upstream is at 1.18.0 (Oct 2026); upgrade via Dependabot after testing on a device |
| Kotlin stdlib | 2.0.21 | Apache-2.0 | |
| Android Gradle Plugin | 8.7.3 | Apache-2.0 | build only |
| Gradle | 8.10.2 | Apache-2.0 | build only |
| Blocklist seed (140 domains) | n/a | hand-compiled domain names | no third-party list text copied |
No other runtime libraries. Verify with `./gradlew :app:dependencies` before release.
