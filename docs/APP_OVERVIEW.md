# SimpleStream (formerly CloudStream) — Application Overview

This document provides a clear, plain-English overview of the application's architecture, features, and extension system. It is written specifically for non-technical readers and project owners to understand what the app does, how it is organized, and how each component works under the hood without confusing programming jargon.

---

## 1. What the App Does

**SimpleStream** is a free, modular, and privacy-focused media streaming center for Android phones, tablets, and Android TV devices. The application itself does not host, distribute, or bundle any media content; instead, it acts as an intelligent universal media player and browser. By installing community-made or self-hosted "extensions" (plugins), users can search, browse, stream, and download movies, television series, anime, audiobooks, and live IPTV streams from their preferred sources. The app features zero advertisements, zero tracking or user telemetry, automated subtitle fetching, resume-watching capabilities, Chromecast casting, and an interface optimized for both touchscreens and television remotes.

---

## 2. How the App is Structured (The "House" Analogy)

Think of SimpleStream like a modern, fully-equipped home. Rather than looking at technical files and code directories, here is how each part of the project maps to rooms in a house:

```
SimpleStream Project
 ├── The Construction Blueprints (Root & Gradle files)
 ├── The Foundation & Universal Outlets (`/library`)
 ├── The Living Room & Display (`/app/src/main/res` & `/ui`)
 ├── The Workshop & Engine Room (`/app/src/main/java/.../services` & `/utils`)
 ├── The Delivery Bay & Tool Shed (`/app/src/main/java/.../plugins`)
 └── The Front Gate & Security (`AndroidManifest.xml`)
```

### 1. The Foundation & Universal Wall Outlets (`/library`)
Before you can plug in lamps or kitchen appliances, your house needs standard electrical wiring and standard wall outlets. The `/library` module is this foundation. It contains the universal contract and rules that every plugin must connect to. Because this foundation is fixed and standardized, any extension created by anyone in the community can plug into SimpleStream seamlessly.

### 2. The Living Room & Display (`/app/src/main/res/` and `/app/src/main/java/.../ui/`)
This is the furnished space where the user actually sits, looks around, and interacts:
- **The Walls and Paint (`/res/values/colors.xml`, `styles.xml`)**: The visual theme, colors, dark mode background, and typography.
- **The Furniture Layouts (`/res/layout/`)**: How posters, search bars, episode lists, and player buttons are arranged on screen.
- **The Entertainment Center (`/ui/player/`)**: The on-screen video player with play/pause buttons, scrubbing seek-bar, audio/subtitle selectors, and video quality pickers.
- **The Bookshelves (`/ui/library/`, `/ui/home/`)**: The home screen carousel, watch-history cards, bookmarked favorites, and category collections.

### 3. The Workshop & Engine Room (`/app/src/main/java/.../services/` and `.../utils/`)
Behind the walls in the basement, heavy machinery runs silently:
- **The Plumbing & Water Pumps (`nicehttp`, networking)**: Makes secure web connections to fetch data, movie covers, and video streams.
- **The Storage Locker (`/downloader/`)**: Manages downloading episodes to internal storage in the background while prioritizing your current downloads.
- **The Translator (`/subtitles/`)**: Downloads subtitle files, detects character encodings (Arabic, Japanese, European, etc.), and renders clean text onto the screen.

### 4. The Delivery Bay & Tool Shed (`/app/src/main/java/.../plugins/`)
This is where packages and new tools arrive from the outside world:
- **The Mailbox (`RepositoryManager`)**: Checks repository links to see if new extensions or updates are available online.
- **The Inspector (`PluginManager`)**: Receives downloaded extension packages (`.cs3` files), verifies their safety and version, unzips them, and plugs them into the app so their sources appear instantly in your search and home screen.

### 5. The Front Gate & House Rules (`AndroidManifest.xml`)
This is the official registration with Android OS. It declares the app name, app icon, what permissions the app needs (Internet access, background downloading, storage access, notifications), and tells Android how to open the app when someone clicks a media link.

### 6. The Construction Blueprints (`build.gradle.kts`, `settings.gradle.kts`)
These are the build instructions used by the computer to assemble all the raw code, graphics, and external open-source libraries into the final installable Android APK file.

---

## 3. Core Features List

Every major user-facing feature in SimpleStream is built for speed, privacy, and convenience:

1. **Ad-Free Universal Search & Browse**
   - Search across dozens of streaming websites simultaneously with a single query.
   - View detailed media pages: synopsis, release year, IMDb ratings, duration, director, cast members, and high-definition artwork.
2. **Multi-Category Home Screen**
   - Dynamic home sections (Trending, Popular, Top Rated, Latest Releases) powered directly by your active extensions.
3. **Advanced Built-In Video Player**
   - Intuitive touch gestures: slide up/down on the left side for brightness, slide up/down on the right side for volume, slide horizontally to scrub forward/backward.
   - Aspect ratio control: Fit, Crop, Stretch, 16:9, or 4:3.
   - Playback speed control (from 0.25x up to 3.0x).
   - Audio track selector, audio sync offset slider, and pitch adjustment.
4. **Smart Subtitles**
   - Load embedded video subtitles, provider subtitles, or fetch subtitles automatically from OpenSubtitles.
   - Full subtitle customization: change font size, font family, text color, outline, background opacity, and timing synchronization.
5. **Anime Enhancements (Auto-Skip & Fillers)**
   - Integration with AniSkip and AnimeSkip to automatically detect and skip opening themes, recaps, and ending credits.
   - Highlight and filter filler episodes automatically using community databases.
6. **Background Download Manager**
   - Download full movies or whole seasons for offline viewing.
   - Multi-threaded parallel downloading, customizable concurrent connections, automatic pause/resume, and notification progress updates.
