# Changelog

Each version below becomes a GitHub release. To publish a new one: bump `versionCode` and
`versionName` in `app/build.gradle.kts`, add a matching `## [x.y.z]` section here, and push to `main`.

## [2.4.2] - 2026-10-17

### Security
- **Wrong-PIN lockout can't be skipped by changing the clock.** The wait after too many wrong PINs now runs on the phone's uptime clock, so setting the date or time forward no longer ends it early. It still carries on after a restart.
- Every PIN attempt is counted before it's checked, so closing the app right after a wrong PIN no longer resets the count.
- While the app is locked, the screens underneath are hidden from screen readers and other accessibility services.
- **Notifications on the lock screen:** when your phone hides sensitive notification content, server alerts show only "A server needs attention" and the alerts notification shows no server names, player counts or errors. Unlock to see details.
- The **Turn off** button on the alerts notification needs the phone unlocked first.
- Uploaded files keep only their own name. A file whose name (as given by the app it came from) contains a folder path like `../plugins/` can no longer land outside the folder you chose.
- File and folder names from the server that contain `/`, `\`, `.` / `..` or control characters are left out of the file list, and move, rename, delete and extract refuse paths that step out of the server's folder with `..`.
- Console commands are always sent as a single line, so a player name or message taken from the server can't sneak in a second command.
- **Server settings** (server.properties) always blocks screenshots and screen recording, like sign-in and the file editor, because RCON and management passwords can be revealed there.
- The API key, password and 2FA code typed on the sign-in screen are no longer kept in Android's saved screen state.
- Unencrypted HTTP is now switched off in the app's manifest too (it was already refused at sign-in).

### Fixed
- Tapping Back (or a list item) twice quickly during a screen change could close one screen too many and leave a black screen. Repeat taps during a transition are now ignored, and the server list is never closed.

## [2.4.1] - 2026-10-16

### Security
- **Hide content in screenshots & recents** (Settings → Privacy, on by default) blocks screenshots and screen recording. Sign-in and the file editor are always hidden, and the Recents card never shows a snapshot of the app.
- Fingerprint/face unlock now needs a strong (class 3) biometric and is backed by a Keystore key that only works after a real match. If fingerprints or faces change, unlock with your PIN and turn it on again.
- New PINs need at least 6 digits. Existing shorter PINs keep working until you change them.
- Copied diagnostics and console lines are marked as sensitive, so Android hides them from clipboard previews.
- Nothing is included in cloud backups or device-to-device transfers.
- Builds are never published signed with the debug key.

## [2.4.0] - 2026-10-16

### Changed
- **Server pages use the tab island too:** Overview, Console, Stats, Files, Backups and Schedules are now in the same floating frosted-glass island as the home screen, at the bottom instead of a tab row at the top. It steps aside while the keyboard is open, so the console's command field stays in view.

## [2.3.2] - 2026-10-16

### Changed
- The Servers header has a single ⋮ button, styled like the other tonal buttons, holding Broadcast, Settings and Sign out.

## [2.3.1] - 2026-10-16

### Fixed
- Text and icons on the Servers and Host tabs (including the ⋮ menu button) were black in dark mode. They now follow the theme: light in dark mode, dark in light mode.

## [2.3.0] - 2026-10-16

### Changed
- **Floating tab island:** the home screen's Servers / Host tabs are now a frosted-glass island floating above the content, like in SeerrCtrl and JellyCtrl. The list scrolls underneath it and blurs, and a selection bubble springs between tabs.
- Updated to the same Kotlin and Jetpack Compose versions as the other Ctrl apps.

## [2.2.0] - 2026-10-16

### Changed
- **Same layout as the other Ctrl apps.**
  - The home screen has bottom tabs: **Servers** and the new **Host** tab, which shows CPU, memory and disk meters plus servers running and players online.
  - Compact headers with an icon tile, plus quick buttons for broadcast and settings.
  - Servers show as rounded cards with CPU, memory and players.
  - The server page groups status and power, players, details and the Manage pages into cards, and uses standard Material tabs.
  - Stats charts sit in cards.
  - Settings has a large collapsing title, and its options are rows with icons, switches and segmented buttons.

## [2.1.0] - 2026-10-16

### Changed
- **Matches the other Ctrl apps' theme:** Material You colours by default, with CraftyCtrl's blue as the fallback; System / Light / Dark; the system font; and the same rounded shapes.
- **Settings → Appearance** now has Theme, **Material You colours** and **Pure black** (true black backgrounds in dark mode). The accent colour picker is gone.
- Server status colours (running, starting, crashed, stopped) are tuned separately for light and dark mode.

## [2.0.1] - 2026-10-15

### Changed
- **New icon:** the server rack in a blocky pixel style with a small pixel pickaxe. Android's themed (monochrome) icons use the same mark.

## [2.0.0] - 2026-10-15

### Changed
- **New package name: `com.vknxworks.craftyctrl`** (was `com.vknxworks.craftyremote`), to match the CraftyCtrl name.

### Upgrading
Android sees this as a new app, so it won't update over Crafty Remote or CraftyCtrl 1.x. Uninstall the old app, install this one, then sign in again and set your PIN, theme and alerts again.

## [1.6.0] - 2026-10-15

### Changed
- **Requires Android 13 or newer** (was Android 8). Code paths that only existed for older Android versions were removed.
- Confirming a stop, restart or kill now always accepts your phone's PIN, pattern or password as well as fingerprint or face.

## [1.5.1] - 2026-10-15

### Changed
- **Crafty Remote is now CraftyCtrl.** The new name shows on the home screen, sign-in and lock screens. It installs over earlier versions and keeps your sign-in and settings.

## [1.5.0] - 2026-10-15

### New
- **Plugins & mods** (server → Manage): see every plugin and mod, turn them on or off (the jar is renamed to `.jar.disabled`), remove them, and upload new jars.
- **Logs** (server → Manage): browse the logs folder, open `latest.log` or older logs, search them, filter to warnings or errors, and long-press a line to copy it. Compressed `.gz` logs are listed but can't be opened in the app.
- **Console upgrades:** search, Warnings/Errors filters, long-press any line to copy it, and online player names offered as you type a command.
- **Warned restarts and stops:** choose Now, 1, 5 or 10 minutes. Players get chat countdown warnings (with an optional reason), and a banner on the server page lets you cancel. Keep the app open until it finishes.
- **Broadcast:** the megaphone on the Servers screen sends one chat message to every running server.
- **Fingerprint / face confirmation** for stopping, restarting, killing, deleting, restoring and updating a server. Your phone's PIN or pattern also works. Phones without a screen lock only get the normal confirmation.

### Changed
- **More colour:** on Android 12+ the app now uses your wallpaper's Material You palette by default, for buttons, switches, tabs, chips, bars and charts. The fixed accents tint the app in the same way. Running servers always show a green dot.

## [1.4.1] - 2026-10-15

### New
- **Light mode.** The minimal look now comes in light too: a warm off-white background with near-black text.
- **Theme setting** in Settings → Appearance: Follow system (the default), Light or Dark. The status and navigation bar icons follow the app's choice.

## [1.4.0] - 2026-10-15

### Changed
- **New minimal look.** A quiet near-black theme with off-white text, the Geist typeface (Geist Mono for numbers), thin divider lines instead of cards, and underlined text tabs.
- **Servers screen:** host CPU, memory and disk as large numbers with thin bars; each server is one line with a status dot, version and port, player count, and CPU, RAM and uptime while it runs.
- **Server overview:** status and uptime, CPU, memory and world size as large numbers, a single Start button when stopped (Restart / Stop / Kill when running), online players as small chips, and a short details list above the Manage pages.
- **Stats:** each chart is a single thin line with min, average and max underneath.
- **Accent colour** in Settings → Appearance: green, blue, amber, purple, or your wallpaper's colour (Android 12+). Colour is only used for server status and the players chart.

## [1.3.1] - 2026-10-14

### Changed
- Alerts now only notify when a server stops or crashes. Player-join and backup alerts are gone, along with their notification categories in Android's settings.

## [1.3.0] - 2026-10-14

### New
- **Server settings.** Every `server.properties` setting for Java and Bedrock, grouped (gameplay, players, world, performance, network, resource packs, RCON/query/management, advanced) with switches, number fields, dropdowns and hidden passwords. Search by name or key. Settings your file has that the app doesn't know show under **Other**, and ones missing from your file show their default. Saving only changes what you edited and keeps comments and order; if the server is running you can restart it straight from the confirmation.
- **Players & bans.** Whitelist (allowlist on Bedrock), operators, banned players and banned IPs, with a switch to turn the whitelist on or off. While the server runs, changes go through the console and apply at once; while it's stopped, the app edits the list files directly (looking up the player's UUID).
- **Server options.** Crafty's own settings for a server: name, start with Crafty (and delay), crash detection, memory (min/max), stop command, shutdown timeout, the address and port Crafty checks, status page and player counting, log cleanup, update URL and the full start command.
- **Restore a backup.** **Restore a backup…** on each backup profile. Pick or type the archive, choose in place or a clean restore, and type RESTORE to confirm. The server is stopped first.
- **Run a schedule now** with the ▶ button on each schedule.
- **Extract .zip files** from a file's ⋮ menu in the Files tab.
- **Update server software** and **Clone server** in the server's ⋮ menu.
- **Backup alerts.** A notification when a backup finishes or fails (Settings → Server alerts → "A backup finishes or fails", on by default). Backup start isn't notified.
- Backup profiles show the result of their last run when Crafty reports it.

### Changed
- The server's Overview tab has a **Manage** section, and the ⋮ menu lists all the management pages.

## [1.2.1] - 2026-10-14

### Fixed
- New Java servers still wouldn't start from the app. The EULA file the app wrote had a comment line above `eula=true`; Minecraft accepts that, but Crafty only reads the first line, so it kept treating the EULA as not accepted. The app now writes exactly `eula=true`, like Crafty's web panel does, and checks it the same way Crafty does. Servers created with 1.1.1–1.2.0 are fixed the next time you press **Start** (accept the EULA prompt).

## [1.2.0] - 2026-10-14

### New
- **Delete a server.** On a server's page, open the **⋮** menu → **Delete server**. To confirm, you type the server's name. A running server is stopped first.
  - **Also delete its files** (on by default) removes the server folder with its world, configs and jar. Turn it off to only remove the server from Crafty and keep the files on disk.
- The diagnostics view moved into the same **⋮** menu.

## [1.1.1] - 2026-10-14

### Fixed
- New Java servers created in the app wouldn't start, because the Minecraft EULA you accepted in the form was never written to the server. The app now writes `eula=true` to `eula.txt` before starting it.
- Pressing **Start** on any Java server whose EULA isn't accepted now asks you to accept it (and shows a link to read it), instead of silently doing nothing.

## [1.1.0] - 2026-10-14

### New
- **Create servers from the app.** Tap **New server** on the Servers screen to set up a Java or Bedrock server:
  - Java: pick the server software (Vanilla, Paper, Purpur, Fabric, …) and version from Crafty's download list, and set memory and port.
  - Bedrock: Crafty downloads the official Bedrock server.
  - A free port is suggested automatically, and names or ports already in use are flagged.
  - The app waits while Crafty downloads the server files, then starts the server if you choose to, and opens its page.
  - Requires accepting the Minecraft EULA, and an account or API key allowed to create servers in Crafty.

## [1.0.0] - 2026-10-14

First release of Crafty Remote, an Android app for managing a Crafty Controller 4 panel.

### Servers
- Server list with live status, CPU, memory and player gauges, plus a host card with CPU, RAM and disk usage of the Crafty machine.
- Server screen with Overview, Console, Stats, Files, Backups and Schedules tabs.
- Power controls: start, restart, stop and kill, with confirmation for anything disruptive.
- Live console with coloured warnings and errors, quick commands and command history.
- Stats tab with CPU, memory and player graphs from Crafty's history (1 h / 6 h / 24 h / all).
- Server version from Crafty, its metrics, the console log or the jar name; fields Crafty can't report are hidden instead of shown empty.

### Players
- Tap a player for actions: message, game mode, op/deop, whitelist (allowlist on Bedrock), kick, ban (Java), clear inventory and kill.
- Manage a player by name for servers that don't share player names.

### Files
- Browse server folders and edit text files with an in-app editor.
- Create files and folders, upload files (chunked, with progress), rename, move, copy text files and delete.

### Backups and schedules
- Backup profiles with settings editing and "Back up now".
- Create, edit, enable/disable and delete schedules: start, stop, restart, backup or console command, repeating, daily at a time, or cron.
- Each backup shows and edits its own backup schedules.
- Works around Crafty's broken "list schedules" endpoint.

### Security and alerts
- Sign in with an API key or username, password and 2FA; the token is stored encrypted with the Android Keystore.
- App lock with PIN and optional fingerprint or face unlock, with a timed lockout after wrong PINs.
- Background alerts for player joins and for servers stopping or crashing, with adjustable check interval.

### Look and feel
- Material You colours from your wallpaper (Android 12+), rounded modern layout, ring gauges and smooth transitions.
- Adjustable live refresh for open screens (1–10 s).
- Diagnostics view (bug icon) that shows Crafty's raw replies for troubleshooting.
