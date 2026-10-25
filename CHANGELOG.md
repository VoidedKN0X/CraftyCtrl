# Changelog

Each version below becomes a GitHub release. To publish a new one: bump `versionCode` and
`versionName` in `app/build.gradle.kts`, add a matching `## [x.y.z]` section here, and push to `main`.

## [2.10.0] - 2026-10-25

### Changed

- **Plain HTTP panels work now.** If your Crafty panel runs on your home network without HTTPS, type `http://` in front of its address when signing in. Before, CraftyCtrl refused any `http://` address with "Use an https:// address"
- **A warning comes first.** Signing in to an `http://` panel opens **Use an unencrypted connection?**, which explains that your API key or password, your login token, the console and everything else are then sent unencrypted, so anyone on the network can read them: fine on your home network or over a VPN, risky anywhere else. CraftyCtrl only connects after you tap **Use HTTP**; **Cancel** leaves you on the sign-in screen. While you type an `http://` address, a red line under it says it isn't encrypted
- **Your choice is remembered for that panel** (its address and port), so server alerts, push checks and signing in again work without asking again. **Forgot PIN?** on the lock screen forgets it along with the rest
- **"Not encrypted (HTTP)"** now shows next to the panel address on the Servers tab and on the You page (tap your profile picture) when you use an `http://` panel
- **The live console works on HTTP panels too**: it connects over `ws://` to an `http://` panel and over `wss://` to an `https://` one, as Crafty's own web panel does
- An address typed without `https://` or `http://` still means HTTPS

### Security

- **HTTPS stays the default and is never quietly dropped.** CraftyCtrl never switches an `https://` panel to HTTP, never follows a redirect from HTTPS to HTTP, and never sends your login over HTTP to an address you didn't accept; such a connection is blocked with "Blocked an unencrypted (HTTP) connection"
- **Mojang's name lookup (`api.mojang.com`) and Crafty's download catalog (`jars.arcadiatech.org`) are always HTTPS**, on every redirect as well, whatever you choose for your own panel
- Certificates are checked as before: only certificates Android itself trusts are accepted for HTTPS panels
- Push from Crafty through ntfy is unchanged: your push address and the webhook setup work the same with an `http://` or `https://` panel

## [2.9.1] - 2026-10-25

### Fixed

- **No more crash when you close the app with the app lock set to lock immediately.** Pressing Back on the Servers screen or swiping CraftyCtrl away from recent apps locked the app as it closed, and the lock screen then tried to open on a screen that was already gone, which could crash the app. The app still locks as before and opens locked next time; the lock screen just waits until CraftyCtrl is actually on screen
- **The live console no longer keeps a connection open after you leave it.** When the console lost its connection and reconnected around the moment you switched tabs, left the server or left the app, a late message from the old connection could make CraftyCtrl forget the new one without closing it, so it kept streaming in the background. Late messages from old connections are now ignored, and a connection that can't close cleanly is cut off at once
- **Turning on fingerprint unlock in Settings no longer crashes** when Android refuses to show the fingerprint prompt (for example while the app is being left); it shows the usual "couldn't set up fingerprint unlock" message instead
- **Diagnostics no longer crash when the app locks while they run**: the report stops and shows that CraftyCtrl is locked
- **A background check that Android stops early** (out of time, or the network went away) no longer reports itself finished a second time

### Security

- **Turn off on the ongoing alerts notification** now does everything turning alerts off in Settings does: with the app lock on, the separate copy of your Crafty login that alerts use while locked is deleted, and what alerts last saw is forgotten. Before, the copy stayed on the phone until you next opened Settings
- **A push address from ntfy is only accepted when it's a valid web address**, so a malformed one can't be shown or set up as a webhook

## [2.9.0] - 2026-10-25

### New

