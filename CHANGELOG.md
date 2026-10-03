# Changelog

All notable changes to this project are recorded here.
The format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.10.0-m9] - 2026-10-03 (Milestone 9: Widgets & shortcuts)

### Added
- **Today widget** (resizable):
  - Shows today's program routine, or a rest day with the next training day.
  - Its button starts today's routine, an empty workout, or resumes the one running.
  - Shows workouts this week and your week streak.
  - When larger, it adds Scan food, today's calories and protein against your
    targets, and habits done.
- **Habits widget:** today's habits with streaks. Tap a custom habit to tick it off
  from the home screen.
- **Launcher shortcuts:** Start workout, Scan food (opens straight to the scanner),
  Log activity, Habits.
- Widgets refresh themselves when your data changes while Forge is running, and every
  30 minutes for the date.

### Changed
- Notifications, widgets and shortcuts share one set of app actions, so each one
  opens exactly where it says.

## [0.9.0-m8] - 2026-10-03 (Milestone 8: Goals, habits and on-device AI)

### Added
- **Habits**
  - Custom habits you tick by hand.
  - Auto habits that tick themselves from your data: training or an activity,
    hitting your protein target, logging food, steps, sleep.
  - Pick the days each is due; a day it isn't due never breaks a streak.
  - Streaks, best streak, a 7-day strip and 30-day completion.
  - Optional reminder notifications with a "Done" action. They are re-set after a
    reboot, an app update or a time-zone change.
- **Goals:** workouts per week, a lift (estimated 1RM), reps in one set, bodyweight
  (either direction, from your starting weight) and protein days per week.
- **Achievements:** 17 milestones for consistency and strength, with "up next" progress.
- **Today:** a habits card (tap to tick) showing your closest goal.
- **Quick log** in a workout, by voice or text ("3x8 bench at 60"). It matches the
  exercise, fills and ticks the sets, and always asks you to confirm first.
- **Nutrition label scanning:** on-device text recognition plus a parser for
  AU/NZ panels (kJ, per-serving and per-100 g columns, sodium in mg). It also copes
  with common OCR slips.
- **Optional on-device AI**
  - Gemma 4 E2B running locally with LiteRT-LM (GPU, falling back to CPU).
  - Download it with Android's download manager, or import a model file.
  - Uses:
    - a weekly summary;
    - an explanation of Forge's plateau check;
    - a fallback for quick-log phrasings the parser can't read.
  - Every AI text is checked before it's shown: no numbers that aren't in your
    data, no risky diet advice. Otherwise Forge shows its own version.
- **Plateau check** on each exercise's progress page. Rule-based: e1RM trend, rising
  RPE, weekly sets against target, training frequency, sleep and protein.
- **Weekly summary** on the Progress tab: the last 7 days against the 7 before.

### Changed
- Database version 7 (goals, habits and habit ticks). Export format 7.
- Debug and release builds now include the LiteRT-LM and ML Kit text-recognition
  libraries.

## [0.8.0-m7] - 2026-10-03 (Milestone 7: Nutrition lite)

### Added
- **Food diary** (Today → Food today): calories and protein/carbs/fat for the day
  against your targets, then breakfast, lunch, dinner and snacks. Tap an item to
  change the amount or meal, or remove it (with undo). Copy a meal from the day before.
- **Barcode scanning** with Google's on-device code scanner (no camera permission),
  looked up on **Open Food Facts**. Found products are saved on the phone, so a
  second scan works offline and keeps any corrections you make.
- **Food search**: your saved foods instantly, Open Food Facts on request.
  Favourites and recents.
- **Create or edit foods** from a label, per 100 g or per serving, with a check
  that calories match the macros (catches kJ typed as kcal).
- **Quick add** calories/macros without a food.
- **Targets** from Mifflin–St Jeor × activity, with gentle goals (under 18 the
  deficit is capped at 250 kcal), protein 1.6–2.0 g/kg, and the working shown. Or
  set your own numbers.
- Today shows a food card; Settings has a Nutrition section.

### Fixed (from a full code review)
- Undo after deleting a workout or progress photo now works (it was lost when the
  screen closed).
- Today showed no routines under "My routines" when no program was active.
- Health Connect: after not opening Forge for over a week, the gap is now filled in
  (up to 30 days); reconnecting or allowing more data re-reads 30 days; leaving the
  Health screen no longer cancels a sync and reports it as failed.
- Health Connect duplicates: a strap session only merges into a hand-logged activity
  of the same sport; the same session recorded by two apps is imported once; the
  same night recorded by two apps isn't counted twice; afternoon naps count for the
  right day.
- Adding strap heart rate to a workout can no longer undo a finish happening at the
  same moment.
- Ticking a set uses its latest saved numbers, so a value typed a split second
  before isn't lost.
- Logging an activity just after midnight defaulted to 11 pm *tonight*.
- Double-tapping Save on an activity no longer creates two.
- Quick equipment toggles in Settings could overwrite each other.
- Recovery and readiness on Today refresh every minute instead of only when data changes.

### Changed
- Database version 6 (foods and food log). Upgrades automatically; covered by the
  migration test.
- JSON export format 6 includes foods, the food log and your nutrition profile.
- Forge now has the INTERNET permission, used only for Open Food Facts lookups.

## [0.7.0-m6] - 2026-10-03 (Milestone 6: Other activities + Health Connect)

### Added
- **Activities**: log sports, cardio and mobility sessions (28 types) with date,
  start time, duration, effort (1–10), distance, name and notes. Edit or delete
  them from History.
