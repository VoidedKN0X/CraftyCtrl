# Changelog

Each version below becomes a GitHub release. To publish a new one: bump `versionCode` and
`versionName` in `app/build.gradle.kts`, add a matching `## [x.y.z]` section here, and push to `main`.

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
