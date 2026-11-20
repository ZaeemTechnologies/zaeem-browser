# Privacy report
- No accounts, no analytics, no telemetry, no ads, no crash-reporting SDK. The only network traffic is the user's browsing, HTTPS downloads they start, and nothing to the author.
- No history is stored. Cookies/storage live in per-space profiles and are erased on request, panic, or session end.
- Stats (blocked counts) are local counters only.
- Vault data is encrypted locally with a Keystore key and never leaves the device.
- Caveat: sites you visit see your IP address; Secure DNS depends on the user's system Private DNS setting; no Tor/VPN built in.
- Not verified by traffic capture on a device. A packet-capture test is on the roadmap.
