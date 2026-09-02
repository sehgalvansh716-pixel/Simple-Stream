<p align="center">
  <img src="Logo.png" alt="SimpleStream Logo" width="130" height="130" style="border-radius: 26px; box-shadow: 0 10px 30px rgba(99, 102, 241, 0.3);">
</p>

<h1 align="center">SimpleStream</h1>

<p align="center">
  <strong>A modern, ultra-fast, and personalized media streaming client for Android and Android TV.</strong>
</p>

<p align="center">
  <a href="https://github.com/sehgalvansh716-pixel/Simple-Stream/releases/latest"><img src="https://img.shields.io/github/v/release/sehgalvansh716-pixel/Simple-Stream?style=for-the-badge&color=6366F1&label=Release" alt="Latest Release"></a>
  <a href="https://github.com/sehgalvansh716-pixel/Simple-Stream/releases"><img src="https://img.shields.io/github/downloads/sehgalvansh716-pixel/Simple-Stream/total?style=for-the-badge&color=10B981&label=Downloads" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV-00C853?style=for-the-badge&logo=android&logoColor=white" alt="Platform Android">
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge" alt="License GPLv3">
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
</p>

---

<p align="center">
  <a href="#-quick-download">🚀 Quick Download</a> •
  <a href="#-key-features">✨ Key Features</a> •
  <a href="#-android-tv--remote-experience">📺 Android TV</a> •
  <a href="#-installation-guide">📦 Installation</a> •
  <a href="#-in-app-auto-updater">🔄 OTA Updates</a> •
  <a href="#-developer--ai-agents">🤖 Developers & AI</a> •
  <a href="#-license--attribution">📜 License</a>
</p>

---

## ⚡ Overview

**SimpleStream** is a lightweight, open-source streaming ecosystem built with modern Kotlin and AndroidX Media3 (ExoPlayer). Designed with an Obsidian Dark aesthetic (`#0B0C10`) and Electric Indigo accents (`#6366F1`), it delivers an ad-free, clutter-free streaming experience across handheld devices, foldables, tablets, and television screens.

> [!NOTE]
> **SimpleStream does not host, scrape, upload, or bundle any media content.** Content indexing and stream retrieval are dynamically driven through modular, user-configured third-party provider plugins (`.cs3` extensions).

---

## 🚀 Quick Download

