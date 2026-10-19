# CraftyCtrl

**Manage your Minecraft servers on Crafty Controller from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/CraftyCtrl?label=download)](https://github.com/VoidedKN0X/CraftyCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

CraftyCtrl is an Android app for [Crafty Controller 4](https://craftycontrol.com), the self-hosted Minecraft server panel. Start, stop and watch your servers, use the console and manage backups without opening a browser.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/CraftyCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Server list** with live status, CPU, memory, players and uptime
- **Start, restart, stop and kill**, with a fingerprint check for stop and kill and an optional in-game countdown
- **Live console** with search, filters, quick commands and player-name completion
- **Logs and stats history** charts for CPU, memory and players
- **Server settings**: every `server.properties` option, whitelist, operators and bans
- **Plugins and mods**: turn them on or off, add or remove jars
- **File browser** with a built-in text editor, upload, unzip and more
- **Backups and scheduled tasks**: run, edit, restore and schedule
- **Create, clone and update** Java and Bedrock servers
- **Alerts** when a server stops or crashes; app lock with PIN and fingerprint

## Requirements

- Android 13 or newer
- Crafty Controller 4.x reachable over **HTTPS with a valid certificate** (for example behind a reverse proxy). Plain `http://` is not supported.

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/CraftyCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds CraftyCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/CraftyCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/CraftyCtrl` in Obtainium by hand.

## Getting started

1. Enter your panel address, e.g. `https://crafty.example.com`.
2. Sign in with an **API key** (in Crafty: your profile → *API Keys* → *Generate*) or your **username and password**.
3. Tap **Connect**. Your login is stored encrypted on the phone.

## Usage stats

CraftyCtrl can share anonymous usage stats, **only if you say yes**. It asks once after you sign in, and you can change it any time in **Settings → Usage stats**.

- **Shared:** which screens you open (the kind of screen, never which server or file), whether you start, stop, restart or kill a server, opening the console, starting a backup, whether you sign in with an API key or a password, the app version, Android version, phone model, language, phone or tablet, and your theme setting.
- **Never shared:** your panel address, username, password or API key, server or player names, console output or commands, file names, or any ID that stays the same between sessions.
- **Where:** VKNXWorks' own self-hosted [Aptabase](https://aptabase.com) server. Nothing goes to Google or analytics companies. Like any website, the server sees your IP address; Aptabase uses it only to work out the country and doesn't store it.

Builds from source don't include the stats key, so they can't send anything and the setting is hidden. The code is in [`Telemetry.kt`](app/src/main/java/com/vknxworks/craftyctrl/data/Telemetry.kt).

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/CraftyCtrl.git
   cd CraftyCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

CraftyCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/CraftyCtrl/issues) helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| **CraftyCtrl** (this app) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |

## License

CraftyCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

CraftyCtrl is an unofficial app and is not affiliated with the Crafty Controller team.