- **Push from Crafty through ntfy.** Under **Settings > Notifications** there's a new **Push from Crafty** section. Install the ntfy app (from F-Droid), connect it to your own ntfy server and turn on **Receive Crafty webhooks**: CraftyCtrl registers with ntfy (UnifiedPush, and only ntfy) and shows its **push address**. Tap it to copy it; it's copied as sensitive, so it stays out of clipboard previews
- **Set up webhooks on all servers.** One button adds a webhook named CraftyCtrl to every server on the panel through Crafty's API (Crafty 4.4 or newer), pointing at your push address, for **Server Start, Server Stop, Server Crash and Server Killed**. Tapping it again updates those webhooks instead of adding new ones. If your Crafty doesn't know every event, it falls back to start and stop. Your account needs permission to change the servers' webhooks
- **Or set it up by hand:** the same section shows exactly what to enter in a server's **Webhooks** tab in Crafty (provider Discord, the push address as URL, the four events and the message line `CRAFTYCTRL|{{ event_type }}|{{ server_id }}|{{ server_name }}`), with a button to copy the message
- **Every push makes CraftyCtrl check your servers straight away.** A stop, kill or crash push is confirmed with Crafty first (a stop has to last 20 seconds, so a restart doesn't alert), and a stop that was already notified isn't notified again by the next check. Anyone who learns the push address can only make CraftyCtrl check; they can't make it show false alerts
- **Choose how alerts run.** With push on, **Alerts run as** offers **Foreground monitor** (as before: checks every 15 or 30 seconds or 1 or 5 minutes with a quiet ongoing notification, and pushes add an extra check at once) or **Push only**: no service in the background and no ongoing notification. Each push starts a check, and a check every 15 minutes catches anything a push missed. Without ntfy installed, alerts always use the foreground monitor
- **Player join and leave alerts.** A new **Player alerts** section lists your servers with a switch each (all off at first). For the servers you switch on, CraftyCtrl notifies when a player joins or leaves, with the player's name and the server's name, for example "Steve joined Survival", and how many players are online now. Player notifications are grouped into one with a summary of the latest joins and leaves, and have their own notification channel, **Players joining and leaving**, so you can set their sound separately. Names come from Crafty's online player list, or from the console's "joined the game" / "left the game" lines when the list has none. Crafty has no webhook for players, so they're noticed by the next check (within seconds to minutes with the foreground monitor, within 15 minutes or at the next push with push only). A failed ping that briefly reports 0 players doesn't count as everyone leaving
- **The console is now live.** It connects to Crafty's own WebSocket (the one the web panel's terminal uses), so new lines appear the moment the server prints them instead of every couple of seconds. A **LIVE** label with a violet square shows above the console while it streams; **POLLING** with a grey square means it's refreshing the log the old way. If the connection drops, CraftyCtrl reconnects on its own (after 2, 4, 8 … up to 60 seconds) and refreshes the log meanwhile, and after reconnecting it fills in anything printed while it was away. The socket closes when you leave the Console tab or the app, and isn't opened while the app is locked. Commands are still sent the way Crafty expects them, through its command API

### Changed

- **The Server alerts description says what alerts do now**: stops and crashes, and player joins and leaves on the servers you pick. With push only it explains that there's no ongoing notification and checks run every 15 minutes as a fallback
- **The foreground monitor's description in Android's settings** (what Android shows for its special-use service) now says it notifies when a server stops or crashes, and when players join or leave the servers you picked. It used to promise player joins while the app didn't send them
- **Alerts remember what they last saw**, so a check after a push or after the phone restarts still compares with the one before and doesn't miss a stop or a join. Turning alerts off or signing out forgets it
- **"Server alerts paused"** (when Crafty signs the phone out) is now one notification that's replaced rather than stacked
- After a command, the console no longer reloads the whole log while it's live: the reply streams in

## [2.8.0] - 2026-10-25

### New

