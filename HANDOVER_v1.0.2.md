# SimpleStream v1.0.2 Developer & AI Handover Manual

> **MANDATORY READING FOR ANY AI AGENT OR DEVELOPER WORKING ON THIS PROJECT**  
> SimpleStream is maintained by an owner with **zero programming knowledge**. You must operate with 100% autonomy, make sound architectural decisions, strictly adhere to the technical invariants, and never break existing functionality.  
> **Production Baseline**: `v1.0.1` (`versionCode = 102`, Git Tag `v1.0.1` on `master`)  
> **Active Development Sandbox**: `Simple Stream 1.0.2` (`versionCode = 103`, `versionName = "1.0.2"`)  
> **Testing Protocol**: The project owner tests APKs directly on their personal physical devices. **DO NOT** run emulators, ADB loops, or screencap captures unless explicitly instructed.  
> **Workspace Hygiene**: Keep `c:\SimpleStream\` clean at all times. Do NOT leave screenshots, test files, or temporary logs in the project root.

---

## 1. Repository Topography & Current State

The workspace root at `c:\SimpleStream\` contains:

```
c:\SimpleStream\
├── Simple-Stream\                     # 🌟 PRODUCTION GIT REPOSITORY (Clean master branch, tagged v1.0.1)
│   ├── .git\                          # Git history and origin tracking
│   ├── .github\workflows\build.yml   # GitHub Actions CI
│   ├── app\                           # Android app module (com.lagradost.cloudstream3)
│   ├── library\                       # Kotlin Multiplatform provider / extension engine
│   └── build.gradle.kts, etc.
│
├── Simple Stream 1.0.2\               # 🧪 ACTIVE DEVELOPMENT SANDBOX (v1.0.2 development, versionCode = 103)
│   ├── (NO .git / NO .github)         # COMPLETELY DETACHED FROM GIT to prevent accidental commits
│   ├── app\                           # Android app module with v1.0.2 work
│   └── library\
│
├── Simple-Stream 1.0.1\               # 📦 LIGHTWEIGHT SOURCE BACKUP OF v1.0.1 (~14 MB, no build caches)
├── NetMirror_Local\                   # 🔌 LOCAL PROVIDER EXTENSION (NetMirror source & NetMirror.cs3)
├── SimpleStream-1.0.0.apk             # Compiled v1.0.0 release APK
├── SimpleStream-1.0.1.apk             # Baseline compiled v1.0.1 release APK (versionCode = 102, 72.7 MB)
├── Logo.png                           # Source high-res square brand artwork
├── Tv Logo.png                        # Source high-res Android TV launcher banner (16:9)
├── AGENTS.md                          # Permanent AI directive manual
└── HANDOVER_v1.0.2.md                 # THIS FILE (Comprehensive technical handover)
```

---

## 2. The Sandbox Development Workflow (v1.0.2)

To ensure zero downtime, prevent broken git states, and protect the production GitHub release:
- **Sandbox Rule**: All ongoing development, new features, and bug fixes for `v1.0.2` take place inside:
  `c:\SimpleStream\Simple Stream 1.0.2\`
- **Git Disconnection**: The sandbox directory has NO `.git` directory. Do NOT initialize git inside `Simple Stream 1.0.2`.
- **Testing Cycle**: 
  1. Make edits in `Simple Stream 1.0.2/`.
  2. Build release APK with:
     ```powershell
     .\gradlew.bat assembleStableRelease
     ```
  3. Copy APK to root:
     ```powershell
     Copy-Item -Path "app\build\outputs\apk\stable\release\app-stable-release.apk" -Destination "c:\SimpleStream\SimpleStream-1.0.2.apk" -Force
     ```
  4. The owner tests on physical mobile and TV devices.
  5. Once the owner approves, changes will be migrated to `c:\SimpleStream\Simple-Stream\`, committed, tagged `v1.0.2`, and pushed to GitHub.

---

## 3. What Was Delivered in SimpleStream v1.0.1

### A. Android TV Leanback Banner & 10-Foot Experience
- **16:9 Leanback Launcher Banner**: Replaced all vector banners with high-resolution bitmap PNGs (`@drawable/ic_banner` in `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`, `mipmap-xhdpi`) derived from `Tv Logo.png`.
- **Manifest Declarations**: Both `<application>` and `<activity android:name=".ui.account.AccountSelectActivity">` specify `android:banner="@drawable/ic_banner"` and `android:logo="@drawable/ic_banner"`.
- **D-Pad Navigation Stability**: Restored stable baseline Android focus handling across `MainActivity.kt` and `SettingsFragment.kt` without focus traps.
- **TV Initial Setup Wizard**: Full D-pad support for initial language, layout, media, and provider language setup screens on TV screens.

### B. Dynamic Content Card Orientation
- **Aspect Ratio Auto-Adaptation**: Home cards automatically detect landscape vs portrait media thumbnails via Coil 3 `onSuccess` callback.
- Landscape items (e.g. YouTube feeds, backdrops) expand smoothly to 16:9 width (~180dp), eliminating awkward cropping and letterboxing.
- Cached in thread-safe `ConcurrentHashMap` in `HomeChildItemAdapter.kt`.

### C. YouTube-Style 2X Speed Gesture
- Long-pressing the screen while player HUD controls are hidden temporarily boosts playback speed to `2.0x`.
- Features an ultra-compact frosted glass HUD pill (`2x ▶▶`) styled in `glass_speedup_pill.xml`.
- Integrated haptic feedback on speedup engagement and finger release.

### D. Netflix-Style Glassmorphic Season & Episode Drawer
- In-player episode drawer sliding smoothly in from the right edge with a frosted Obsidian panel (`bg_player_drawer_glass.xml`).
- Interactive frosted glass season dropdown pill (`glass_season_pill.xml`) allowing instant season filtering with episode counts.
- Auto-scrolls directly to the currently playing episode.

### E. Pre-packaged NetMirror Extension via `1908` Secret Unlock
- **Direct Kotlin Integration**: Embedded natively in [InternalStreamBridge.kt](file:///c:/SimpleStream/Simple%20Stream%201.0.2/app/src/main/java/com/lagradost/cloudstream3/utils/InternalStreamBridge.kt) to bypass Android 14+ dynamic dex loading restrictions.
- **Camouflage Naming**: Classes named `InternalStreamBridge`, `InternalStreamCommon`, `InternalStreamBase`, `InternalStreamA`, `InternalStreamB`, `InternalStreamC`.
- **UI Titles**: Clear user-facing names (`NetMirror - Netflix`, `NetMirror - Prime Video`, `NetMirror - Disney+ Hotstar`).
- **Base64 String Concealment**: All scraper endpoints, CDN domains, referers, API keys, and failovers are Base64 encoded in `InternalStreamCommon`.
- **No "Invalid URL" Popup**: Early return at line 1 of the dialog submit handler in `ExtensionsFragment.kt` and `AppContextUtils.kt` when `1908` is typed.
- **App Restart Persistence**: Unlock state stored via `setKey("internal_stream_bridge_unlocked", true)`. `MainActivity.onCreate()` calls `InternalStreamBridge.init(this@MainActivity)` synchronously on every launch, ensuring providers are immediately restored.

---

## 4. Critical Invariants (NEVER VIOLATE)

1. **🔴 NEVER rename internal package `com.lagradost.cloudstream3`**:
   - All external `.cs3` plugins and repository extensions depend on this exact package hierarchy. Renaming it will break binary compatibility with every provider.
   - User-facing application ID remains `com.github.sehgalvansh716pixel.simplestream`.
2. **🔴 Launcher Activity is `AccountSelectActivity`**:
   - `MainActivity` is not exported (`android:exported="false"`). Launching via ADB must target `com.lagradost.cloudstream3.ui.account.AccountSelectActivity`.
3. **🔴 TV Banner Must Be Bitmap (`@drawable/ic_banner`)**:
   - Never reintroduce vector XMLs for `ic_banner`. Android TV Leanback launchers require standard 16:9 bitmap drawables.
4. **🔴 Visual Brand Consistency**:
   - Electric Indigo: `#6366F1`
   - Dark Obsidian: `#0B0C10`, `#121212`, `#1F2833`
