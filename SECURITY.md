# Security policy

Thank you for helping keep CraftyCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/CraftyCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of CraftyCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your Crafty API key, login token, password or other data leaking to anyone other than your own Crafty Controller panel
- CraftyCtrl connecting over plain HTTP when you didn't accept it, or accepting an HTTPS certificate Android doesn't trust
- Ways around the app lock, or protected screens (sign-in, lock and PIN screens, the file editor, server settings) showing up in screenshots when they shouldn't
- Another app on the phone, or a push sent to your push address, being able to make CraftyCtrl do something you didn't ask for, such as stopping a server or running a console command
- A file name or reply from the panel making CraftyCtrl read, write or delete files outside the server's folder
- Anything that lets someone run code or read files they shouldn't

Problems in Crafty Controller itself should go to that project.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
AD:45:50:1C:0C:C0:F7:E9:CA:68:F7:A7:71:99:49:96:40:7E:50:DE:B8:5A:2A:B8:59:20:8F:DA:D3:60:CB:57
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
