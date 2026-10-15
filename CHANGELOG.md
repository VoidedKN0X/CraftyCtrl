# Changelog

Each version below becomes a GitHub release. To publish a new one: bump `versionCode` and
`versionName` in `app/build.gradle.kts`, add a matching `## [x.y.z]` section here, and push to `main`.

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

 - 2026-10-15

### New
- **Light mode.** The minimal look now comes in light too: a warm off-white background with near-black text.
- **Theme setting** in Settings → Appearance: Follow system (the default), Light or Dark. The status and navigation bar icons follow the app's choice.

 - 2026-10-15

### Changed
- **New minimal look.** A quiet near-black theme with off-white text, the Geist typeface (Geist Mono for numbers), thin divider lines instead of cards, and underlined text tabs.
- **Servers screen:** host CPU, memory and disk as large numbers with thin bars; each server is one line with a status dot, version and port, player count, and CPU, RAM and uptime while it runs.
- **Server overview:** status and uptime, CPU, memory and world size as large numbers, a single Start button when stopped (Restart / Stop / Kill when running), online players as small chips, and a short details list above the Manage pages.
- **Stats:** each chart is a single thin line with min, average and max underneath.
- **Accent colour** in Settings → Appearance: green, blue, amber, purple, or your wallpaper's colour (Android 12+). Colour is only used for server status and the players chart.

 - 2026-10-14

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
