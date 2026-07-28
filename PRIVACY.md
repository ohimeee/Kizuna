# Privacy Policy

Last updated: 2026-07-29

Kizuna does not have its own servers, does not require an account, and does not collect or
transmit any personal data to its developers. Everything below describes what actually happens on
your device and with the third-party services you choose to connect.

## What stays on your device

- Your library, reading history, downloaded chapters/pages, and app settings are stored locally in
  the app's own database and storage, and are never sent anywhere by Kizuna itself.
- Backups you create are saved to local storage or to a cloud location you pick yourself (e.g. your
  own Google Drive) via Android's standard file/document picker - Kizuna has no access to or
  knowledge of what happens to that file afterward.

## Sources and extensions

Kizuna is a reader, not a content host - it does not host or provide any manga, novel, or chapter
content itself ("hosts zero content"). When you browse or read through a source/extension, your
device communicates directly with that source's own website or API to fetch content; that
communication is between you and the source, governed by the source's own terms, not Kizuna's.

## Trackers (AniList, MyAnimeList, etc.)

Tracker integrations are entirely opt-in. If you log into a tracker, your credentials/session are
used to authenticate directly with that tracker's own service (not a Kizuna server), and reading
progress you choose to sync is sent directly to that tracker. See the tracker's own privacy policy
for how they handle your data.

## Telemetry

Kizuna's official release builds (the APKs published on the
[Releases page](https://github.com/ohimeee/Kizuna/releases)) do **not** include any crash reporting
or analytics SDK - that code path only compiles in for local development builds explicitly opted
into it, and is never enabled in what's published.

## Permissions

Permissions requested at runtime (storage, notifications, install-unknown-apps for extensions,
etc.) are used only for the on-device feature they're named for (saving downloads/backups, showing
library-update notifications, installing source extensions) and are never a channel for sending
data off your device.

## Changes to this policy

If this policy changes, the updated version will be committed to this same file in the
[Kizuna repository](https://github.com/ohimeee/Kizuna), with the "Last updated" date above reflecting
the change.

## Contact

Questions or concerns can be raised via a
[GitHub issue](https://github.com/ohimeee/Kizuna/issues) on this repository.
