# Changelog

All notable changes to this project are recorded here.
The format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.4.1-m3] - 2026-10-03 (bug sweep)

### Fixed
- **Program rotation:** after deleting a routine from a program, "next routine"
  could repeat the same one forever. It mixed stored positions with list order.
  Now it uses the current order (regression test added).
- **Rest timer:** Android drops pending alarms when an app is force-stopped or
  updated, so a rest that was running when you installed an update never buzzed.
  The alarm is now re-armed when the app starts.
- **Opening from the rest-timer notification:** a stale "open workout" request
  could linger after the workout ended and jump to a later workout unexpectedly.
  It now waits for the database to load and always clears.
- **Today:** the "This week" count was really the last 7 days. It's now labelled
  that way.

## [0.4.0-m3] - 2026-10-03 (Milestone 3: Routines & programs)

### Added
- **Routines**
  - Create, rename, add notes, duplicate, file in folders, reorder, and delete with
    undo.
  - Each exercise has target sets, a rep range (or seconds for timed moves), rest
    and supersets.
  - Drag ☰ to reorder exercises.
  - Each routine shows an estimated length ("~30 min").
- **Start from a routine**
  - The workout gets the routine's exercises, order, supersets, rest and targets,
    with one row per target set (plus last time's warm-ups).
  - Each exercise card shows "Target 3 × 8–12".
- **Ready-made home programs** (copied into your routines so you can edit them):
  - Full Body Home (A/B, Mon/Wed/Fri, ~30 min)
  - Home Push/Pull/Legs (any days, ~40 min)
  - Calisthenics Foundations (A/B, Mon/Wed/Fri, ~40 min)
  - Express 15 (two supersets and a plank)
- **Active program and today's plan**
  - Routines rotate in order on your training days. Missed days don't skip a
    routine.
  - Today shows "Today: …" with Start, "Rest day. Next up: …", or "Done for today".
  - Training days are editable, and you can stop following a program at any time.
- **Today:** your routines with one-tap Start, plus a link to Routines & programs.
- **Easier / Harder variation** in a workout's exercise menu swaps along the
  progression ladder (before you've ticked a set of it).
- **Save as routine** on any finished ad-hoc workout.
- **Tests:** templates (every exercise exists, equipment declared, ranges valid,
  durations match what's advertised), schedule rotation and rest days, the time
  estimate, routine CRUD, superset tidying, template install, start-from-routine,
  save-as-routine, deleting a program, and the v1 → v3 migration.

### Changed
- Database version 3 (automatic migration; all data kept).
- The JSON export (format 3) includes programs and routines.

## [0.3.1-m2] - 2026-10-03 (fix)

### Fixed
- A workout still asked for your bodyweight (and showed no loads) after you'd
  entered it in Settings.
  - Cause: a workout copies your bodyweight when it starts, and one started before
    you entered it had none to copy.
  - Fix: such a workout now uses your latest logged bodyweight, saves it, and
    recalculates the sets you've already ticked. It only asks if you've never
    entered a weight.
- The "+kg" box on bodyweight exercises shows "–" instead of "0", making it clear
  it's only for optional extra weight (vest or bag).

## [0.3.0-m2] - 2026-10-03 (Milestone 2: Exercise library + bodyweight loads)

### Added
- **Bodyweight load calculation** (your request after M1)
  - Each calisthenics movement has a profile: the share of bodyweight you
    actually lift, with its source:
    - push-up 64%, knee push-up 49%, incline and decline values measured with
      force plates (Ebben et al. 2011)
    - squat 89%, pull-up, dip and handstand push-up 95% (body-segment masses,
      de Leva 1996)
    - other moves are clearly marked as estimates
  - Incline and decline push-ups scale with bench height relative to your height.
  - Added weight (vest or bag) counts by how it's carried: fully in a pull-up,
    about 70% in a push-up.
  - One-sided moves (archer, one-arm, pistol) show the load per side.
  - 50+ library exercises are matched automatically and now log as reps
    (+ optional added kg).
- **Body section in Settings:** bodyweight log (dated entries) and height.
  - Each workout snapshots your bodyweight when it starts, so old workouts never
    change when your weight does.
  - "Bodyweight today" in the workout menu updates it and recalculates that
    workout's bodyweight sets.
- **Per-set load:** stored when you tick a set. Volume and the History totals now
  include the bodyweight you lifted.
- **Home & calisthenics pack** (45 exercises):
  - push-up, handstand push-up, pull-up (from a doorway row upwards), dip,
    single-leg squat, leg raise, core hold, hinge and calf progressions
  - 14 weighted-bag exercises: squats, deadlifts, cleans, press, rows, carries
    and more
- **9 progression chains**, shown as a ladder on each exercise page.
- **Exercise images:** all 1,746 free-exercise-db images, bundled as compact WebP
  (about 17 MB). Thumbnails appear in lists and an animated start/end demo on
  the exercise page.
- **Exercise page:** muscles, equipment, level, bodyweight load with its
  explanation and source, progression ladder, instructions, and your last 10
  sessions with estimated 1RM.
- **Custom exercises:** create, edit and archive.
  - Fields: name, log type, bodyweight type and bench height, equipment,
    muscles, instructions.
  - A "My exercises" filter and "New" in the exercise picker.
- **Tests:** the bodyweight maths against the study values, the matcher against
  the real dataset, home-pack integrity (every progression step exists, no
  duplicate names), a database migration test from the real v1 schema, load
  recalculation, and the load helper.

### Changed
- Database version 2 (automatic migration; all M1 data is kept).
- The JSON export (format 2) now includes your bodyweight log and height.

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
