# Security report
Self-review only. **No external audit, no penetration test, no fuzzing was done.**
- Crypto: platform primitives only (PBKDF2-HMAC-SHA256 600k, AES-256-GCM in Android Keystore). No custom crypto.
- PIN record is Keystore-wrapped; lockout with exponential back-off; constant-time comparison.
- Biometric is a UI gate (platform BiometricPrompt), not a key-binding.
- Network: cert errors are never bypassable; HTTPS-first with prompt on fallback; mixed content blocked; system CAs only in network security config (cleartext still permitted so HTTP fallback is possible).
- Isolation: per-space WebView Profiles; at most 4 live WebViews; renderer-crash containment.
- Hardening: allowBackup=false, FLAG_SECURE, recents screenshot disabled, filterTouchesWhenObscured (tapjacking), external-scheme launches blocked, javascript:/file:/data: never loaded from the address bar.
- Source of review findings fixed so far: insets, auto-lock false triggers, clipboard listener leak, session-end reset, thread-unsafe matcher.
- Residual risks: see KNOWN_LIMITATIONS.md.