- **Light mode that follows the device theme.** CraftyCtrl now switches between **Signal Dark** and the new **Signal Light** on its own, whenever your phone switches between dark and light mode (including scheduled or sunset-to-sunrise dark mode). There's no setting for it and no theme picker: the app always matches the phone. Material You and wallpaper colours stay off in both modes
- **Signal Light colours**: a warm off-white background (#F5F5F1), white cards and sheets with a thin light-grey border, near-black text (#0B0B0D) and grey secondary text (#5C5C64)
- **The violet accent is the same in both modes.** Hero cards, main buttons, the selected tab, switches that are on, the lock badge, your profile picture on the Profile page and entered PIN squares keep the #B48CFF fill with near-black content
- **A deeper violet for text and icons in light mode** (#6A3BDB), because the fill violet is too pale to read on white. It's used for the slash in the CRAFTY/CTRL wordmark, running-server status words and squares, the focused text-field border and cursor, the selected option in pickers, the selected tab in tab rows, link-style buttons such as **Allow**, dialog icons, the CPU line in stats charts and the outlined fingerprint key on the lock screen. In dark mode these stay #B48CFF

### Changed

- **Every screen works in both modes**: the Servers and Host tabs, a server's Overview, Stats, Files, Backups and Schedules tabs, the management pages (server.properties, players, add-ons, logs, options), creating a server, the sign-in screen, Profile, Settings, the lock and PIN screens, dialogs, bottom sheets and menus
- **The status bar and navigation bar** take the background colour of the current mode, with light icons in dark mode and dark icons in light mode. They update straight away when the phone switches. The app also starts on the right background colour, so there's no dark flash in light mode
- **The floating bottom bar** in light mode is white at 92 % with a thin grey border; the selected tab is still the violet block
- **Switches in light mode**: violet with a near-black knob when on, light grey with a grey knob when off
- **Progress bars and usage meters in light mode**: a light grey track with a near-black fill (amber and red as they fill up)
- **Errors** are red #D93636 in light mode (#FF6B6B in dark), and error banners use a pale red background
- **The lock screen in light mode**: white keypad keys with a thin border, empty PIN squares outlined in grey, entered squares violet
- **The console stays dark in both modes**, as do the log viewer under **Logs**, the file editor and the **Diagnostics** report. They keep the near-black background and log colours (white text, amber warnings, red errors) so logs read the same as in a terminal
- The server-state colours (running, starting or updating, crashed, stopped) are tuned for each mode so they stay readable

## [2.7.0] - 2026-10-25

CraftyCtrl gets the new **Ctrl / Signal** look, the design every Ctrl app now shares. Only the look changes: every screen, feature, button and setting works exactly as before.

### New

- **The Signal look, everywhere.** A near-black background (#08080A), flat dark cards with a thin border, and one violet accent colour (#B48CFF) for the things that matter: running servers, the selected tab, switches that are on and main buttons
- **New fonts, built into the app.** Space Grotesk for all text and headings, and JetBrains Mono for numbers, stats, labels, the console and file editor. Both are bundled under the SIL Open Font License 1.1, with no font downloads and nothing from Google. The licence texts ship inside the app (`assets/licenses`) and are credited in the README
- **New Servers overview**, laid out like ProxCtrl's. A **CRAFTY/CTRL** wordmark at the top left, a mono "Panel · online" label and a big **Servers** title, then a violet hero card with the total **players online** across your running servers (and their combined slots), the panel's CPU in its corner and a live CPU bar chart. Under it are **Memory** and **Storage** cards with the panel machine's usage and a thin bar. The server list follows as plain rows: port on the left, then name, version, players and memory, and on the right the state in mono with a small coloured square (violet running, amber starting or updating, red crashed, grey stopped)
- **New Host tab**: CPU load as the violet hero card (with the live CPU bars), **Servers running** and **Players online** as cards, then the System, Storage and Machine cards as before
- **New server Overview hero**: while a server runs, its violet hero card shows the players online out of the maximum (or CPU when Crafty doesn't report players), how long it has been up and a live CPU bar chart, with **CPU** and **Memory** cards and their bars underneath. A stopped or crashed server shows its state in a plain card instead

### Changed

- **Top bars** no longer have a coloured fill. The main tabs show the **CRAFTY/CTRL** wordmark on the left (the slash in violet), with Broadcast and your profile picture on the right. Other screens have a square back button and the title in Space Grotesk. The ⋮ menu on a server is now a square button too
- **Your profile picture** is now a rounded square with your initials (or the placeholder figure for API-key sign-ins) and a small violet square at its corner while you're connected. Tapping it still opens the You page
- **Bottom tabs** (Servers / Host, and a server's Overview, Console, Stats, Files, Backups and Schedules) are a floating dark bar with a thin border. The selected tab is a violet block with its icon and name, the other tabs show just their icon. Content scrolls clear of the bar
- **Switches** are rounded rectangles: violet with a dark knob when on, dark grey with a grey knob when off. They still read out as switches to TalkBack
- **Buttons**: main actions are violet with dark text, other buttons are dark with a thin border. Corners are 16 dp throughout, and **Start**, **Connect** and **Create server** are taller (56 dp)
- **Text fields** are dark with a thin border and turn violet while you type. Search fields are no longer pill-shaped
- **Cards** have 22 dp corners and a thin border; section headings are small upper-case mono labels instead of coloured titles
- **Progress bars and usage meters** are 6 dp tall with rounded ends, white on a dark track (amber and red as they fill up)
- **Status dots** are small rounded squares instead of circles, and state names are shown in mono capitals
- **The console** sits on the deepest black in JetBrains Mono, in a bordered card. The send button is a violet square
- **Stats charts**: CPU is drawn in violet, memory in white and players in grey, with the current value in large type
- **Dialogs and bottom sheets** are dark cards with 26 dp corners and a thin border
- **The lock screen**: a violet rounded-square lock badge, a "VKNXWORKS · CTRL" label and a big **Locked** title. The PIN is shown as small squares (violet when entered), and the keypad has three columns of tall rounded keys in JetBrains Mono. The fingerprint key, outlined in violet, sits bottom-left on the keypad, and the delete key is icon-only. How the lock works is unchanged: same PIN rules, fingerprint unlock, wrong-PIN wait and Forgot PIN
- **The PIN screens** for turning the lock on or off and changing the PIN use the same keypad, with **Next** or **Done** as the outlined key bottom-left
- **The You page** is now titled **Profile**. It shows a large violet picture with your initials, your name and the panel address in mono, then grouped sections with mono labels and thin dividers. **Support CraftyCtrl** is a violet button at the bottom (it moved there from the About list), above the version line and a "GOOGLE-FREE · TELEMETRY-FREE · ARM64" footer
- **Settings** groups use the same look: mono labels ("NOTIFICATIONS", "PRIVACY"…), dark bordered cards with dividers between rows, plain outlined icons and the new switches. **Hide in screenshots** and **Hide in recent apps** are still off until you turn them on
- **Sign-in screen**: the CRAFTY/CTRL wordmark over a big CraftyCtrl title, and a violet API key / password switcher
- The status bar and navigation bar always match the dark background, with light icons, and the app starts on the same dark colour

### Removed

- **The Appearance section in Settings is gone**, with its **Theme** (Follow system, Dark, Light, Pure black) and **Dynamic colour** options. CraftyCtrl is now always dark with the Signal colours and no longer takes colours from your wallpaper. A theme you picked before is ignored

## [2.6.4] - 2026-10-22

### Fixed

- **Server alerts now actually get permission to notify you.** On Android 13 and later an app has to be allowed to show notifications, and CraftyCtrl only asked at the moment you turned on **Server alerts**. If notifications were blocked later (or never allowed), the switch still showed "on" while every alert, and the quiet "Server alerts on" notification, was silently dropped. Now, when server alerts are on and notifications aren't allowed, CraftyCtrl asks for them once by itself as soon as you're signed in and unlocked, and never nags again after that.
- **The Notifications section in Settings tells the truth.** While notifications are blocked, the **Server alerts** switch no longer shows "on". A **Notifications are blocked** row appears instead, explaining that alerts are paused, with an **Allow** button: it shows Android's permission dialog while Android still offers it, and otherwise opens CraftyCtrl's notification settings. Coming back from there updates the section straight away, and alerts start again on their own once notifications are allowed. A **Turn off server alerts** row lets you switch them off completely instead.
- **Alerts no longer run in the background when they can't reach you.** With notifications blocked, the alerts service stays stopped instead of checking your servers for nothing, and picks up again the next time you open CraftyCtrl with notifications allowed.
- Showing Android's notification dialog or settings no longer locks the app when you come back to it.

## [2.6.3] - 2026-10-21

### Fixed

- **Fingerprint unlock works again after you leave the app.** Once you'd left CraftyCtrl with the back gesture or by swiping home, the lock screen could stop asking for your fingerprint when you came back, and tapping **Use fingerprint** then did nothing either; only the PIN worked until the app was force-closed. The fingerprint prompt was asked for while CraftyCtrl was still coming back on screen, and the phone dropped it without answering, which left the prompt stuck as "already open". Now the prompt only opens once CraftyCtrl is fully back on screen, a prompt that never answered can't block the next one (tapping **Use fingerprint** always opens a fresh one), and the lock screen offers your fingerprint again every time you come back to it. The same applies to the prompt for turning on fingerprint unlock in **Settings**.

## [2.6.2] - 2026-10-21

### Privacy

- **No hidden dependency list in the APK**: Android's build tools used to tuck an encrypted list of the app's libraries, readable only by Google, into the APK's signature block. It's no longer added, so the APK holds nothing that only Google can read and passes F-Droid's check for it. Nothing changes in the app itself.

### Changed

- The "not in file" note next to a setting in **Server settings** is now one text with the setting's name in it, ready for translation. It looks the same as before
- The "e.g. 1.21.4" hint in the Minecraft version field when creating a server comes from the same text list as everything else

## [2.6.1] - 2026-10-21

A tidy-up release: all of CraftyCtrl's text now lives in one place.

### Changed

- **All text is now in one place, ready for translations.** Every label, message, notification and error the app shows now comes from a single list instead of being written into the code
- **Counts read properly for one and for more than one**: "1 server" and "3 servers", "1 player online", "Save 1 change", "Fix 1 invalid value", "1 core", "1 day", "1 minute" and "15 seconds" in the alert and refresh intervals, and "Every hour" or "Every 5 hours" for schedules (also for seconds, days and weeks)
- **Delete server** now says "The server is removed from Crafty (it will be stopped first). This can't be undone." as one sentence, and the **Restore** warning reads as one message too
- **The countdown warnings sent to players** ("Server restarting in 5 minutes.") and the in-app message when a countdown action fails ("Restart failed.") are worded the same way as the rest of the app
- The repeat units in the schedule editor (minutes, hours, days) and the min, avg and max under the stats charts now come from the same text list, ready for translation

## [2.6.0] - 2026-10-21

Settings now open from your profile picture, the same in every Ctrl app.

### New

- **Your profile picture at the top right** of the Servers and Host tabs. It shows the first letter of your Crafty username, or the round placeholder picture the other Ctrl apps use when you signed in with an API key. Tapping it opens the new **You** page
- **The You page** shows who you're signed in as and how (username and password, or an API key), which Crafty panel you're connected to, then **Settings** and **Sign out**, and **About** (Support CraftyCtrl, Report a bug, Source code, Licence) with the version underneath, in the same layout as NaviCtrl, MemoCtrl, ProxCtrl and TrueCtrl
- **Sign out asks first** and says what it does: CraftyCtrl forgets your login on this phone and server alerts stop until you sign in again, while the panel address and username stay filled in. Before, Sign out in the menu signed you out straight away

### Changed

- **Settings moved to your profile picture**: before, Settings was in the **⋮** menu on the Servers screen. Now tap your profile picture, then **Settings** for Appearance, Notifications, Privacy and security and Live stats. It's the only way in, as in every Ctrl app
- **The ⋮ menu on the Servers screen is gone.** **Broadcast** is now its own megaphone button next to your profile picture, and **Sign out** is on the You page
- **About moved from the bottom of Settings to the bottom of the You page**, with the same four rows
- **README**: Getting started says where Settings, Sign out and About are; the App lock, Privacy and Support sections say "tap your profile picture" instead of "⋮ › Settings"

## [2.5.4] - 2026-10-21

### Changed

- **Hide in screenshots** and **Hide in recent apps** are now two separate switches in Settings › Privacy and security, both off by default, the same in every Ctrl app. Hide in screenshots makes screenshots and screen recordings show a blank screen; Hide in recent apps shows a blank card in the recent-apps view instead of the app (screenshots still work). Before, one setting, "Hide content in screenshots & recents", did both and was on unless you turned it off, and the recent-apps view was always blank. **What changes for you:** if you had turned that setting on yourself, both new switches are on. If you never changed it, both are now off, so screenshots work and the recent-apps view shows CraftyCtrl; turn them on if you want the old behaviour. With the app lock on, the recent-apps view always shows a blank card.
- **Always protected, whatever the switches say:** sign-in, the lock screen, the PIN screens, the file editor and server settings are never in screenshots, screen recordings or casting.
- The app lock's text and the Privacy and security rows now come from the app's translatable text resources, with the same wording as the other Ctrl apps. Nothing else changes in how they work.
- **README**: the App lock section now uses the same wording as the other Ctrl apps and describes the two switches.

## [2.5.3] - 2026-10-21

### Security

- **The lock screen and the PIN screens are never in screenshots, screen recordings or casting**, whatever "Hide in screenshots and recent apps" is set to, the same as sign-in already was. A recording would otherwise show which keys of the PIN pad were pressed. This applies to the lock screen, and to choosing, changing or confirming your app PIN in Settings.

## [2.5.2] - 2026-10-21

The app lock and Settings now look and work the same as in every other Ctrl app.

### Changed

- **New lock screen look**, the same as in the other Ctrl apps: a round lock badge, a bigger PIN pad with a short buzz and shake on a wrong PIN, a spinner while the PIN is checked, and **Forgot PIN?** (which still asks first, then signs out and resets).
- **Turning the app lock on, changing the PIN and turning the lock off now use the same full-screen PIN pad** as the lock screen, instead of a dialog with text boxes. Choosing a PIN asks for it twice; changing it asks for your current PIN first; turning the lock off asks for your PIN.
- **Settings are grouped like in the other Ctrl apps**, in this order: **Appearance**, **Notifications** (server alerts, how often to check, and checking while the screen is off), **Privacy and security**, **Live stats** and **About** at the bottom. The rows have the same look as the other apps, and choices such as **Lock again**, **Check every** and **Refresh open screens every** open a list to pick from.
- **Privacy and security** has the same rows, wording and icons as every Ctrl app: **App lock**, **Change app PIN**, **Unlock with fingerprint**, **Lock again** and **Hide in screenshots and recent apps** (formerly "Hide content in screenshots & recents"). The App lock and Privacy sections are now one section, and its note says what CraftyCtrl connects to and, when server alerts are on, that they keep their own Keystore-encrypted copy of your login so they work while the app is locked.
- **Theme choices are the same as in the other Ctrl apps**: Follow system, Dark, Light or **Pure black**. Pure black is now one of the themes instead of a separate switch: if you had Dark with pure black on, you now get the Pure black theme; with "System" and pure black on, CraftyCtrl follows the system with the normal dark colours (pick Pure black again if you want it). "Material You colours" is now called **Use wallpaper colours**.
- **The fingerprint prompts** now say what they're for ("Confirm it's you to manage your servers" when unlocking, "Confirm your fingerprint" when turning fingerprint unlock on), and turning fingerprint unlock on says so if it couldn't be set up.
- **README**: a new App lock section that explains the PIN, fingerprint unlock, the wrong-PIN wait, Lock again, hiding the app in screenshots and recent apps, how server alerts work while locked, and Forgot PIN. The Ctrl series table now lists AdCtrl and MemoCtrl.

## [2.5.1] - 2026-10-21

### Security

- **Trips out of the app no longer skip the lock if you stay away.** The file pickers, links and the battery settings screen still don't count as leaving the app, but if you don't come back within 5 minutes (or your "Lock again" time, if that's longer), CraftyCtrl locks anyway. Before, such a trip never locked, however long you were gone.

## [2.5.0] - 2026-10-21

The app lock now works the same way in every Ctrl app, with the strongest protection of the options we compared.

### Security

- **Your Crafty login is encrypted with your PIN while CraftyCtrl is locked.** The key that decrypts it only exists in memory while the app is unlocked and is wiped the moment it locks, so a locked app, or a copy of its files, can't use your token. The screens underneath the lock keep their place and unsaved edits, but can't reach Crafty until you unlock.
- **The PIN can't be guessed off the phone.** It's never stored. It's stretched with 600,000 rounds of PBKDF2 (up from 60,000) and mixed with a secret held in the phone's secure hardware (StrongBox where available), so copying CraftyCtrl's files to a computer isn't enough to try PINs there.
- **Fingerprint unlock has to unlock that key**, so it can't be faked on a rooted phone. Only strong biometrics count, such as the fingerprint sensor, not face unlock that only uses the camera, which a photo can sometimes fool. The PIN is needed after the phone restarts and at least every 72 hours, like Android's own screen lock. Adding a new fingerprint turns fingerprint unlock off until you turn it on again.
- **Wrong PINs still lock the screen**: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait. Checking your current PIN in Settings (to change it or turn the lock off) counts towards it too.

### Changed

- **One-time update when you first unlock.** Enter your PIN once to move it to the new lock. Fingerprint unlock can't be used for this one unlock, and you have to turn it on again afterwards in **Settings > App lock**.
- **PINs need 6 to 12 digits**, and very obvious ones (like 111111 or 123456) are refused. An older 4- or 5-digit PIN unlocks this one time, then you choose a new one before the app opens.
- **"Lock again" has new choices:** as soon as you leave the app (now the default), after 1, 5 or 15 minutes, after 1 hour, or only when the app restarts. If you had "30 s" or never changed it, it's now "as soon as you leave the app".
- **Picking a file to upload, opening the EULA or a link, and opening battery settings from CraftyCtrl no longer count as leaving the app**, so the lock doesn't ask again when you come back from them.
- **Server alerts with the app lock on**: alerts keep their own copy of your Crafty login (encrypted with the phone's Keystore, as before) so they keep working while the app is locked. Settings says so under Server alerts. Turning alerts off deletes that copy.
- **"Forgot PIN?" asks first, then signs out and forgets everything on the phone**: the login, the panel address and username, the PIN and the fingerprint key. Server alerts are turned off. Your servers on Crafty aren't touched.
- **README**: the app lock line describes the new PIN, fingerprint unlock and encrypted login.

## [2.4.14] - 2026-10-20

### Changed

- **64-bit ARM phones only.** CraftyCtrl now ships only the 64-bit ARM (arm64-v8a) build that nearly every phone from the last several years uses, like every Ctrl app, so the download is smaller. Old 32-bit phones and x86 devices (emulators, some Chromebooks) are no longer supported.
- **README**: Requirements say 64-bit ARM phones only

## [2.4.13] - 2026-10-20

### Privacy
- **Google-free and de-Googled.** CraftyCtrl has no Google Play services, Firebase or other Google services and never contacts Google. This release removes the last mention of Google from inside the app (a note in the app's setup about emoji fonts), so nothing Google-related is left in it. Nothing changes in how the app works: emoji still use your phone's own emoji font, and CraftyCtrl works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

### Changed
- **README**: the "Google-free" line now reads "Google-free and de-Googled" and names the de-Googled phones CraftyCtrl works on.

## [2.4.12] - 2026-10-20

### Privacy
- **No more usage stats: CraftyCtrl is now telemetry-free.** The optional anonymous usage stats are gone completely, along with the Usage stats setting and the one-time question after sign-in. The app no longer contacts VKNXWorks' stats server at all, and the settings it used are deleted from your phone when you update. CraftyCtrl now only talks to your Crafty panel, plus the Mojang player lookup and Crafty's server version catalog when you use those features.

### Changed
- **README**: a Screenshots section (screenshots are coming soon), and a link preview image (`.github/assets/social-preview.png`) with CraftyCtrl's icon, name and summary, for when the repo is shared on Reddit, Discord or elsewhere. It also gets a "Telemetry-free" badge and tag, and a Privacy section listing exactly what CraftyCtrl connects to, in place of the old Usage stats section.

## [2.4.11] - 2026-10-19

### Changed
- **One About section in every Ctrl app**: Support, Report a bug, Source code and Licence (GPL-3.0), with the version underneath, at the bottom of Settings (⋮ → Settings on the Servers screen). The rows are in the same order, with the same wording and icons, as in the other Ctrl apps. The new **Report a bug** row ("Found a problem or have an idea? Open an issue on GitHub") opens CraftyCtrl's issue tracker, the old "Source code and issues" row is now simply **Source code**, and the installed version ("CraftyCtrl 2.4.11 · VKNXWorks") is shown under the section.

## [2.4.10] - 2026-10-19

### New
- **Support CraftyCtrl** under Settings → About opens VKNXWorks' GitHub Sponsors page in your browser. CraftyCtrl is free and open source, and this is the place to sponsor its development if you'd like to. Settings is in the ⋮ menu on the Servers screen.
- **New About section at the bottom of Settings.** Besides **Support CraftyCtrl** it has **Source code and issues**, which opens CraftyCtrl's GitHub page where you can read the code and report bugs, and **Licence**, which opens the GNU General Public License v3.0 that CraftyCtrl is released under.
- The README's **Support the project** section now also points to the **Support CraftyCtrl** button.

## [2.4.9] - 2026-10-19

### Fixed
- **Fingerprint and face unlock really works again.** Since 2.4.1 the lock screen is drawn in a window of its own, so it also covers menus and dialogs that were open when the app locked. From inside that window the lock screen couldn't find the app it belongs to, which the fingerprint prompt has to be attached to, so it quietly treated biometrics as unavailable. It never showed the prompt, and the fingerprint button on the lock screen was missing too, leaving only the PIN pad. The lock screen now finds the app correctly, so the prompt opens when you return to CraftyCtrl and the fingerprint button is back.

### Security
- **Stop, restart, kill, delete, restore and update can't skip the fingerprint check.** If the app couldn't attach the fingerprint prompt to the screen, these actions went ahead without asking. That couldn't happen on the screens where these buttons are today, but they now refuse instead of skipping the check, and they use the same, more robust way of finding the app as the lock screen.

## [2.4.8] - 2026-10-19

CraftyCtrl is now completely Google-free. Nothing changes in how you use it.

### Privacy
- **Emoji no longer make CraftyCtrl ask Google for a font.** A standard Android library inside the app asked Google Play services for a downloadable emoji font every time CraftyCtrl started, which could make Google Play services fetch that font from Google. This lookup is now switched off: emoji are drawn with your phone's own emoji font. On phones without Google Play services nothing changes; on other phones, emoji can look slightly different if the phone's built-in emoji font is older.
- **Checked: CraftyCtrl is Google-free.** It contains no Google Play services, Firebase, ML Kit, Google Cast or Play Core, and it doesn't load fonts or anything else from Google's servers. It also never switches server addresses by itself: it always uses the address you set up, and only you can change it.

### Changed
- **README: new Google-free line** under Requirements: works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font.

## [2.4.7] - 2026-10-19

### Fixed
- **Fingerprint and face unlock works again.** With "Lock again after leaving the app" set to **Immediately**, the app locks while it's going to the background. The fingerprint prompt was requested at that moment, which Android silently ignores, and the app then didn't ask again, so you only got the PIN pad. The prompt now opens only once the lock screen is actually on screen, opens again every time you come back to the app, and opens again if Android closes it (for example when the screen turns off). Tapping **Use PIN** still keeps it closed until you leave and come back, or tap the fingerprint button.

### Changed
- **README brought up to date with CraftyCtrl 2.4.6.** The feature list now matches the app: restart asks for your fingerprint or face too (not only stop and kill), and the list now mentions the broadcast to every running server, the player actions (message, kick, ban, make operator, game mode, clear inventory), the typed confirmations for restoring a backup and deleting a server, alerts that work with the app closed, hiding the app in screenshots and Recents, and the theme options. Getting started now mentions the 2FA code and that a password is never stored, only the token Crafty sends back. Requirements now say the notification permission is only needed for server alerts. No change to the app itself.
- **Changelog: the 2.4.5 heading is back.** It was lost when the 2.4.6 notes were added, so the new app icon looked like part of 2.4.6.

## [2.4.6] - 2026-10-19

A privacy review of the new optional usage stats and of every connection the app makes. The stats are still off unless you said yes.

### Security
- **Switching usage stats off now throws away anything not yet sent.** Before, a stat recorded just before you switched them off could still be waiting in the app, and if you switched stats back on within a few seconds it could have been sent after all. Now everything waiting is discarded the moment you switch stats off, so nothing recorded before that point can ever go out.

### Changed
- **README: every connection the app makes is now listed.** Besides your Crafty panel and the optional stats, CraftyCtrl only goes online for two things you start yourself: adding a player to the whitelist, operators or bans of an online-mode Java server looks the name up at Mojang to get the account's ID, and the **Create server** screen loads the list of server versions from Crafty's download catalog. Neither sends your panel address or login. The README's **Usage stats** section now also mentions the countdown and backup-type details that were already listed in the 2.4.4 notes.

## [2.4.5] - 2026-10-19

### Changed

- **New app icon, matching the rest of the Ctrl series.** The icon is now the same dark keycap as the other Ctrl apps, with a green pixel-art pickaxe in the style of the game, with a darker green handle on its screen. The themed (monochrome) icon on Android 13+ home screens uses the same design.
- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates.

## [2.4.4] - 2026-10-19

### New
- **Optional anonymous usage stats.** After you sign in, CraftyCtrl asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in **Settings → Usage stats**.
- **What's shared:** which screens you open (for example "servers", "server" or "settings", never which server or file), whether you start, stop, restart or kill a server (and whether you used a countdown), opening the console, starting a backup, whether you signed in with an API key or a password, the app version, Android version, phone model, language, phone or tablet, and your theme setting.
- **What's never shared:** your panel address, username, password or API key, server names, player names, console output or commands, file names, or any ID that follows you between sessions. Each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked.
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. Builds you make from source can't send stats at all, and the setting doesn't appear in them.

### Changed
- **PrintCtrl in the Ctrl series list.** The README's table of Ctrl apps now includes [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl), the app for Bambu Lab printers through a self-hosted Bambuddy server, and Spoolman spools.
- The README has a new **Usage stats** section listing exactly what's collected.

## [2.4.3] - 2026-10-18

### Security
- **The lock screen now covers everything.** Before, a dialog, menu or sheet that was open when the app locked (for example a delete confirmation or a rename box) stayed on top of the lock screen and could still be used without the PIN. The lock screen now sits above all of them.
- A wrong-PIN lockout is now checked against the saved lockout itself rather than the countdown on screen, so a PIN can't slip through in the moment before the countdown appears.
- Uploaded file names are cleaned up further: names made only of dots, trailing dots and spaces (which Windows servers drop, turning a name like `.. .` into `..`), colons (drive letters and hidden data streams on Windows) and invisible text-direction characters are removed or replaced.
- Console commands now have every control character removed, including the less common line breaks (vertical tab, form feed and others), so nothing can start a second command or send terminal escape codes.
- File paths are always built from the folder you opened and the item's own name, never taken from the server's reply as-is, so a bad reply can't make rename, delete or the plugin and mod manager act on a file somewhere else.
- New files and folders, renames and uploads only happen inside the server's own folder, and a new name can't contain `/`, `\` or `..`. (To move an item, use Move.)

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
