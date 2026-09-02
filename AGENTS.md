# AGENTS.md — Developer & AI Agent Context Manual for SimpleStream

> **CRITICAL DIRECTIVE FOR ALL AI AGENTS**:  
> Read this entire document before inspecting or modifying any code.  
> **SimpleStream 1.0.0 is the official starting point**, and this directory (`c:\SimpleStream\Simple-Stream`) is the primary repository root where all future development, version updates, builds, and git pushes will take place.  
> The project owner has **zero programming knowledge**. You must make all technical decisions autonomously, never delegate code tasks, and ensure full verification before completing any task.

---

## 1. Project Overview & Origins

- **Application Name**: SimpleStream
- **Starting Version**: `1.0.0` (`versionCode = 100`)
- **Package ID**: `com.github.sehgalvansh716pixel.simplestream` (Debug build appends `.debug`: `com.github.sehgalvansh716pixel.simplestream.debug`)
- **GitHub Repository**: [https://github.com/sehgalvansh716-pixel/Simple-Stream](https://github.com/sehgalvansh716-pixel/Simple-Stream)
- **GitHub Owner**: `sehgalvansh716-pixel`
- **License**: GNU General Public License v3.0 (GPLv3).
- **Lineage**: SimpleStream is a personalized, rebranded open-source streaming client for Android and Android TV, originally forked from CloudStream 3 (`recloudstream/cloudstream`).

---

## 2. Core Architecture & Project Structure

SimpleStream is organized as a multi-module Gradle project:

```
c:\SimpleStream\Simple-Stream\
├── app/                        # Android Application Module
│   ├── build.gradle.kts        # Versioning, dependencies, flavors (stable/prerelease), signing
│   └── src/
│       ├── main/               # Main codebase
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/lagradost/cloudstream3/
│       │   │   ├── ui/         # Activities, fragments, viewmodels, settings, setup flows
│       │   │   ├── utils/      # InAppUpdater, media player utilities, coroutines
│       │   │   └── services/   # Background downloaders, workers
│       │   └── res/            # Layouts, values (colors, strings), drawables, mipmaps
│       ├── debug/              # Debug-specific resources (strings.xml, mipmaps)
│       └── prerelease/         # Prerelease / Beta resources
├── library/                    # Kotlin Multiplatform (KMP) Module
│   ├── build.gradle.kts        # Core provider / extension API contracts
│   └── src/                    # Shared networking, parsers, and data models
├── gradle/                     # Gradle wrapper definitions
├── gradlew.bat                 # Windows Gradle build script
├── settings.gradle.kts         # Root settings (rootProject.name = "SimpleStream")
└── AGENTS.md                   # THIS FILE (Permanent AI Agent Guide)
```

### Key Architectural Systems
1. **Extension / Provider System**:
   - SimpleStream does not host media content directly. Providers are loaded dynamically as `.cs3` plugins from user-configured repository URLs (or local storage).
   - Extension API contracts reside in the `:library` module.
2. **Player Engine**:
   - Built on top of AndroidX Media3 (ExoPlayer), with custom subtitle decoders, ExoPlayer renderers, and embedded torrent streaming support via TorrServer.
3. **In-App Updater (`InAppUpdater.kt`)**:
   - Located at `app/src/main/java/com/lagradost/cloudstream3/utils/InAppUpdater.kt`.
   - Directly integrated with the GitHub Releases API of `sehgalvansh716-pixel/Simple-Stream`.
   - Parses releases, compares semantic version weights, and triggers downloads/installations.

---

## 3. Critical Invariants (DO NOT BREAK)

### 🔴 Rule 1: NEVER Rename Internal Package `com.lagradost.cloudstream3`
While the user-facing application ID is `com.github.sehgalvansh716pixel.simplestream`, the internal Kotlin/Java package hierarchy MUST remain `com.lagradost.cloudstream3`.
- **Reason**: External plugins and providers are compiled against classes like `com.lagradost.cloudstream3.plugins.Plugin` and `com.lagradost.cloudstream3.MainAPI`. Renaming internal class packages will completely break binary compatibility with the entire extension ecosystem.

### 🔴 Rule 2: Launcher Activity is `AccountSelectActivity`
In `AndroidManifest.xml`, the intent filter `<action android:name="android.intent.action.MAIN"/>` with `<category android:name="android.intent.category.LAUNCHER"/>` is attached to:
`com.lagradost.cloudstream3.ui.account.AccountSelectActivity`
- `MainActivity` is **not exported** (`android:exported="false"`). Starting `MainActivity` directly via ADB will throw a `SecurityException`.
- To launch the app via ADB:
  ```powershell
  adb shell monkey -p com.github.sehgalvansh716pixel.simplestream.debug -c android.intent.category.LAUNCHER 1
  ```

### 🔴 Rule 3: Adaptive Icons & Monochrome Resources
Android 13+ (API 33+) requires monochromatic icons.
- All `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` files must reference a valid drawable (specifically `@drawable/ic_launcher_foreground`).
- Never delete or break `@drawable/ic_launcher_foreground` or `@drawable/splash_logo`.

### 🔴 Rule 4: Visual Brand Consistency
- **Primary Accent**: Electric Indigo (`#6366F1`)
- **Background / Canvas**: Dark Obsidian (`#0B0C10`, `#121212`, `#1F2833`)
- Maintain this modern, minimalist dark aesthetic across any new UI screens or dialogs.

---

## 4. How the In-App Updater Operates

The updater in `app/src/main/java/com/lagradost/cloudstream3/utils/InAppUpdater.kt` contains the following configuration:

```kotlin
const val GITHUB_USER_NAME = "sehgalvansh716-pixel"
const val GITHUB_REPO = "Simple-Stream"
```

### Version Parsing & Comparison Logic
- When checking for updates, the app queries:
  `https://api.github.com/repos/sehgalvansh716-pixel/Simple-Stream/releases`
- It scans the release assets for files ending in `.apk` with regex pattern:
  `Regex("""(.*?((\d+)\.(\d+)\.(\d+))\.apk)""")`
- Version weighting formula:
  `weight = major * 100,000,000 + minor * 10,000 + patch`
- If `remote_weight > local_weight`, the update prompt is triggered.
- Note: In `BuildConfig.DEBUG`, update checks return `false` by design so debug sessions are not interrupted. In `stable` release builds, it triggers actively.

---

## 5. Standard Step-by-Step Guide for Future Updates

When tasked with creating and releasing an update (e.g., version `1.0.1`, `1.1.0`, etc.), follow this exact checklist:

### Step 1: Increment Version in `app/build.gradle.kts`
Navigate to `app/build.gradle.kts` and update:
```kotlin
// Example for version 1.0.1:
val currentVersionCode = 101       // Increment integer (was 100)
val currentVersionName = "1.0.1"   // Update semver string (was "1.0.0")
```

### Step 2: Implement Feature / Fix
- Write clean, modular Kotlin code.
- Follow existing patterns in `:app` and `:library`.
- Keep strings localized in `app/src/main/res/values/strings.xml`.

### Step 3: Build and Test on Emulator
Always build and test before committing:
```powershell
# From c:\SimpleStream\Simple-Stream:
.\gradlew.bat assembleStableDebug

# Install on emulator:
adb install -r "app\build\outputs\apk\stable\debug\app-stable-debug.apk"

# Launch app:
adb shell monkey -p com.github.sehgalvansh716pixel.simplestream.debug -c android.intent.category.LAUNCHER 1

# Capture screenshot for visual validation:
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png "test_screenshot.png"
```

### Step 4: Commit and Tag in Git
```powershell
# Verify status:
git status

# Stage and commit:
git add -A
git commit -m "SimpleStream v1.0.1: Summary of changes"

# Create annotated git tag:
git tag -a v1.0.1 -m "Release SimpleStream 1.0.1"
```

### Step 5: Push to GitHub Remote
```powershell
$env:GIT_TERMINAL_PROMPT = 0
git push origin master
git push origin v1.0.1
```

### Step 6: Create the GitHub Release (for OTA Updates)
To make the update downloadable by installed apps:
1. Build the release or debug APK:
   `.\gradlew.bat assembleStableRelease` (or copy the debug APK for testing).
2. Name the APK asset: `SimpleStream-1.0.1.apk`.
3. Publish a release on GitHub under tag `v1.0.1` and attach `SimpleStream-1.0.1.apk` as an asset.

---

## 6. Windows & Environment Specific Gotchas

1. **PowerShell Binary Redirection**:
   - Never run `adb exec-out screencap -p > file.png` in standard PowerShell; it corrupts the PNG with UTF-16LE encoding.
   - Always use `adb shell screencap -p /sdcard/screen.png; adb pull /sdcard/screen.png output.png`.
2. **File Locking**:
   - If Gradle reports locked files or directories cannot be renamed, stop the Gradle daemons with:
     `.\gradlew.bat --stop`
3. **Desugar JDK Libs Cache**:
   - If you encounter desugar errors during compilation, clean the cache with `.\gradlew.bat clean`.

---

## 7. Directory Roadmap & Clean State

The workspace root at `c:\SimpleStream\` is organized as follows:
- **`Simple-Stream/`**: The permanent, version-controlled git project.
- **`SimpleStream-1.0.0.apk`**: The baseline compiled APK for v1.0.0.
- **`Logo.png`**: Original source brand logo.
- **`AGENTS.md`**: This guide.

*SimpleStream 1.0.0 is complete and production-ready. All future work continues seamlessly from here.*
