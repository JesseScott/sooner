# Firebase

Sooner uses three Firebase products:

- **Crashlytics** for crash reports.
- **Remote Config** for a few settings that can change without a new build.
- **App Distribution** for beta builds to testers.

Firebase is optional. Without `app/google-services.json` the build skips the
Google Services and Crashlytics plugins, Firebase stays off at runtime, and the
app uses the in-app defaults. CI builds this way.

## One-time setup

1. In the [Firebase console](https://console.firebase.google.com/), create a
   project (Google Analytics is not needed).
2. Add an Android app with package name `tt.co.jesses.sooner`.
3. Download `google-services.json` and put it at `app/google-services.json`.
   It is git-ignored. It holds no secrets, but it is per-project, so keep it
   out of the repo.
4. Build and run once. Crashlytics only shows the app after the first launch;
   to confirm it works, force a crash (for example throw from a debug button)
   and relaunch the app so the report uploads.
5. **Remote Config:** create the parameters below in the console and publish.
   Missing parameters fall back to the in-app defaults.
6. **App Distribution:** open App Distribution, accept the terms, and create a
   tester group with the alias `beta-testers`. Add testers by email.
7. Install the [Firebase CLI](https://firebase.google.com/docs/cli) and run
   `firebase login` once. The Gradle plugin uses that login to upload.

## Remote Config parameters

| Key | Type | Default | What it does |
|---|---|---|---|
| `arrivals_refresh_interval_seconds` | Number | 60 | How often the arrivals screen refetches while visible (clamped 15 to 600). |
| `arrivals_min_refresh_seconds` | Number | 30 | An automatic refresh is skipped if the last fetch is newer than this (clamped 10 to 300). |
| `arrivals_max_minutes` | Number | 90 | Arrivals further out are hidden (clamped 10 to 240). |
| `scan_enabled` | Boolean | true | Shows the photo buttons. Turn off if OCR misbehaves on some phones. |

Defaults live in `config/SoonerFlags.kt`. Debug builds fetch at most once a
minute, release builds once an hour; new values apply on the next refresh.

## Shipping a beta

```
./gradlew assembleRelease appDistributionUploadRelease
```

Release notes come from `distribution/release-notes.txt`. Testers get an email
with an install link.

The TransLink key from `local.properties` is built into the APK, so testers
use your key.

### Signing

Without a release keystore, release builds are signed with your debug key.
That works for testers, but every beta must then come from the same computer,
or Android refuses the update. To use a real keystore, add to
`local.properties`:

```
release.storeFile=keystore/sooner-release.jks
release.storePassword=...
release.keyAlias=sooner
release.keyPassword=...
```

The path is relative to the project root. Keystores are git-ignored; back
yours up somewhere safe, because a lost key means testers must uninstall.