| Build Tier | Version | Package ID | Download Link |
| :--- | :--- | :--- | :--- |
| **Stable Release** | `1.0.0` | `com.github.sehgalvansh716pixel.simplestream` | [**Download SimpleStream-1.0.0.apk**](https://github.com/sehgalvansh716-pixel/Simple-Stream/releases/download/v1.0.0/SimpleStream-1.0.0.apk) |

---

## ✨ Key Features

### 🎬 High-Performance Media Engine
- **AndroidX Media3 (ExoPlayer)**: Ultra-low latency playback with adaptive bitrate streaming (HLS, DASH, MP4, MKV, WebM).
- **Embedded Torrent Streaming**: Built-in sequential torrent client (TorrServer integration) allowing instant playback while downloading without pre-allocating hours of buffering.
- **Hardware-Accelerated Decoders**: Supports AV1, HEVC/H.265, AVC/H.264, and VP9 video pipelines.

### 🎨 Clean & Modern UI / UX
- **Obsidian Aesthetic**: Zero visual clutter, smooth micro-interactions, tailored dark palette (`#0B0C10` canvas with `#6366F1` accents).
- **Gesture Control Suite**: Intuitive vertical swipes for volume and brightness, horizontal double-taps for seeking, and pinch-to-zoom.
- **Biometric Security**: Protect your private watch history, playlists, and bookmarks using Device Biometrics (Fingerprint / Face Unlock) or TV PIN lock.

### 🧩 Modular Plugin Ecosystem
- **Dynamic Extension Loader**: Install scrapers and content sources on-the-fly from custom repository URLs.
- **Binary Compatibility**: Adheres to the established plugin contract in `:library`, allowing full compatibility with community extensions.
- **Auto-Syncing Providers**: Keep all installed plugins updated automatically via background workers.

### 💬 Advanced Subtitles & Audio
- **Multi-Track Switching**: Instant selection between multiple audio languages and surround sound tracks.
- **On-the-Fly Subtitle Search**: Automatically fetch subtitles via OpenSubtitles and local `.srt`/`.vtt` file importers.
- **Customizable Styling**: Tune subtitle font sizes, margins, background opacity, stroke shadows, and timing offsets.

### 📥 Robust Offline Downloads
- **Parallel Multi-Segment Downloader**: Turbocharge download speeds with concurrent chunk streaming.
- **Background Persistence**: Handles network disruptions with automatic retries and pause/resume support.

---

## 📺 Android TV & Remote Experience

SimpleStream offers first-class support for the 10-foot television interface:
- **D-Pad Navigation**: Fully optimized layout for TV remotes, gamepads, and directional arrow controllers.
- **Leanback Launcher Integration**: Live channels and "Next Up" episode recommendations on your Android TV / Google TV home screen.
- **Direct Playback Shortcuts**: Resume your favorite series right from your TV dashboard.

---

## 📦 Installation Guide

### Android Phones & Tablets
1. Download **`SimpleStream-1.0.0.apk`** from the [Latest Release](https://github.com/sehgalvansh716-pixel/Simple-Stream/releases/latest).
2. Tap the downloaded `.apk` file. If prompted, enable **"Allow from this source"** in your device settings.
3. Tap **Install** and launch SimpleStream from your app drawer.

### Android TV & Fire TV Devices
1. Install **Downloader by AFTVnews** from the Google Play Store or Amazon Appstore.
2. In Downloader, enter the direct release URL:
   ```
   https://github.com/sehgalvansh716-pixel/Simple-Stream/releases/download/v1.0.0/SimpleStream-1.0.0.apk
   ```
3. Once downloaded, select **Install** and start streaming!

---

## 🔄 In-App Auto-Updater

SimpleStream features a built-in Over-The-Air (OTA) updater directly linked to this repository:
- **Instant Alerts**: Automatically notifies you when a new release is published to [sehgalvansh716-pixel/Simple-Stream](https://github.com/sehgalvansh716-pixel/Simple-Stream).
- **Manual Verification**: Navigate to **Settings** → **Updates and Backup** → **Check for Update** at any time.

---

## 🛠️ Building From Source

### Prerequisites
- **JDK 17** or higher
- **Android SDK** (API 34 / Build-Tools 34.0.0)
- **PowerShell** or Unix Shell

### Compile Debug APK
```bash
# Clone the repository
git clone https://github.com/sehgalvansh716-pixel/Simple-Stream.git
cd Simple-Stream

# Compile stable debug APK
./gradlew assembleStableDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/stable/debug/app-stable-debug.apk`

---

## 🤖 Developers & AI Agents

If you are an AI coding assistant (or human contributor) working on SimpleStream:
- **Mandatory Manual**: Please read [AGENTS.md](AGENTS.md) before making any code modifications.
- **Critical Invariant**: Never modify internal class package names (`com.lagradost.cloudstream3`); binary compatibility with community extensions depends on this contract.
- **Version Roadmap**: SimpleStream `1.0.0` is the baseline milestone. See [ROADMAP.md](docs/ROADMAP.md) for planned future features.

---

## 📜 License & Attribution

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

```
SimpleStream — A personalized fork of CloudStream 3 (recloudstream/cloudstream).
Copyright (C) 2026 SimpleStream Contributors
Modifications & Rebranding by sehgalvansh716-pixel.
```

- Upstream Project: [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream)
- All original copyright notices, licenses, and attributions are faithfully preserved in compliance with the GPLv3 license.
- Full license text is available in the [LICENSE](LICENSE) file.
