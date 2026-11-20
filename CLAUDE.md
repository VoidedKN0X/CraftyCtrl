# CraftyCtrl

## Dependency verification

Gradle checks every downloaded library and plugin against the SHA-256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or Gradle version changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/CraftyCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (between the BEGIN/END markers, `base64 -d | gunzip`), check the diff (only Maven Central, Google's Maven and the Gradle plugin portal) and commit it in the same push as the change. Never turn verification off or make it lenient.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "CraftyCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.

## Encryption at rest

- The per-server snapshots in the `alert_state` SharedPreferences (`monitor/Checker`: running, crashed, player count and player names) are sealed with AES-256-GCM (`data/crypto/SealedText`: `"CRAFTYCTRL-REST1:"` + Base64 of a `SealedFile` blob) under the data key (`DataAtRest` / `DataKeyStore`: 32 random bytes in `filesDir/data.key`, wrapped by the Keystore key `craftyctrl_data_at_rest` through `KeystoreWrapper`/`KeystoreAes`; no user authentication, so the monitor service, the check job and push wake-ups work while locked).
- Readers must accept both sealed and ordinary values (`SealedText.open` returns ordinary values as they are). Without a data key (`DataAtRest.key` returns null) snapshots are read and written the ordinary way. A sealed snapshot that can't be opened is a cache miss (the server counts as "never seen"), not an error.
- `DataAtRest.key` asks the Keystore the first time in a run: only call it off the main thread (`Checker.check` does it on `Dispatchers.IO`).
- A sealed value differs every time it's made (random IV). Write one only when the snapshot changed or the stored copy isn't sealed yet, or every check rewrites the settings file.
- Never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`). The token key (`crafty_remote_token_key`) and the app-lock vault are separate and must not be touched by this.
- Not sealed on purpose: `crafty_session` (address and user name are read synchronously at start, also while locked), `cleartext_policy`, `app_settings`, `push`, `unifiedpush` and the `down_alerted:` times in `alert_state`.