5. **🔴 Secret Unlock 1908 & Early Return**:
   - Always intercept `1908` before regex validation to prevent "Invalid URL" dialogs.
   - Keep `InternalStreamBridge` registered on startup via `setKey`/`getKey`.
6. **🔴 Standalone APK Signing**:
   - Release builds use debug signing so APKs can be sideloaded directly onto devices without keystore passwords.
7. **🔴 No Clutter in Project Root**:
   - Keep `c:\SimpleStream\` clean. Never leave temporary screenshot dumps, logs, or test artifacts in the root directory.

---

## 5. Standard Guide for Working on v1.0.2

When tasked with new features or fixes for `v1.0.2`:
1. Work exclusively inside `c:\SimpleStream\Simple Stream 1.0.2\`.
2. Ensure `versionCode = 103` and `versionName = "1.0.2"` in `app/build.gradle.kts`.
3. Compile release APK for user testing:
   ```powershell
   cd "c:\SimpleStream\Simple Stream 1.0.2"
   .\gradlew.bat assembleStableRelease
   Copy-Item -Path "app\build\outputs\apk\stable\release\app-stable-release.apk" -Destination "c:\SimpleStream\SimpleStream-1.0.2.apk" -Force
   ```
4. Ask user to test `SimpleStream-1.0.2.apk` on physical hardware.
5. Upon user approval, copy changed files to `Simple-Stream`, commit, tag `v1.0.2`, and push to GitHub.
