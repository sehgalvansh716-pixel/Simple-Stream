# AGENTS.md — Developer & AI Agent Context Manual for SimpleStream

> **CRITICAL DIRECTIVE FOR ALL AI AGENTS**:  
> Read this entire document before inspecting or modifying any code.  
> **SimpleStream 1.0.1 is the official production baseline**, located on `master` in the primary git repository `c:\SimpleStream\Simple-Stream` (tagged `v1.0.1`).  
> **SimpleStream 1.0.2 is currently in active sandbox development** under `c:\SimpleStream\Simple Stream 1.0.2` with `versionCode = 103`.  
> The project owner has **zero programming knowledge**. You must make all technical decisions autonomously, never delegate code tasks, and ensure full verification before completing any task.  
> **Testing Protocol**: The project owner prefers to test APKs themselves on physical devices. **DO NOT** run emulators, ADB background loops, or screencap captures unless explicitly asked.  
> **Workspace Hygiene**: Keep the workspace root (`c:\SimpleStream\`) pristine. Never leave loose screenshots, temporary logs, or scratch files in the root folder.

---

## 1. Project Overview & Origins

- **Application Name**: SimpleStream
- **Baseline Production Version**: `1.0.1` (`versionCode = 102`, Git Tag: `v1.0.1`)
- **Active Development Version**: `1.0.2` (`versionCode = 103`)
- **Package ID**: `com.github.sehgalvansh716pixel.simplestream` (Debug build appends `.debug`: `com.github.sehgalvansh716pixel.simplestream.debug`)
- **GitHub Repository**: [https://github.com/sehgalvansh716-pixel/Simple-Stream](https://github.com/sehgalvansh716-pixel/Simple-Stream)
- **GitHub Owner**: `sehgalvansh716-pixel`
- **License**: GNU General Public License v3.0 (GPLv3).
- **Lineage**: SimpleStream is a personalized, rebranded open-source streaming client for Android and Android TV, originally forked from CloudStream 3 (`recloudstream/cloudstream`).

---

## 2. Core Architecture & Project Topography

SimpleStream is organized as follows:

```
c:\SimpleStream\
├── Simple-Stream\                     # 🌟 PRODUCTION GIT REPOSITORY (Clean master branch, tagged v1.0.1)
│   ├── .git\                          # Git history and origin tracking
│   ├── app\                           # Android app module (v1.0.1 release)
│   ├── library\                       # Kotlin Multiplatform provider / extension engine
│   └── build.gradle.kts, etc.
│
├── Simple Stream 1.0.2\               # 🧪 ACTIVE DEVELOPMENT SANDBOX (v1.0.2 development, versionCode = 103)
│   ├── (NO .git / NO .github)         # COMPLETELY DETACHED FROM GIT to prevent accidental commits
│   ├── app\                           # Android app module with v1.0.2 work
│   └── library\
│
├── Simple-Stream 1.0.1\               # 📦 SOURCE BACKUP OF v1.0.1 (Stripped of build caches, ~14 MB)
├── NetMirror_Local\                   # 🔌 LOCAL PROVIDER EXTENSION (NetMirror source & NetMirror.cs3)
├── SimpleStream-1.0.0.apk             # Baseline compiled v1.0.0 release APK
├── SimpleStream-1.0.1.apk             # Baseline compiled v1.0.1 release APK (versionCode = 102, 72.7 MB)
├── Logo.png                           # Source high-res brand artwork (square)
├── Tv Logo.png                        # Source high-res Android TV launcher banner (16:9)
├── AGENTS.md                          # THIS DIRECTIVE FILE
└── HANDOVER_v1.0.2.md                 # Complete technical handover document for v1.0.2
```

---

## 3. The Isolated Sandbox Workflow (v1.0.2)

To ensure zero downtime, prevent broken git states, and protect the production GitHub repository:
1. **Isolated Development**: All feature additions, bug fixes, UI improvements, and testing for version `1.0.2` MUST be done inside:
   `c:\SimpleStream\Simple Stream 1.0.2\`
2. **Git Disconnection Invariant**: The `Simple Stream 1.0.2` directory MUST NEVER contain a `.git` folder. Do not run `git init` inside it.
3. **Compilation for Testing**:
   ```powershell
   # Inside c:\SimpleStream\Simple Stream 1.0.2:
   .\gradlew.bat assembleStableRelease
   Copy-Item -Path "app\build\outputs\apk\stable\release\app-stable-release.apk" -Destination "c:\SimpleStream\SimpleStream-1.0.2.apk" -Force
   ```
4. **Promotion to Master**: Only when the user explicitly approves releasing v1.0.2 will files be copied to `c:\SimpleStream\Simple-Stream\`, committed, tagged `v1.0.2`, and pushed to GitHub.

---

## 4. Critical Invariants (NEVER VIOLATE)

### 🔴 Rule 1: NEVER Rename Internal Package `com.lagradost.cloudstream3`
While the user-facing application ID is `com.github.sehgalvansh716pixel.simplestream`, the internal Kotlin/Java package hierarchy MUST remain `com.lagradost.cloudstream3`.
- **Reason**: External plugins and providers are compiled against classes like `com.lagradost.cloudstream3.plugins.Plugin` and `com.lagradost.cloudstream3.MainAPI`. Renaming internal class packages will completely break binary compatibility with the entire extension ecosystem.

### 🔴 Rule 2: Launcher Activity is `AccountSelectActivity`
In `AndroidManifest.xml`, the intent filter `<action android:name="android.intent.action.MAIN"/>` with `<category android:name="android.intent.category.LAUNCHER"/>` is attached to:
`com.lagradost.cloudstream3.ui.account.AccountSelectActivity`
- `MainActivity` is **not exported** (`android:exported="false"`). Starting `MainActivity` directly via ADB will throw a `SecurityException`.
- To launch the app via ADB:
  ```powershell
  adb shell monkey -p com.github.sehgalvansh716pixel.simplestream -c android.intent.category.LAUNCHER 1
  ```

### 🔴 Rule 3: Adaptive Icons & Monochrome Resources
Android 13+ (API 33+) requires monochromatic icons.
- All `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` files must reference a valid drawable (specifically `@drawable/ic_launcher_foreground`).
- Never delete or break `@drawable/ic_launcher_foreground` or `@drawable/splash_logo`.

### 🔴 Rule 4: Visual Brand Consistency
- **Primary Accent**: Electric Indigo (`#6366F1`)
- **Background / Canvas**: Dark Obsidian (`#0B0C10`, `#121212`, `#1F2833`)
- Maintain this modern, minimalist dark aesthetic across any new UI screens, glassmorphic panels, or dialogs.

### 🔴 Rule 5: Android TV Launcher Banner (`ic_banner.png`) & Leanback Rules
Android TV Leanback launcher mandates a 16:9 banner for app tiles.
- **Source Artwork**: `Tv Logo.png` in the project root.
- **Format**: High-resolution bitmap PNG (`@drawable/ic_banner`):
  - `drawable-xhdpi/ic_banner.png` (320x180 px)
  - `drawable-xxhdpi/ic_banner.png` (480x270 px)
  - `drawable-xxxhdpi/ic_banner.png` (640x360 px)
- **NO Vector Adaptive Banners**: Do NOT restore legacy `ic_banner_foreground.xml` or `mipmap-anydpi-v26/ic_banner.xml`.
- **Manifest Declarations**: Both `<application>` and `<activity android:name=".ui.account.AccountSelectActivity">` must specify `android:banner="@drawable/ic_banner"` and `android:logo="@drawable/ic_banner"`.
- **TV OS Cache Invariant**: Android TV OS caches app banners in its SQLite database by package name. When installing an updated APK with a new banner, users must uninstall the existing app or clear the launcher's cache for the new banner to display immediately.

### 🔴 Rule 6: TV D-Pad Navigation Stability
- Keep DPAD focus connections natural and clean.
- **NEVER** apply descendant-blocking (`FOCUS_BLOCK_DESCENDANTS`) or key-event trapping loops to the TV sidebar navigation rail (`MainActivity.kt`, `rail_footer.xml`, `SettingsFragment.kt`). The sidebar must preserve the original baseline navigation so users can smoothly jump between navigation tabs and page options without focus traps.

### 🔴 Rule 7: Pre-packaged Internal Extension & Secret Unlock Invariants (`1908`)
SimpleStream includes a pre-packaged NetMirror provider suite that is unlocked via code `1908`.
- **Activation Flow**: In Settings -> Extensions -> Add Repository, entering `1908` in the URL or Name field triggers activation.
- **Early Return Invariant**: The `1908` check MUST return early on line 1 of the dialog submit handler before any URL regex, empty checks, or network calls run in `ExtensionsFragment.kt` and `AppContextUtils.kt`. This prevents "Invalid URL" or "Invalid Data" dialogs.
- **App Restart Loss Invariant**: Activation is permanently stored using `setKey("internal_stream_bridge_unlocked", true)`. On every app launch, `MainActivity.onCreate()` calls `InternalStreamBridge.init(this@MainActivity)` synchronously to restore providers if `getKey("internal_stream_bridge_unlocked") == true`.
- **Camouflage & Disguise Invariant**:
  - Source file: `InternalStreamBridge.kt` (`com.lagradost.cloudstream3.utils`).
  - Class camouflage: `InternalStreamBridge`, `InternalStreamCommon`, `InternalStreamBase`, `InternalStreamA`, `InternalStreamB`, `InternalStreamC`.
  - UI Titles: `NetMirror - Netflix`, `NetMirror - Prime Video`, `NetMirror - Disney+ Hotstar`.
  - String Concealment: All scraper endpoints, CDN URLs, and API keys are Base64 encoded in `InternalStreamCommon` to blend in as player/streaming internals.
- **Locking / Removal**: If the user removes the NetMirror repository entry, `RepositoryManager.removeRepository()` invokes `InternalStreamBridge.lock()`, which clears the key (`setKey(..., false)`) and unregisters the providers.

---

## 5. How the In-App Updater Operates

The updater in `app/src/main/java/com/lagradost/cloudstream3/utils/InAppUpdater.kt` contains the following configuration:

```kotlin
const val GITHUB_USER_NAME = "sehgalvansh716-pixel"
const val GITHUB_REPO = "Simple-Stream"
```

### Version Parsing & Comparison Logic
- When checking for updates, the app queries:
  `https://api.github.com/repos/sehgalvansh716-pixel/Simple-Stream/releases`
- Scans release assets for files ending in `.apk` with regex pattern:
  `Regex("""(.*?((\d+)\.(\d+)\.(\d+))\.apk)""")`
- Version weighting formula:
  `weight = major * 100,000,000 + minor * 10,000 + patch`
- If `remote_weight > local_weight`, the update prompt is triggered.

---

## 6. Standard Step-by-Step Guide for Releasing Updates

When instructed to release an update (e.g. `1.0.2`):
1. **Sync Files**: Copy modified files from `Simple Stream 1.0.2/` to `Simple-Stream/`.
2. **Commit & Tag**:
   ```powershell
   git add -A
   git commit -m "SimpleStream v1.0.2: <features>"
   git tag -a v1.0.2 -m "Release SimpleStream 1.0.2"
   ```
3. **Push to Remote**:
   ```powershell
   $env:GIT_TERMINAL_PROMPT = 0
   git push origin master
   git push origin v1.0.2
   ```
4. **Publish Release**: Attach compiled `SimpleStream-1.0.2.apk` on GitHub for OTA distribution.
