# Crafty Remote

An Android app (Kotlin + Jetpack Compose) for controlling a [Crafty Controller 4](https://craftycontrol.com) panel through its v2 API.

## Features

- **Server list** with live status, CPU, RAM and player count (refreshes every 5 s, pull to refresh)
- **Overview** per server: state, uptime, CPU, memory, players online, version, world size, port
- **Power controls**: start, restart, stop, kill (with confirmation for anything disruptive)
- **Live console**: log view refreshing every 2 s, warnings/errors coloured, send commands, quick-command chips and recent-command history
- **Backups**: list backup profiles and trigger a backup
- **Schedules**: list scheduled tasks and switch them on or off
- **Sign-in** with an API key, or username + password (+ 2FA code). The token is encrypted with an Android Keystore key; passwords are never stored.

Polling pauses whenever the app is in the background.

## Requirements

- Crafty Controller 4.x reachable over **HTTPS with a valid certificate** (e.g. behind a reverse proxy). The app uses Android's normal certificate checks and refuses plain `http://`.
- Android 8.0 (API 26) or newer.

## Getting the APK

### Option A — GitHub Actions (no local setup)

1. Push this folder to a GitHub repository.
2. The **Build APK** workflow runs on every push to `main`. Open the run → *Artifacts* → download `crafty-remote-apk`.
3. **Releases:** the first build of each new version is published under **Releases** as
   `crafty-remote-<version>.apk`, with that version's notes from `CHANGELOG.md`. To release an update,
   bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a `## [x.y.z]` section to
   `CHANGELOG.md`, and push to `main`.

### Option B — Android Studio

Open the folder in Android Studio (Ladybug or newer), let Gradle sync, then *Run* or *Build → Build APK(s)*.

From a terminal with the Android SDK installed: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Stable signing (recommended)

Without a signing key, CI signs each build with a throwaway debug key, so a new build **won't install over an old one** (you'd have to uninstall first). Create one key and reuse it:

```bash
keytool -genkeypair -v -keystore crafty-remote.jks -alias crafty \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 crafty-remote.jks > keystore.b64   # macOS: base64 -i crafty-remote.jks
```

Add these repository secrets (*Settings → Secrets and variables → Actions*):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `crafty` |
| `KEY_PASSWORD` | the key password |

For local release builds, create `keystore.properties` in the project root (git-ignored):

```properties
storeFile=/absolute/path/to/crafty-remote.jks
storePassword=...
keyAlias=crafty
keyPassword=...
```

Keep the `.jks` file and passwords safe — losing them means you can't ship updates that install over the current app.

## First run

1. Enter the panel address, e.g. `https://crafty.example.com` (a pasted dashboard URL is fine; reverse-proxy subpaths like `/crafty` are kept).
2. **API key**: in Crafty open your profile → *API Keys* → *Generate*, and give it only the servers and permissions the phone needs (Commands for power/console, Logs for the console, Backup, Schedule).
   **Password**: enter your Crafty username, password and, if enabled, the 2FA code.
3. Tap **Connect**. The app checks the address and credentials before saving anything.

## Using your own app icon

The icon is an adaptive icon made of two layers in `app/src/main/res/drawable/`:
`ic_launcher_background.xml` (gradient) and `ic_launcher_foreground.xml` (the rack mark).
To use your own artwork instead:

1. Make a square PNG, 432×432 px, with the artwork centred inside the middle 264×264 px
   (launchers crop the edges into circles or squircles).
2. Save it as `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`.
3. Delete `app/src/main/res/drawable/ic_launcher_foreground.xml` (two files with the same name won't build).
4. Optionally change `ic_launcher_background.xml`, or replace it with a solid colour.
5. Push to `main`; the next build uses the new icon.

## Crafty API endpoints used

| Feature | Endpoint |
| --- | --- |
| Login | `POST /api/v2/auth/login` |
| Server list | `GET /api/v2/servers` |
| Stats | `GET /api/v2/servers/{id}/stats` |
| Power / backup | `POST /api/v2/servers/{id}/action/{start_server, stop_server, restart_server, kill_server, backup_server}` |
| Console log | `GET /api/v2/servers/{id}/logs` |
| Send command | `POST /api/v2/servers/{id}/stdin` (plain-text body) |
| Backup profiles | `GET /api/v2/servers/{id}/backups` |
| Schedules | `GET /api/v2/servers/{id}/tasks`, `PATCH /api/v2/servers/{id}/tasks/{taskId}` |

Response parsing is deliberately lenient because field types differ between Crafty releases.

## Project layout

```
app/src/main/java/com/vknxworks/craftyremote/
├── MainActivity.kt              navigation + session gate
├── data/
│   ├── CraftyApi.kt             OkHttp client for the v2 API
│   ├── CredentialStore.kt       Keystore-encrypted token storage
│   └── Models.kt                lenient JSON → model parsing
└── ui/
    ├── ViewModels.kt            session, server list, server detail
    ├── Components.kt            status pills, tiles, banners, polling helper
    ├── theme/Theme.kt
    └── screens/                 Login, ServerList, ServerDetail (4 tabs)
```
