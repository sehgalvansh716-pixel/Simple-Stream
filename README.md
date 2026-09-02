# SimpleStream

> A sleek, minimalist, and personalized streaming media center for Android & Android TV.

**⚠️ Notice:** *SimpleStream does not host, upload, or bundle any media content. All functionality is powered dynamically through modular community-developed extensions.*

---

## Attribution & License Notice

> **This project is a personalized fork of [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream), originally licensed under the GNU General Public License v3.0 (GPLv3). Modifications and rebranding by [sehgalvansh716-pixel](https://github.com/sehgalvansh716-pixel).**
>
> All original copyright notices and the GPLv3 license terms are fully preserved in accordance with the original software license.

---

## Features

SimpleStream brings a refined, high-performance streaming experience with complete user freedom:

- **100% Ad-Free & Privacy-Centric:** No advertisements, no telemetry, no tracking SDKs.
- **Universal Multi-Source Search:** Query across multiple video providers simultaneously in real time.
- **Modular Plugin/Extension Engine:** Add, update, and manage third-party scrapers and media sources without rebuilding the app.
- **Advanced In-App Media Player:**
  - Gesture controls for volume, brightness, and fast scrubbing.
  - Multi-speed playback (0.25x to 3.0x).
  - Audio track switching and audio-sync offset correction.
  - Aspect ratio adjustments (Fit, Zoom, Stretch, 16:9, 4:3).
- **Automated Subtitles:** Load embedded stream subtitles or download on the fly from OpenSubtitles with custom typography, colors, and timing sync.
- **Anime Quality-of-Life:** Automatic intro, outro, recap skip (powered by AniSkip), and episode filler filtering.
- **Robust Background Downloader:** Multi-threaded parallel downloading, pause/resume support, and offline playback.
- **Watch Progress & Library Management:** Automatically syncs watch position, continue watching row, and categorized lists ("Watching", "Plan to Watch", "Completed", etc.).
- **Big Screen Ready:** First-class support for Android TV, Google TV, and game controllers with remote-optimized navigation.
- **Casting Support:** Full Google Cast (Chromecast) integration and external player launcher (VLC, MPV, Next Player).
- **Security Lock:** Lock the app with device biometric authentication (Fingerprint / Face Unlock) or TV PIN lock.

---

## Screenshots

<!-- App Screenshots Placeholder -->
*Screenshots coming soon.*

---

## Getting Started

### Installation
1. Download the latest debug APK from the project builds or Releases.
2. Install the APK on your Android device (Android 7.0+ supported).
3. Open **Settings** → **Extensions** to add community plugin repositories.

### Extension Development
SimpleStream uses the standardized plugin API (`:library` module). For instructions on creating new extensions, see the [Extension Developer Guide](https://recloudstream.github.io/csdocs/devs/gettingstarted/) or scaffold using the official [TestPlugins](https://github.com/recloudstream/TestPlugins) repository.

---

## Documentation
- For a comprehensive non-technical breakdown of the app architecture and feature set, see [APP_OVERVIEW.md](file:///c:/SimpleStream/docs/APP_OVERVIEW.md).
- For planned upcoming phases, see [ROADMAP.md](file:///c:/SimpleStream/docs/ROADMAP.md).

---

## License
SimpleStream is licensed under the [GNU General Public License v3.0](LICENSE).
