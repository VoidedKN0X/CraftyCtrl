# CraftyCtrl

An Android app for managing a [Crafty Controller 4](https://craftycontrol.com) panel from your phone, built with Kotlin and Jetpack Compose on Crafty's v2 API.

CraftyCtrl is an unofficial app. It isn't made by or affiliated with the Crafty Controller team.

## Features

**Servers**
- Server list with live status, CPU, memory, players and uptime, plus the host machine's CPU, memory and disk
- Start, restart, stop and kill. Stopping, restarting and killing ask for your fingerprint, face or screen lock
- Restarts and stops can warn players first, with a chat countdown you can cancel
- Broadcast one chat message to every running server
- Create new Java or Bedrock servers, clone, update the server jar, or delete a server

**Console and logs**
- Live console with search, warning/error filters, copy on long-press, quick commands and player-name completion
- Log viewer for `latest.log` and older logs, with search and filters
- Stats history charts for CPU, memory and players

**Configuration**
- Server settings: every `server.properties` key for Java and Bedrock, grouped and typed
- Players & bans: whitelist/allowlist, operators, banned players and IPs
- Server options: name, memory, auto-start, crash detection, stop command and more
- Plugins & mods: turn them on or off, add or remove jars
- File browser with a text editor, upload, create, rename, move, copy, unzip and delete

**Backups and schedules**
- Backup profiles: run, edit, restore and schedule
- Scheduled tasks: create, edit, enable, run now and delete

**App**
- Light and dark mode (or follow the system), Material You colours and an optional pure black mode
- App lock with a PIN and fingerprint or face unlock
- Background alerts when a server stops or crashes

## Requirements

- Crafty Controller 4.x reachable over **HTTPS with a valid certificate**, for example behind a reverse proxy. The app refuses plain `http://`.
- Android 13 or newer.

## First run

1. Enter the panel address, e.g. `https://crafty.example.com`. A pasted dashboard URL works, and reverse-proxy subpaths such as `/crafty` are kept.
2. Sign in with either:
   - **API key:** in Crafty open your profile → *API Keys* → *Generate*, and give it only the servers and permissions you need.
   - **Password:** your Crafty username, password and, if enabled, your 2FA code.
3. Tap **Connect**. The app checks the address and credentials before saving anything. The token is encrypted with the Android Keystore; passwords are never stored.

## Building from source

Open the project in Android Studio (Ladybug or newer) and run it, or build from a terminal with `./gradlew assembleDebug`.

## Crafty API endpoints used

| Feature | Endpoint |
| --- | --- |
| Login | `POST /auth/login` |
| Servers | `GET /servers`, `POST /servers`, `GET`/`PATCH`/`DELETE /servers/{id}` |
| Stats and history | `GET /servers/{id}/stats`, `GET /servers/{id}/history`, `GET /crafty/stats` |
| Power, backup, update, clone | `POST /servers/{id}/action/{action}` |
| Console | `GET /servers/{id}/logs`, `POST /servers/{id}/stdin` |
| Files | `POST`/`PATCH`/`DELETE /servers/{id}/files`, `PUT`/`PATCH /servers/{id}/files/create`, `POST /servers/{id}/files/upload/`, `POST /servers/{id}/files/zip` |
| Backups | `GET /servers/{id}/backups`, `PATCH`/`POST /servers/{id}/backups/backup/{backupId}` |
| Schedules | `GET`/`POST /servers/{id}/tasks`, `GET`/`PATCH`/`DELETE /servers/{id}/tasks/{taskId}`, `POST /servers/{id}/tasks/{taskId}/run` |

All paths are under `/api/v2`. Responses are parsed leniently, because field types differ between Crafty releases.
