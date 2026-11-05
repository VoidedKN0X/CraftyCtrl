# CraftyCtrl

**Manage your Minecraft servers on Crafty Controller from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/CraftyCtrl?label=download)](https://github.com/VoidedKN0X/CraftyCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

CraftyCtrl is an Android app for [Crafty Controller 4](https://craftycontrol.com), the self-hosted Minecraft server panel. Start, stop and watch your servers, use the console and manage backups without opening a browser.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/CraftyCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Server list** with live status, CPU, memory, players and uptime, and a broadcast to every running server
- **Start, restart, stop and kill**, with a fingerprint or face check (or your phone's screen lock) before restart, stop and kill, and an optional in-game countdown
- **Live console** streamed over Crafty's own WebSocket as lines are printed, reconnecting by itself and falling back to refreshing the log; with search, filters, quick commands and player-name completion
- **Logs and stats history** charts for CPU, memory and players
- **Server settings**: every `server.properties` option, whitelist, operators and bans
- **Players**: message, kick, ban, make operator, change game mode or clear inventory for any player
- **Plugins and mods**: turn them on or off, add or remove jars
- **File browser** with a built-in text editor, upload, unzip and more
- **Backups and scheduled tasks**: run, edit, restore and schedule; restoring asks you to type RESTORE first
- **Create, clone, update and delete** Java and Bedrock servers; deleting asks you to type the server name
- **Alerts** when a server stops or crashes, even with the app closed, and **player join and leave alerts** for the servers you pick, grouped into one notification
- **Push from Crafty through ntfy** (UnifiedPush): one tap adds a webhook to every server so starts, stops, kills and crashes wake CraftyCtrl at once. Choose the foreground monitor or push only (no ongoing notification, with a check every 15 minutes as a fallback)
- **HTTPS by default; plain HTTP only for your own panel after a warning**, never for anything else. An HTTPS connection is never redirected to HTTP, and your login, password and anything you send are never passed on to another server by a redirect
- **App lock** with its own PIN and optional fingerprint unlock; your Crafty login stays encrypted while the app is locked, and two switches keep the app out of screenshots and the recent apps view
- The Ctrl / Signal look shared by every Ctrl app: dark and light modes that follow your phone, one violet accent, Space Grotesk and JetBrains Mono

## Requirements

- Android 15 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Crafty Controller 4.x. **HTTPS by default** (with a certificate Android trusts, for example behind a reverse proxy; Crafty's own self-signed certificate isn't accepted); plain `http://` only for your own panel, after a warning
- Notification permission, only if you turn on server alerts
- For push: the [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) app connected to your own ntfy server, and Crafty 4.4 or newer for webhooks
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Open the [Releases page](https://github.com/VoidedKN0X/CraftyCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `craftyctrl-2.10.2.apk`) to download it. The "Source code" zip and tar.gz files are not the app.
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds CraftyCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/CraftyCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/CraftyCtrl` in Obtainium by hand.

## Is it safe to install?

- **Open source.** All of CraftyCtrl's code is in this repo, under the GPL-3.0 licence. Anyone can read it.
- **Built by GitHub, not on a personal computer.** Every APK on the Releases page is built by GitHub Actions from the code in this repo, using [the build workflow](.github/workflows/build.yml). You can see each build's log under the Actions tab. Each build also checks that the Gradle wrapper is an official Gradle release and that no Google Play services, Firebase, ML Kit, Play Core or Tink library is in the app.
- **Always signed with the same key.** Every release is signed with the same release key. Android refuses to install an update signed with a different key over the app you have, and the CtrlF-Droid repo only accepts APKs signed with this key. Its SHA-256 certificate fingerprint is:

  ```
  AD:45:50:1C:0C:C0:F7:E9:CA:68:F7:A7:71:99:49:96:40:7E:50:DE:B8:5A:2A:B8:59:20:8F:DA:D3:60:CB:57
  ```

  Advanced users can compare it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
- **Check a download.** Every release lists the APK's SHA-256 in its release notes, so you can compare it with `sha256sum craftyctrl-X.Y.Z.apk`. Each release APK also has a signed build provenance attestation: with the [GitHub CLI](https://cli.github.com/), `gh attestation verify craftyctrl-X.Y.Z.apk -R VoidedKN0X/CraftyCtrl` proves it was built by this repo's build workflow from the tagged commit. The signing certificate check above still applies.
- **Checked dependencies and workflows.** Every library and Gradle plugin the build downloads is checked against the SHA-256 in [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml), so a changed download fails the build. The workflows are checked with [zizmor](https://docs.zizmor.sh/), each job gets only the permissions it needs, and every action is pinned to a commit. [CodeQL](https://codeql.github.com/) scans the code every week.

**Permissions** CraftyCtrl asks for, and why:

| Permission | What it's for |
| --- | --- |
| Internet (`INTERNET`) | Talking to your Crafty panel, and the two lookups in [Privacy](#privacy) |
| Notifications (`POST_NOTIFICATIONS`) | Server alerts and player alerts, only if you turn them on |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | The foreground monitor that watches your servers while alerts are on |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Turning alerts back on after the phone restarts or the app updates |
| Prevent phone from sleeping (`WAKE_LOCK`) | The optional **Keep checking while the screen is off** setting for alerts |
| Ask to ignore battery optimizations (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | Asks Android to let alerts run without battery limits, only when you turn on that same setting |
| Use biometrics (`USE_BIOMETRIC`) | Optional fingerprint unlock for the app lock, and the fingerprint or face check before restart, stop and kill |
| Hide other apps' floating windows (`HIDE_OVERLAY_WINDOWS`) | Hide other apps' floating windows over the PIN screen, sign-in and delete confirmations, so nothing can be drawn over them. Granted at install, no prompt |

Android also lists a "dynamic receiver" permission (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) that AndroidX adds to every app so only CraftyCtrl itself can send it internal broadcasts; it gives the app no access to anything. CraftyCtrl has no location, camera, microphone, contacts, storage or phone permissions: files you upload are picked with Android's file picker, which only hands over the files you choose.

What CraftyCtrl sends and to whom is in the [Privacy](#privacy) section.

## Getting started

1. Enter your panel address, e.g. `https://crafty.example.com`. Without `https://` or `http://` in front, HTTPS is used. For a panel on your home network without HTTPS, type `http://` in front: CraftyCtrl warns that your login and everything else are then sent unencrypted, and only connects after you tap **Use HTTP**. The panel then shows **Not encrypted (HTTP)** on the Servers tab and the You page.
2. Sign in with an **API key** (in Crafty: your profile → *API Keys* → *Generate*) or your **username and password**, plus your 2FA code if you use one.
3. Tap **Connect**. Your login is stored encrypted on the phone; with a password, only the token Crafty sends back is kept, never the password itself.

Settings, signing out and About are behind your profile picture at the top right of the Servers and Host tabs: tap it to open the You page, then **Settings**. **Broadcast** (a chat message to every running server) is the megaphone button next to it.

## Alerts and push

Turn on **Settings > Notifications > Server alerts** (tap your profile picture, then **Settings**).

- **Foreground monitor** (the default): CraftyCtrl checks your servers every 15 or 30 seconds, or 1 or 5 minutes, with a quiet ongoing notification. A stop is only reported once the server stays down for two checks, so restarts don't alert; crashes are reported straight away.
- **Player alerts:** under **Player alerts**, switch on the servers whose joins and leaves you want. Each notification names the player and the server, and they're grouped into one. Names come from Crafty's online player list, or from the console's "joined the game" / "left the game" lines when the list has none. Crafty has no webhook for players, so these always come from the checks.
- **Push from Crafty:** install [ntfy](https://f-droid.org/packages/io.heckel.ntfy/), connect it to your ntfy server, and turn on **Push from Crafty > Receive Crafty webhooks**. CraftyCtrl shows its push address and can add a webhook named CraftyCtrl to every server for you (**Set up webhooks on all servers**, Crafty 4.4+). To do it by hand, open each server's **Webhooks** tab in Crafty and add one with provider *Discord*, the push address as URL, the events *Server Start*, *Server Stop*, *Server Crash* and *Server Killed*, and the message `CRAFTYCTRL|{{ event_type }}|{{ server_id }}|{{ server_name }}`.
- **Push only:** with push on you can pick **Alerts run as > Push only**. Then no service runs in the background and there's no ongoing notification: every push starts a check at once, and a check every 15 minutes catches anything a push missed.
- Every push only makes CraftyCtrl check with Crafty; nothing is notified from what the push says alone, so someone who learns the push address can't fake alerts. Keep the address private anyway.

## App lock

Turn it on under **Settings > Privacy and security > App lock** (tap your profile picture at the top right, then **Settings**). It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your Crafty login is encrypted with the PIN while CraftyCtrl is locked.** The key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking a file to upload, opening a link or the Minecraft EULA, and opening battery settings from CraftyCtrl don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your login. The lock screen offers to sign out, turn off server alerts and forget the panel address and login on this phone; your servers on Crafty aren't touched.
- **With the app lock on**, the recent-apps screen always shows a blank card.
- **Server alerts keep working while locked**: while alerts are on, they keep their own copy of your Crafty login, encrypted with the Android Keystore as without the app lock. Turn alerts off to keep the login sealed with your PIN only.

**Hide in screenshots** and **Hide in recent apps** are in the same section, work without the app lock too, and are both off by default. The first keeps CraftyCtrl out of screenshots and screen recordings; the second shows a blank card in the recent-apps view instead of the app. Sign-in, the lock screen, the PIN screens, the file editor and server settings are always kept out of screenshots, whatever the switches say.

## Privacy

**Telemetry-free.** CraftyCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the Crafty panel you sign in to, plus two lookups you start yourself, and neither of those sends your panel address or login:

- Adding someone to the whitelist, operators or bans of an online-mode Java server looks the player name up at Mojang (`api.mojang.com`) to get the account's ID, as Minecraft itself does.
- The **Create server** screen loads the list of available server versions from Crafty's download catalog (`jars.arcadiatech.org`).

Both are always HTTPS. Your panel is HTTPS by default too; plain HTTP is only used for a panel address you typed as `http://` and accepted after the warning.

Push alerts go through the [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) app and your own ntfy server, only if you turn them on. CraftyCtrl itself never contacts the ntfy server: the ntfy app hands it the push address and the webhook messages, and every message only makes CraftyCtrl check with your panel.

The Support, Report a bug, Source code and Licence links on the You page (tap your profile picture), the **Install ntfy** link (f-droid.org) in the push settings, and the Minecraft EULA links (aka.ms and minecraft.net) open in your browser only when you tap them.

## How CraftyCtrl is made

CraftyCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

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

**Dependency verification.** Gradle checks every library and plugin it downloads against the SHA-256 checksums in `gradle/verification-metadata.xml`, and the build fails if one doesn't match or is missing. When you add or update a dependency or plugin, run the **Dependency verification metadata** workflow (Actions tab, *Run workflow*), decode the file from its log (between the BEGIN and END markers: `base64 -d | gunzip`), check the diff and commit it together with the dependency change.

## Support the project

CraftyCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, tap your profile picture: **About** at the bottom of the You page has **Support CraftyCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/CraftyCtrl/issues). Starring the repo helps too.

Found a bug or have an idea? [Open an issue](https://github.com/VoidedKN0X/CraftyCtrl/issues/new/choose) and pick **Bug report** or **Feature request**; the form asks for what's needed.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Ctrl / Signal design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| **CraftyCtrl** (this app) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

CraftyCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The app bundles two fonts, both under the [SIL Open Font License 1.1](https://openfontlicense.org) (licence texts in `app/src/main/assets/licenses/`): [Space Grotesk](https://github.com/floriankarsten/space-grotesk) by Florian Karsten and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) by JetBrains.

CraftyCtrl is an independent app and isn't made by or affiliated with Crafty Controller (Arcadia Technology) or Mojang/Microsoft.