- **Activities count towards recovery.** Every 10 minutes at full effort counts
  like one hard set for the muscles the sport works (capped at 8), so "What should
  I train?" accounts for yesterday's game. The log screen previews the effect.
- **History** shows workouts and activities together, month by month.
- **Health Connect** (Settings → Health & watch), read-only:
  - Imports exercise sessions from your watch/strap app (e.g. Zepp for the
    Amazfit Helio Strap) as activities, without duplicates; re-syncs refresh
    them, deleted ones stay deleted, and a session you'd already logged by hand
    is merged into yours.
  - Strength sessions that overlap a Forge workout add **average and max heart
    rate** to that workout instead (shown in History and the workout summary).
  - Daily **steps, sleep, HRV and resting heart rate**, with 30-day charts.
  - Syncs on app open (at most every 15 minutes) and with *Sync now*.
- **Readiness check** on Today: sleep, HRV and resting heart rate against your
  own 4-week baseline. On a low day, quick workouts use 2 sets per exercise.
- A privacy page that Health Connect links to, explaining what Forge reads and
  that it stays on the phone.

### Changed
- Database version 5 (adds activity and daily-health tables and workout heart
  rate). Upgrades automatically and is covered by the migration test.
- JSON export format 5 includes activities and daily health data.

## [0.6.0-m5] - 2026-10-03 (Milestone 5: Progress & analytics)

### Added
- **Progress tab**
  - Stat tiles: workouts in the last 30 days, week streak, and this week's volume
    with a change vs last week.
  - Training calendar (17 weeks). The shading uses your own training's quartiles
    and a single colour, with a "Less → More" legend. Tap a day for its workouts
    and volume.
  - Sets per muscle in the last 7 days, against weekly targets.
  - Recent personal records.
  - Strength charts for your most-used exercises.
  - Links to Body and Progress photos.
- **Personal records** per exercise:
  - best estimated 1RM
  - heaviest load
  - most reps in a set
  - most volume in one workout
  - longest hold

  A record only counts as "new" if it beats every *earlier* workout, so
  first-timers aren't flooded.
- **Workout complete** lists the new PRs from that workout.
- **Exercise progress screen**
  - A line chart of e1RM (or best reps, or longest hold) per workout. Drag to read
    any point; values are always written out, never colour-only.
  - Record tiles, plus every workout as a table.
  - Reachable from exercise pages via "See progress chart & records".
- **Body screen:** charts and logs for bodyweight, body fat, waist, chest, hips,
  arm, thigh and neck, with delete + undo.
- **Progress photos**
  - Take them with the camera or add from the gallery.
  - Stored privately inside the app (scaled to 1600 px JPEG, upright via EXIF).
  - Label each Front, Side or Back.
  - Long-press two photos to compare them side by side.
  - Delete with undo.
- **Demo data:** 12 weeks of sample Full Body A/B training plus bodyweight,
  generated the same way every time. It's flagged in the database, so
  *Remove demo data* deletes only the demo rows. Available on the empty
  Progress tab and in Settings.
- **Tests:** PR maths and new-PR detection, streaks, calendar levels, the demo
  generator (exercises exist, strength trends up, same output every time), demo
  load/remove keeping real workouts, PRs in a session, and migration v1 → v4.

### Changed
- Database version 4 (automatic migration): photos table, plus demo flags on
  workouts and body entries.
- The JSON export (format 4) includes photo details. The image files themselves
  stay on the phone.

## [0.5.0-m4] - 2026-10-03 (Milestone 4: Smart suggestions)

### Added
- **Progression engine** (double progression), shown on every exercise in a
  workout:
  - Hit the top of the range on all sets at RPE ≤ 9 → the next weight you own
    (or the equipment's smallest step: dumbbells 2 kg, kettlebells 4 kg,
    barbell/bag 2.5 kg), starting again at the bottom of the range.
  - Inside the range → add a rep per set.
  - Missed the bottom once → stay at that weight.
  - Missed it twice at the same weight → drop about 10%.
  - Bodyweight moves → the next harder variation on the ladder, or the lightest
    vest/bag weight once you're at the top.
  - Timed holds progress in 5-second steps.
  - Each suggestion has **Why?**, **Apply** (fills your unticked sets, or swaps to
    the harder variation) and **Dismiss**.
- **Recovery model**
  - Every hard set's fatigue halves every 48 h (small muscles) or 72 h (legs,
    back, chest), and secondary muscles count half.
  - Weekly set targets: about 10 for big muscles, 6–8 for small ones.
  - Today can show the recovery bars for every muscle.
- **"What should I train?" on Today**
  - Choose 15/30/45/60 minutes.
  - **Best fit** scores your routines by how recovered and under-trained their
    muscles are. Your program's routine wins unless another is clearly better
    (25%+), and the card says why.
  - **Quick workout** picks the freshest, least-trained muscles and builds a
    session that fits the time (supersets for short ones).
    - For each muscle it uses the exercise you use most, else a go-to home
      exercise for your equipment.
- **Lighter-week (deload) hint** on Today, with its reasons:
  - 6+ weeks in a row of regular training
  - several lifts not improving over 3 sessions
  - average RPE up by 0.5+ to 8.5+
  - "Remind me in a week" dismisses it.
- **Tests:** 30+ new domain tests (every progression branch, step grid,
  recovery half-lives, routine choice vs plan, time-fitting, deload rules and
  dismissal) and Robolectric tests against real logged data (weight jump,
  bodyweight ladder, fatigue, quick workout start).

### Changed
- Starting a routine and starting a quick workout share one code path
  (`startPlanned`).

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