7. **Watch History & Bookmarks**
   - Automatically remembers your exact playback position for every video.
   - Organized lists: "Watching", "Plan to Watch", "Completed", "On Hold", "Dropped", and custom user tags.
8. **Multi-User Profiles**
   - Switch between different user profiles on the same device with separate history, bookmarks, and settings.
9. **Android TV & Gamepad Optimization**
   - Fully navigable using standard TV remotes (D-Pad navigation) or game controllers.
   - TV leanback launcher banner and dedicated 10-foot user interface.
10. **Casting & External Players**
    - Seamless Google Cast (Chromecast) integration to stream videos to TVs and smart displays.
    - One-tap handoff to external player apps (such as VLC, MPV, Next Player, or MX Player) if desired.
11. **Privacy & Security Lock**
    - Protect the application with Biometric Authentication (Fingerprint / Face Unlock) or TV PIN lock.
    - Zero user tracking, telemetry, or third-party advertising SDKs.

---

## 4. How the Plugin & Extension System Works

SimpleStream’s most powerful capability is its plug-and-play architecture. The app itself contains no pre-bundled streaming links or scraping code. Everything comes through **plugins** (also called **extensions**).

### A. What is an Extension?
An extension is a tiny, self-contained file (with the `.cs3` extension) that contains compiled code and instructions for how to read a specific website or API. Think of it like a game cartridge that you slide into a gaming console.

### B. What Does an Extension Contain?
Every extension package contains two key components:
1. **The Manifest (`manifest.json`)**: A small label identifying the plugin:
   - Plugin display name (e.g., *"AwesomeStreams"*)
   - Version number (e.g., `1`)
   - The main entry point class
   - Whether it needs bundled graphics/resources
2. **The Provider Class (`MainAPI`)**: The code that tells SimpleStream how to interact with the target site:
   - **`mainUrl`**: The website's address.
   - **`getMainPage()`**: How to fetch the front page categories and posters.
   - **`search(query)`**: How to type a query into the site and turn search results into movie posters.
   - **`load(url)`**: How to open a specific movie/show page and list its seasons, episodes, and plot summary.
   - **`loadLinks(...)`**: How to get the actual playable video stream link (e.g., `.m3u8` or `.mp4`) when the user taps "Play".
3. **The Extractors (`ExtractorApi`)**: Many streaming sites embed video players hosted on third-party servers (like Streamtape, Filemoon, or Vidcloud). Extractors are small reusable modules that know how to bypass video player embeds and pull the raw video stream.

### C. How the App Discovers & Loads Extensions
1. **Repository Links**: A repository is simply a link to a `plugins.json` file hosted on GitHub or any web server. The user enters or taps a repository URL in Settings.
2. **Download & Safety**: SimpleStream downloads the `.cs3` plugin files, calculates their SHA-256 security checksum, and stores them in the app's private sandboxed directory.
3. **Dynamic Loading (`PathClassLoader`)**: At runtime, Android's `PathClassLoader` reads the `.cs3` package without needing to reinstall the app.
4. **Registration**: SimpleStream reads the manifest, initializes the provider, and registers it in the app's search engine and home screen. The new sources are instantly ready to watch!

---

## 5. How Each Major Feature Could Be Recreated from Scratch

If you ever wanted to rebuild any part of SimpleStream from scratch in the future, this section explains conceptually (in plain terms) what is involved:

| Feature | What It Does Conceptually | What You Would Need to Build It |
| :--- | :--- | :--- |
| **Video Player** | Plays video streams smoothly on screen with audio controls, brightness gestures, and subtitle overlay. | Use Google's standard Android media engine (**Media3 / ExoPlayer**). Add gesture detection listeners on top of the video view to adjust brightness and volume, and layer a custom subtitle render view. |
| **Plugin / Extension Engine** | Allows users to install new streaming scrapers without updating the main app. | Use Java/Android's dynamic class loader (**`PathClassLoader`**). Define a strict interface library (like our `/library` module). Extensions are compiled into `.dex` code inside `.zip` files, and loaded into memory on demand. |
| **Website Scraper (Providers)** | Reads HTML web pages, finds movie posters, links, and episode lists. | Use an HTTP client library (**OkHttp** or **Ktor**) to request web pages, and an HTML parser (**Jsoup**) with CSS queries (like finding `div.movie-card`) to turn web text into clean data objects. |
| **Download Manager** | Downloads video chunks and files to phone storage in the background even if the app is closed. | Use Android's **`WorkManager`** and a **Foreground Service** with persistent notifications. The downloader streams bytes from the web server into a file on disk, resuming automatically if internet cuts out. |
| **Watch History & Bookmarks** | Remembers how much of an episode you watched, what shows you love, and personal ratings. | Use a local embedded database (such as **Room / SQLite**) or a structured JSON preferences store. Whenever a video pauses or ends, save the current millisecond timestamp and duration. |
| **Android TV Interface** | Adapts the UI for big screens with 10-foot viewing distance and arrow-key (remote) navigation. | Use Android's **Leanback framework** or standard Android layout with explicit focusability (`nextFocusDown`, `nextFocusUp`). Replace phone tab bars with a side navigation rail or top header. |
| **App Lock & Security** | Asks for fingerprint or PIN before showing the user's media. | Use the Android **BiometricPrompt API**. When the app resumes from background, show an overlay requesting authentication before showing private history or bookmarks. |

---

## Summary

SimpleStream is built with a rock-solid, decoupled design:
- The **App Shell** handles visuals, gestures, storage, and playback.
- The **Extension Core** is an open, standardized bridge that allows any source to be added without modifying the core player.

This clean separation ensures high stability, effortless maintenance, and long-term durability.
