# Security reports

Do not put credentials, captured sessions or exploitable private deployment details in public issues. Use GitHub private vulnerability reporting if enabled for this repository; otherwise ask the maintainer for a private reporting channel without posting sensitive details.

ESuite is a development-stage native testing app. It has explicit HTTPS execution, browser inspection, bounded tasks and a Keystore-backed encrypted workspace. It has no device-wide VPN capture, blanket TLS decryption or full active scanner. Physical-device validation is still required. The standalone React concept remains synthetic.

Future interception modules must protect CA keys, validate upstream TLS, enforce target scope and account isolation, expose capture limitations, and keep saved evidence local. Validate any scanner findings before reporting them as confirmed vulnerabilities.

Runtime targets require HTTPS. API redirects and automatic retries are disabled. Identity changes and comparison remove observed/known credential headers, but arbitrary body credentials remain unchanged and must be reviewed. Sanitized exports omit paths, queries, bodies, headers, saved identity config and finding free text. Browser cookies/cache are platform-managed app-private data outside the workspace cipher. No telemetry or remote processing service is bundled.
