# Changelog

All notable changes to this project are recorded here.
The format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.1.0-m0] - 2026-10-02 (Milestone 0: Foundation)

### Added
- Android project set up with: Gradle 9.6, Android Gradle Plugin 9.4 (built-in
  Kotlin 2.4), Jetpack Compose (BOM 2026.09.00), Material 3, Hilt, Room 2.8,
  DataStore, and type-safe Navigation.
- `:domain` module (pure Kotlin) with:
  - e1RM formulas (Epley, Brzycki, RPE-adjusted)
  - kg/lb conversion and rounding helpers
  - unit tests for both
- Dark-first design system with:
  - colour tokens (dark and light), type scale with tabular numbers, spacing scale,
    and shapes
  - named haptics
  - shared components: `BigButton`, `ForgeCard`, `EmptyState`, `SectionHeader`,
    `MilestoneBadge`
- App shell with:
  - a splash screen that waits for settings, so the theme never flashes
  - an edge-to-edge layout
  - a 5-tab bottom navigation: Today, History, Exercises, Progress, Settings
  - collapsing large titles
- Settings screen, saved with DataStore:
  - theme (dark/light/system)
  - weight unit
  - default rest timer
  - equipment you own (default: bodyweight, bands, dumbbells)
- Draft Room schema v1: `exercise`, `workout_session`, `session_exercise`,
  `set_entry`. Every table has UUID ids, timestamps, and soft deletes. The schema
  isn't opened yet; it will be finalized in M1.
- Shared debug signing key, so APKs built locally and on CI install over each other.
- Optional release signing, from `keystore.properties` or GitHub secrets.
- GitHub Actions workflow that runs the unit tests, builds the debug APK (and the
  release APK when secrets exist), and uploads them as downloadable artifacts.
- `docs/PLAN.md`: the approved project plan.
- README with a Linux (Nobara) setup guide and S25+ install steps.
