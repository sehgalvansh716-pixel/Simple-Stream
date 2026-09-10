# SimpleStream UI Overhaul — Progress & Context Tracker

> **MANDATORY INSTRUCTION FOR ANY AI AGENT RESUMING THIS PROJECT**  
> Read the persistent context folder at `/.agent-context/` (or `c:\.SimpleStreamEnhanced\.agent-context\`) before modifying code.  
> **Instance Wrap-up Rule**: At the end of every session or when wrapping up, ask the user: *"Are you satisfied with the changes made in this session, and are you planning to move to a new session/instance?"* If yes, update all files in `/.agent-context/` immediately.  
> Whenever a UI change is finalized, verified on the emulator/device, and **explicitly approved/cleared by the owner**, both this file and `/.agent-context/03-CURRENT-STATE.md` MUST be updated immediately. Items marked as **[LOCKED & APPROVED]** must NOT be changed further without explicit user permission.


---

## 1. Project Overview & Operational Guardrails

* **Application**: SimpleStream v1.0.1 (`versionCode = 102`, `versionName = "1.0.1"`)
* **Location**: `c:\.SimpleStreamEnhanced\Simple-Stream 1.0.1\`
* **Application ID**: `com.github.sehgalvansh716pixel.simplestream`
* **Internal Package**: `com.lagradost.cloudstream3` (MUST NEVER BE RENAMED)
* **Launcher Activity**: `com.lagradost.cloudstream3.ui.account.AccountSelectActivity`
* **Sandboxed Workspace**:
  * This folder is **completely detached from Git** (no `.git` directory).
  * **DO NOT** run `git init`, `git add`, `git commit`, or `git push` under any circumstances.
  * All work remains strictly local and sandboxed.
* **Testing Protocol (CRITICAL USER INSTRUCTION)**:
  * **DO NOT** use browser/screenshot analysis subagents for manual UI validation — the user specifically requested to save tokens by letting them test on the active Android TV emulator (`emulator-5554`).
  * Workflow: Apply changes -> compile (`assembleStableRelease`) -> sideload via `adb -s emulator-5554 install -r ...` -> provide brief pointers for the user to verify in the app.

---

## 2. Technical Invariants (NEVER BREAK)

1. **🔴 Package Name Invariant**:  
   All internal Kotlin code and XML bindings must use package `com.lagradost.cloudstream3`. Changing this breaks binary compatibility with all external `.cs3` repository plugins and scraper extensions.
2. **🔴 Launcher Activity Invariant**:  
   The launcher activity is `AccountSelectActivity`. `MainActivity` is not exported (`android:exported="false"`).
3. **🔴 TV Launcher Banner Invariant**:  
   Android TV Leanback launcher requires a standard 16:9 bitmap drawable at `@drawable/ic_banner`. Never reintroduce legacy CloudStream vector XMLs for banners.
4. **🔴 D-Pad Focus Invariant**:  
   Android TV navigation must remain natural and fluid. Do NOT attach descendant-blocking (`FOCUS_BLOCK_DESCENDANTS`) or focus traps to the sidebar navigation rail (`nav_rail_view`).
5. **🔴 Built-in NetMirror / Secret Unlock 1908**:  
   The built-in provider bridge in `InternalStreamBridge.kt` and the early-return code intercept in `ExtensionsFragment.kt` (entering `1908`) must remain functional.
6. **🔴 Design Foundation & Palette**:  
   * **Electric Indigo**: `#6366F1` (Primary Accent)
   * **Deep Indigo Dark**: `#4338CA`
   * **Dark Obsidian**: `#0B0C10` (Root Canvas / Background)
   * **Charcoal Surface**: `#14161D` / `#121319` (Cards & Dialogs)
   * **Text Primary**: `#E9EAEE`
   * **Text Muted**: `#9BA0A4`
   * **Bundled Fonts**: `google_sans`, `netflix_sans`, `poppins_regular`, `productsans_*`

---

## 3. Build & Deployment Commands

```powershell
# 1. Compile Stable Release APK
.\gradlew.bat assembleStableRelease

# 2. Install directly onto connected emulator (e.g. emulator-5554)
adb -s emulator-5554 install -r "app\build\outputs\apk\stable\release\app-stable-release.apk"

# 3. Launch App on Emulator
adb -s emulator-5554 shell monkey -p com.github.sehgalvansh716pixel.simplestream -c android.intent.category.LAUNCHER 1
```

---

## 4. UI Overhaul Status Board

### Overall Progress
| Screen / Component | Layout File(s) | Status | Approval Date | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **TV Top-Right Navbar** | `activity_main_tv.xml` | ✅ STABLE | — | Glassmorphic floating capsule (`tv_navbar_pill`), Home/Search/Library/Downloads/Settings tabs |
| **Hero Carousel & Buttons** | `fragment_home_head_tv.xml` | ✅ STABLE | — | Enlargement on focus preserved; outline cropping fixed; smooth D-Pad navigation to cards |
| **Plugin Selector Pill** | `fragment_home_head_tv.xml`, `bg_tv_provider_pill.xml` | ✅ STABLE | — | Glassmorphic style, correct outline | **TV In-Plugin Search Overlay** | `fragment_home_tv.xml`, `fragment_home_head_tv.xml`, `HomeFragment.kt`, `item_tv_plugin_search_card.xml` | 🔄 IN REVIEW | — | Seamless 60fps Cinejoy procedural shader loop (`tv_search_bg.mp4`); pure glassmorphic search bar; animated collapsing header; 5-column result grid; focused card centered white play circle, title & year/rating overlay |
| **Content Cards Feed** | `fragment_home_tv.xml`, `home_result_grid.xml`, `home_result_grid_expanded.xml`, `SearchResultBuilder.kt` | ✅ STABLE | — | Preserved 1.08f card enlargement & white outline on focus; centered white play circle + black arrow, title, and gold star year/rating overlay matching search plugin card look |
| **Search Screen & Chips** | `fragment_search.xml`, `search_result_grid.xml` | ⏳ PENDING REFERENCE | — | Global search |
| **Media Details / Results** | `fragment_result.xml`, `result_episode_large.xml` | ⏳ PENDING REFERENCE | — | Awaiting user reference images |
| **Video Player & HUD Overlay** | `player_custom_layout.xml`, `glass_speedup_pill.xml` | ⏳ PENDING REFERENCE | — | Awaiting user reference images |

---

## 5. Detailed Implementation Notes: TV In-Plugin Search & Content Cards

### User Requirements & References:
* Separate from global search. Searches only within the currently selected plugin/provider.
* Search button positioned directly to the right of the plugin selector pill in the hero carousel header.
* Glassmorphic search bar pill (`bg_tv_search_pill.xml`) with neutral translucent glass (`#14FFFFFF` default, `#29FFFFFF` focused) letting the fluid background completely dictate tint without blue/slate color clash.
* Looping background video: Seamless 60fps Cinejoy procedural WebGL shader video (`tv_search_bg.mp4`), rendered through hardware-accelerated `TextureView` with auto-crop matrix transform and subtle atmospheric overlay (`bg_tv_search_video_overlay.xml`).
* Initial state (`Screenshot 2026-09-06 222215.png`):
  * Top-left back button (`home_plugin_search_back`) and brand logo (`home_plugin_search_logo`).
  * Title headline: `"Let's find a movie night pick"` (`@id/home_plugin_search_title`).
  * Glassmorphic search capsule (`@id/home_plugin_search_bar`) with search icon, text input, clear button, and progress spinner.
  * Section title: `"Trending Today"` (`@id/home_plugin_search_section_title`) loaded automatically from the provider's homepage if search input is empty.
  * 5-column content card grid (`@id/home_plugin_search_recycler`) peeking at the bottom of the screen.
* Navigation & collapse transition (`Screenshot 2026-09-06 222223.png`):
  * Moving D-Pad focus down from the search bar to the cards smoothly animates `home_plugin_search_header_group` upward (`translationY` / `alpha -> 0`).
  * `home_plugin_search_content_group` translates upward so `"Trending Today"` / query results title docks at the top center (`~28dp`), expanding 2 full rows of 5-column cards across the screen.
  * Pressing `DPAD_UP` from row 0 of cards smoothly slides the search header back down into full view and returns focus to `home_plugin_search_input`.
  * Pressing Back while cards are focused returns focus to the search input; pressing Back again closes the overlay and pauses the video.
* Card styling (`To be expected card look in search plugin.png`):
  * Consistent across both **Plugin Search** (`item_tv_plugin_search_card.xml`) and **Home screen rails** (`home_result_grid.xml`, `home_result_grid_expanded.xml`, `SearchResultBuilder.kt`).
  * **On focus**:
    - Retains 1.08f scale enlargement and crisp white focus outline (`R.drawable.outline`).
    - Hides corner badges and default bottom title.
    - Shows centered solid white circular play button (`bg_tv_card_play_circle.xml`) with solid black play triangle (`ic_tv_play_black.xml`).
    - Shows centered bold white title and `Year ★ Rating` with gold star (`#FFC107`) over a 45% dark scrim.
  * **On unfocus**:
    - Smoothly animates scale back to 1.0f, removes outline, and restores normal poster view.

### Key Files Involved:
1. `app/src/main/res/raw/tv_search_bg.mp4`: 60fps Cinejoy procedural shader loop.
2. `app/src/main/res/drawable/bg_tv_search_pill.xml`: Neutral translucent glassmorphic search bar.
3. `app/src/main/res/drawable/bg_tv_search_video_overlay.xml`: Atmospheric overlay with transparent center and bottom gradient.
4. `app/src/main/res/drawable/bg_tv_card_play_circle.xml`: Solid white circular play button background.
5. `app/src/main/res/drawable/ic_tv_play_black.xml`: Solid black play arrow vector.
6. `app/src/main/res/layout/item_tv_plugin_search_card.xml`: 5-column TV card layout with centered focus info.
7. `app/src/main/res/layout/home_result_grid.xml` & `home_result_grid_expanded.xml`: TV home rail content card layouts with centered focus info.
8. `app/src/main/java/com/lagradost/cloudstream3/ui/search/SearchResultBuilder.kt`: Focus handling and data binding for TV home cards.
9. `app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt`:
   - `initTvPluginSearchVideo()`: lifecycle & matrix transform for background video playback.
   - `setPluginSearchCardsFocusedState()`: interactive collapse/expand animation on navigation.
   - `TvPluginSearchAdapter`: 5-column adapter with focus listeners and D-Pad key handlers.
10. `app/src/main/java/com/lagradost/cloudstream3/utils/CardMetadataManager.kt`:
    - Centralized metadata resolution & caching.
    - Asynchronous loading modeled after cinematic carousel (`APIRepository.load(url)`).
    - Subtitle formatting with gold star: `Year ★ Rating`.
    - Fixes persistent HD badge bug (`val hasQuality = card.quality != null`).
11. Reference Images:
    - `c:\.SimpleStreamEnhanced\Tv ui\Screenshot 2026-09-06 222215.png` (Search state with green looping video)
    - `c:\.SimpleStreamEnhanced\Tv ui\Screenshot 2026-09-06 222223.png` (Cards navigation state with collapsed search bar)
    - `c:\.SimpleStreamEnhanced\To be expected card look in search plugin.png` (Focused card look: white play circle, black arrow, title, year ★ rating)
