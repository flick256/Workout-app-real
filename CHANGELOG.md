# Changelog

All notable changes to this project are recorded here.
The format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.2.0-m1] - 2026-10-02 (Milestone 1: Workout logging)

### Added
- **Workout logging**
  - Start an empty workout or resume the one in progress (never two at once).
  - Each set has a type (warm-up, normal, drop, failure), weight × reps (or
    bodyweight reps with optional added weight, time, or distance and time), RPE,
    and a ✓ to complete it.
  - "Previous" column and grey hints show last time's numbers, matched set by set.
    Ticking an empty row fills it from last time.
  - Add or delete sets with undo, plus an automatic warm-up ramp.
  - Supersets, reordering, per-exercise notes and rest time, and workout notes.
  - Quick-pick chips for weights you own.
  - Finish shows a summary (time, sets, reps, volume). Discarding asks for
    confirmation.
- **Rest timer**
  - Runs in the background on an exact alarm.
  - The countdown notification has **+30s** and **Skip** buttons.
  - A "Rest over" alert vibrates and sounds.
  - An in-app timer bar has −15/+15/Skip, and the timer survives app restarts.
  - Rest after a warm-up set is capped at 60 s.
  - Within a superset, rest only starts after the last exercise of the round.
- **Exercise library:** 876 exercises from free-exercise-db, bundled and imported
  on first launch.
  - Search ignores hyphens and case, so "pushup" finds "Push-Ups".
  - Filter by muscle or by "My equipment", with a Recent section.
  - Tap an exercise for its instructions.
- **History:** workouts grouped by month, with time, sets, volume and an exercise
  summary. The detail view has delete with undo.
- **Today:** start or resume, a last-workout card, and workouts this week.
- **Resume bar** above the tabs while a workout is running.
- **Equipment**
  - New items: weighted bag and weighted vest.
  - "Weights I own" editor per item: add single weights or an adjustable range,
    e.g. 2.5–24 kg in 2.5 kg steps.
- **Export:** Settings → Export data (JSON) writes your workouts, sets, custom
  exercises and settings to a file you choose.
- **Tests**
  - Domain tests for the dataset parser, warm-up generator, weight snapping,
    stats and previous-set matching.
  - Robolectric tests for the repository (including surviving an app kill),
    seeding and supersets.
  - An export round-trip test.

### Changed
- Database schema v1 is now final. Future changes ship with migrations.
- CI commits the exported Room schema (`app/schemas/`) automatically.

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
