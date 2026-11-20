# Architecture

## Engine decision
Options evaluated: (1) Android System WebView via androidx.webkit, (2) GeckoView, (3) Chromium fork.
- **Chromium fork**: needs a very large build, constant rebasing; impossible at $0 on free CI. Rejected.
- **GeckoView**: Mozilla publishes a Maven artifact with a ~4-week release cadence; strong option, but ~tens of MB, and must be kept current by us. Kept as the top roadmap item.
- **System WebView (chosen)**: smallest, updated by Play/system, supports multiple isolated Profiles (androidx.webkit), runs the renderer in an isolated sandboxed process. Trade-off: limited browser API and, per Chromium docs, little process-level site isolation (one renderer per profile, no out-of-process iframes in WebView).

## Modules
- `core/` pure Kotlin, JVM-tested: hashing, lock policy, session clock, URL rules, tracker matcher, privacy config, erase model, sensitive detector.
- `app/` Android: `engine/` (profiles, tabs, eraser, session controller, downloader, injected scripts), `security/` (Keystore box, app lock), `data/` (prefs, stats, vault), `ui/` (23 screens, programmatic views, no external UI library).

## Profile lifecycle and erase
`ProfileStore.deleteProfile` fails while a profile is loaded. So: clear data via API now → queue the profile in `pendingDeletes` → delete in `Engine.init` at next cold start before any WebView exists. Complete erase and Panic restart the process through `RestartActivity` (`:restart` process) to finish. Timed-session `eph_` profiles are always deleted at next launch.

## Threat model
In scope: shoulder-surfer/borrowed unlocked or locked phone, casual forensic browsing of app data, trackers and cross-site tracking, passive fingerprinting, accidental permission grants, screenshots/recents, clipboard leaks, malicious links launching other apps.
Out of scope / not defended: compromised OS or root, a malicious WebView provider, a renderer exploit (mitigated only by the Android sandbox and WebView updates), network adversary beyond TLS, forensic recovery of flash storage after deletion, targeted fingerprinting by sophisticated sites, camera photos of the screen.
Known weakness: JS-based fingerprint defences can be detected; WebView has weaker site isolation than Chrome.
