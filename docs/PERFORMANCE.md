# Performance report
**No measurements exist.** No device or emulator was available, so no startup, memory, or page-load numbers are claimed.
Design choices expected to help (unmeasured): no external UI toolkit, programmatic views, minify off for now, max 4 live WebViews, background tabs discarded on memory pressure (`onTrimMemory`), per-request config read from an in-memory cache, concurrent suffix-set tracker matching.
Methodology: run `scripts/bench_startup.sh <pkg> <activity> 10` on the same device, same state, for each browser; capture PSS via `dumpsys meminfo`; page-load with a fixed page set over the same network. Publish raw numbers, device model, Android and WebView versions.
