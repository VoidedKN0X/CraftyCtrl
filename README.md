# CraftyCtrl

**Manage your Minecraft servers on Crafty Controller from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/CraftyCtrl?label=download)](https://github.com/VoidedKN0X/CraftyCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

CraftyCtrl is an Android app for [Crafty Controller 4](https://craftycontrol.com), the self-hosted Minecraft server panel. Start, stop and watch your servers, use the console and manage backups without opening a browser.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/CraftyCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Server list** with live status, CPU, memory, players and uptime, and a broadcast to every running server
- **Start, restart, stop and kill**, with a fingerprint or face check (or your phone's screen lock) before restart, stop and kill, and an optional in-game countdown
- **Live console** with search, filters, quick commands and player-name completion
- **Logs and stats history** charts for CPU, memory and players
- **Server settings**: every `server.properties` option, whitelist, operators and bans
- **Players**: message, kick, ban, make operator, change game mode or clear inventory for any player
- **Plugins and mods**: turn them on or off, add or remove jars
- **File browser** with a built-in text editor, upload, unzip and more
- **Backups and scheduled tasks**: run, edit, restore and schedule; restoring asks you to type RESTORE first
- **Create, clone, update and delete** Java and Bedrock servers; deleting asks you to type the server name
- **Alerts** when a server stops or crashes, even with the app closed
- **App lock** with its own PIN and optional fingerprint unlock; your Crafty login stays encrypted while the app is locked, and an option hides the app in screenshots and recent apps
- Follow system, dark, light or pure black themes, and colours from your wallpaper

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Crafty Controller 4.x reachable over **HTTPS with a valid certificate** (for example behind a reverse proxy). Plain `http://` is not supported.
- Notification permission, only if you turn on server alerts
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

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
2. Sign in with an **API key** (in Crafty: your profile → *API Keys* → *Generate*) or your **username and password**, plus your 2FA code if you use one.
3. Tap **Connect**. Your login is stored encrypted on the phone; with a password, only the token Crafty sends back is kept, never the password itself.

## App lock

Turn it on in **⋮ → Settings → Privacy and security → App lock**. It's off until you turn it on.

- **An app PIN of its own**, at least 6 digits and separate from your phone's. Very obvious PINs, like 111111 or 123456, are refused. The PIN is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key that never leaves the phone's secure hardware, so a copy of the app's files isn't enough to guess it somewhere else.
- **Unlock with fingerprint** (optional): strong biometrics only, checked by the phone's secure hardware. Adding a fingerprint to the phone turns it off until you turn it on again. The PIN is still needed after the phone restarts and at least every 72 hours.
- **Wrong PINs make you wait**: 30 seconds after 5 wrong tries, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't end the wait.
- **Your Crafty login stays encrypted while CraftyCtrl is locked**, with a key that only exists in memory while the app is unlocked and is wiped the moment it locks.
- **Lock again**: as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking a file, opening a link or the battery settings from CraftyCtrl doesn't count as leaving, unless you're away for more than 5 minutes.
- **Hide in screenshots and recent apps** (on by default) blocks screenshots and screen recording and blanks the app in recent apps. Sign-in, the file editor and server settings are always hidden.
- **Server alerts keep working while locked**: while alerts are on, they keep their own copy of your Crafty login, encrypted with the Android Keystore as without the app lock. Turn alerts off to keep the login sealed with your PIN only.
- **Forgot PIN?** on the lock screen signs out and forgets the panel address and login on this phone, so you can sign in again. Your servers on Crafty aren't touched.

## Privacy

**Telemetry-free.** CraftyCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the Crafty panel you sign in to, plus two lookups you start yourself, and neither of those sends your panel address or login:

- Adding someone to the whitelist, operators or bans of an online-mode Java server looks the player name up at Mojang (`api.mojang.com`) to get the account's ID, as Minecraft itself does.
- The **Create server** screen loads the list of available server versions from Crafty's download catalog (`jars.arcadiatech.org`).

The Support, Report a bug, Source code and Licence links in Settings, and the Minecraft EULA link, open in your browser only when you tap them.

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

CraftyCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, **⋮ → Settings → About** has **Support CraftyCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/CraftyCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| **CraftyCtrl** (this app) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

CraftyCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

CraftyCtrl is an unofficial app and is not affiliated with the Crafty Controller team.
