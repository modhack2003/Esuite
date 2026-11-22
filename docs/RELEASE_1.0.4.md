# ESuite 1.0.4

Version code 7 continues the local 1.0.3 candidate. GitHub's published baseline at the start of work was 1.0.2, so the production upgrade fixture remains 1.0.2 and retains its pinned checksum and signer.

## Product changes

- Home prioritizes the current project, switching, and the most common destinations. Settings and recovery appear where relevant.
- API Lab uses separate Request, Response, Saved and Session views. Request edits survive navigation in memory. A send exposes the matching response and shared cancellation.
- Findings uses a searchable lazy list with on-demand details and previewable redacted reports. Tools groups offline transforms and imports by purpose.
- Durable admissions validate on a worker and merge against the current document, preserving concurrent history. Save calls serialize and skip an already committed snapshot. Report preparation, cURL parsing and response comparison run off the UI thread.
- App builds cover the local and scoped manual workflows. Previous automated runner history remains readable.

## Release gates

1. Run Android JVM tests, lint, optimized package checks and the independent UI harness from the release branch.
2. Check the exact main commit across API 28–37.0. The signed workflow uses the existing signer and installs over the published 1.0.2 fixture on API 28, 35 and 37.0 before publication.
3. Confirm certificate, build metadata and checksums on the downloaded release asset. Keep physical-device accessibility and document-provider checks in the release record.

## Verification record

- Local JDK 17 and the pinned Gradle distribution passed 75 JVM tests in each build variant, debug and release lint, assembly of the Android APKs, and the publication guard fixtures.
- The first native matrix reproduced a clipped Session tab on narrow emulator displays. The corrected layout uses full-size controls that wrap, and the Browser-to-API test still requires reaching Session through the real controls. [First candidate matrix](https://github.com/modhack2003/Esuite/actions/runs/45015577452)
- The exact corrected commit is `de0258f8`. GitHub’s Android assembly, Chromium design checks, and all ten device jobs passed. The device reports record 180 passes and 30 WebView capability skips, with zero failures. The complete browser flow passed on API 32, 35, 36 and 37.0. [Corrected release matrix](https://github.com/modhack2003/Esuite/actions/runs/45016518450)

- The first signed run reached the redesigned Home on all three emulators, but the startup check still expected the previous heading. The revised check requires the selected Home tab and real project controls on both launches. The observed Home hierarchy passed the revised detector, which also rejected a fixture with a required control removed. [First signed run](https://github.com/modhack2003/Esuite/actions/runs/45017423924)
