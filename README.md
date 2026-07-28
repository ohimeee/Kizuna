<div align="center">

<a href="https://github.com/ohimeee/Kizuna">
    <img src="./.github/assets/kizuna-logo.png" alt="Kizuna logo" title="Kizuna logo" width="80"/>
</a>

# Kizuna [App](https://github.com/ohimeee/Kizuna/releases/latest)

### Manga/manhwa/manhua + light novel reader
Discover and read manga, webtoons, comics, and light novels — in one unified library — on your Android device.

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-0877d2.svg)](/LICENSE)

## Download

[![Kizuna Stable](https://img.shields.io/github/release/ohimeee/Kizuna.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://github.com/ohimeee/Kizuna/releases/latest)

*Requires Android 8.0 or higher.*

Kizuna is distributed as a direct APK download only (not on the Google Play Store).

## About

Kizuna (絆, "bond/connection") is a fork of [Mihon](https://github.com/mihonapp/mihon) that adds
native light-novel reading alongside Mihon's existing image-based (manga/manhwa/manhua) reading,
in a single unified app. The two content types share one library, backup/restore, tracker sync,
and download manager, and can be cross-linked so a comic and its novel counterpart point to each
other for quick navigation.

Kizuna keeps Mihon's library management, backup/restore, tracker sync (AniList/MAL/etc.),
download manager, and update-checking system as-is, since these are content-type-agnostic.
The novel-reading source format and JS extension engine follow patterns established by
[LNReader](https://github.com/LNReader/lnreader). See [NOTICE](./NOTICE) for full attribution.

## Features

<div align="left">

* Local reading of content.
* A configurable reader with multiple viewers, reading directions and other settings (image content).
* A native text reader with font size, font family (including bundled Lora and Nunito), theme, bionic reading, and line-spacing controls (novel content).
* Cross-linking between a comic and its novel adaptation, with independently tracked progress per title.
* Tracker support: [MangaBaka](https://mangabaka.org), [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), [Bangumi](https://bgm.tv/), and [Hikka](https://hikka.io/) support.
* Categories to organize your library.
* Light and dark themes.
* Schedule updating your library for new chapters.
* Create backups locally to read offline or to your desired cloud service.
* Plus much more...

</div>

## Contributing

[Code of conduct](./CODE_OF_CONDUCT.md) · [Contributing guide](./CONTRIBUTING.md)

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

### Credits

Kizuna builds on the work of the [Mihon](https://github.com/mihonapp/mihon) contributors (itself a
fork of [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi)) and incorporates ideas and patterns
from [LNReader](https://github.com/LNReader/lnreader). Thank you to all contributors of both
upstream projects.

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

### License

<pre>
Copyright © 2026 Kizuna Contributors
Copyright © 2024 Mihon Open Source Project
Copyright © 2015 Javier Tomás

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>

Portions of Kizuna's novel-reading functionality follow patterns from LNReader, licensed under the
MIT License. See [NOTICE](./NOTICE) for details.

</div>
