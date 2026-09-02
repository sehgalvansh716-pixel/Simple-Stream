# SimpleStream — Development Roadmap

This document outlines the planned future development phases for **SimpleStream**. Each major phase will be implemented in its own dedicated task directory (`/task-0X-...`) per the project workflow rules.

---

## Completed Phases

- [x] **Phase 1: Analysis & Rebranding** (`/task-01-rebrand`)
  - Full codebase architectural analysis and plain-English documentation (`/docs/APP_OVERVIEW.md`).
  - Rebranded app identity to **SimpleStream** across resources, strings, manifest, and build configuration.
  - Updated package/application ID to `com.github.sehgalvansh716pixel.simplestream`.
  - Applied new minimalist color palette (Electric Indigo `#6366F1` and deep obsidian `#0B0C10`).
  - Generated and installed new high-definition launcher assets, adaptive foregrounds, and Android TV banners from the minimalist geometric logo.
  - Removed old developer links, telemetry, and identifiers; added license-compliant attribution for GPLv3 fork under `sehgalvansh716-pixel`.
  - Verified compilation and confirmed plugin/extension compatibility.

---

## Upcoming Phases (Future Prompts)

- [ ] **Phase 2: Modern UI/UX Redesign** (`/task-02-ui-redesign`)
  - Revamp home screen cards and hero banners with smooth transitions and glassmorphic elements.
  - Modernize the media details screen with richer metadata presentation, improved season/episode carousels, and enhanced backdrop gradients.
  - Upgrade the in-app video player overlay with sleek minimalist playback controls, modern time scrubber, and refined quick-setting sheets.
  - Streamline settings navigation and user preference categorization.

- [ ] **Phase 3: Android TV & Large Screen Enhancements** (`/task-03-android-tv-support`)
  - Optimize 10-foot Leanback user experience for smart TVs, Google TV devices, and Android boxes.
  - Enhance remote D-Pad navigation, focus states, and animations across all screens.
  - Implement full Android TV "Watch Next" / "Continue Watching" recommendation channel integration.
  - Add customized TV quick-actions and gamepad controller mapping.

- [ ] **Phase 4: Custom Extension Creation & Dev Tooling** (`/task-04-custom-extension-creation`)
  - Build automated tooling and templates for creating custom website scrapers and video extractors.
  - Provide an isolated plugin testing environment within the project to test new scraping providers quickly.
  - Create developer guides and recipes for handling tricky video hosts, captcha-protected sites, and authenticated sources.
  - Implement a streamlined repository generator to easily host and distribute private plugin feeds via GitHub.

- [ ] **Phase 5: New Advanced Features & Media Integrations** (`/task-05-new-features`)
  - Advanced media tracking integration (Simkl, AniList, MyAnimeList, Trakt) synchronization enhancements.
  - Download storage management enhancements (automatic storage cleanup, SD card migration).
  - Audio-only playback mode for background podcasts, audiobooks, and stream listening.
