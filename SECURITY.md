# Security reports

Do not put credentials, captured sessions or exploitable private deployment details in public issues. Use GitHub private vulnerability reporting if enabled for this repository; otherwise ask the maintainer for a private reporting channel without posting sensitive details.

ESuite is currently a UI/development foundation. The native starter has no network permission, VPN, scanner or secret vault. Its local utilities are not a substitute for cryptographic key management.

Future interception modules must protect CA keys, validate upstream TLS, enforce target scope and account isolation, expose capture limitations, and keep saved evidence local. Validate any scanner findings before reporting them as confirmed vulnerabilities.
