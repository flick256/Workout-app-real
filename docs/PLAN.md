# Forge: Plan (Step 1)

> Status: **draft, waiting for approval.** Nothing is built yet.
> "Forge" is a working name. It can be renamed any time before Milestone 10,
> because the Google OAuth client is tied to the package name.

---

## 1. Stack decision

| | Native Kotlin + Jetpack Compose | Flutter | Installable PWA |
|---|---|---|---|
| Background rest timer (notification + vibration while the screen is locked) | First-class | Needs plugins | Unreliable; browsers throttle background tabs |
| Home-screen widget and app shortcuts | First-class (Glance) | Needs native code anyway | Not possible |
| Health Connect (steps, active minutes) | Official SDK | Third-party plugin | Not possible |
| Local database | Room (SQLite) with compile-time-checked queries and tested migrations | sqflite/drift | IndexedDB, which the browser may evict |
| Build an APK on Linux | `./gradlew assembleDebug` | `flutter build apk` | No APK |

**Pick: native Kotlin + Jetpack Compose.**

- Several features you asked for (the background timer, widgets, Health Connect,
  shortcuts) are Android-system features. Native code reaches them directly;
  Flutter would add a bridge layer between the app and each one; a PWA can't
  do some of them at all.
- Room plus Kotlin coroutines is the most reliable offline-first data stack on
  Android, and it includes tested schema migrations.
- Everything builds from the command line on Linux with a JDK and the Android
  SDK. No Mac is needed.
- Compose with Baseline Profiles and R8 is the standard way to get a cold start
  under 2 s on mid-range phones.

---

## 2. Architecture

```
app/        Android app: Compose UI, ViewModels, Room, Health Connect, Drive, widgets, notifications
domain/     Pure Kotlin (no Android): progression rules, 1RM maths, "train today" engine,
            backup merge/conflict logic, CSV/JSON mappers. Its unit tests run in milliseconds.
```

- **Single activity, Compose UI, MVVM with one-way data flow.** Screens
  observe `StateFlow<UiState>` and send events to their ViewModel.
- **Room is the single source of truth.** The UI never holds unsaved data.
  Every completed set is written to the database the moment you tap ✓.
- **DI:** Hilt, using KSP. **Navigation:** type-safe Navigation Compose.
  **Preferences:** DataStore.
- **Background work:** WorkManager runs auto-backup, reminders, and the Health
  Connect sync.
- **Rest timer:** an exact alarm plus an ongoing notification that uses the
  system's countdown chronometer. The countdown keeps running and vibrates when
  it ends, even if the app is killed, and no long-running service drains the
  battery. (Exact alarms are restricted on the Play Store, but that doesn't
  matter for a sideloaded app.)
- **Libraries (all free and open source unless noted):**
  - Compose Material 3
  - Room
  - Hilt
  - kotlinx-serialization
  - Vico (charts)
  - Coil (images)
  - Glance (widget)
  - Health Connect client
  - CameraX with ML Kit bundled barcode scanning: free and on-device, but not open source
  - OkHttp for the Drive REST API and Open Food Facts
- **Builds:** `minSdk 26` (Android 8), latest `targetSdk`. JDK 17. A GitHub
  Actions workflow also builds the APK on every push, so you can download it
  straight to your phone without building locally.

---

## 3. Data model (Room)

Every table gets:

- a **UUID `id`**, so records can be merged across devices and backups without
  ID clashes
- `createdAt` and `updatedAt` (epoch ms)
- `deletedAt` (a soft delete), which gives undo and safe sync merges

Weights are stored in kg and shown in kg or lb.

**Training**
- `Exercise`: name, primaryMuscles, secondaryMuscles, equipment, category,
  mechanic, force, level, instructions, images, isCustom, sourceId,
  progressionChainId, archived
- `ProgressionChain` + `ProgressionStep`: e.g. incline push-up → knee →
  full → diamond → archer → one-arm
- `Routine`: name, notes, folder, sortOrder
- `RoutineExercise`: order, supersetGroup, targetSets, repMin, repMax,
  targetRpe, restSec, progressionRule, warmupSets
- `Program` → `ProgramWeek` → `ProgramDay` (→ routine), and `ScheduleEntry`
  (date or weekday → routine/program day)
- `WorkoutSession`: name, routineId?, startedAt, endedAt,
  status (ACTIVE/FINISHED/DISCARDED), notes, perceivedEffort, bodyweightKg
- `SessionExercise`: sessionId, exerciseId, order, supersetGroup, notes,
  restSec
- `SetEntry`: sessionExerciseId, order, type (WARMUP/WORKING/DROP/FAILURE),
  weightKg, reps, rpe, durationSec, distanceM, completedAt
- `PersonalRecord` (derived cache, can be rebuilt): exerciseId, kind
  (E1RM/WEIGHT/REPS_AT_WEIGHT/VOLUME), value, setId

**Other activity and body**
- `ActivitySession`: type (SPORT/CARDIO/MOBILITY), sport, start, durationMin,
  intensity 1–10, distanceM, notes, source (MANUAL/HEALTH_CONNECT),
  externalId
- `DailyActivity`: date, steps, activeMinutes (from Health Connect)
- `BodyMetric`: date, kind (WEIGHT/WAIST/CHEST/ARM/THIGH/BODY_FAT/…), value
- `ProgressPhoto`: date, pose, filePath (in app-private storage), note

**Nutrition**
- `Food`: name, brand, barcode, kcal/protein/carbs/fat per 100 g,
  servingGrams, source (CUSTOM/OFF)
- `FoodLogEntry`: date, meal, foodId, grams, plus a snapshot of kcal and
  protein, so editing a food doesn't rewrite your history
- `WaterEntry`: date, ml

**Goals and habits**
- `Goal`: kind, target, period (WEEK/…), active
- `Habit` + `HabitCheck`
- `Reminder`: kind, time, weekdays
- `Achievement`: key, unlockedAt

**Sync**
- `BackupRecord`: fileId, deviceId, createdAt, schemaVersion, checksum, and
  where it came from (local/drive)

**Integrity rules**
- Room schema JSON is exported to git. Every schema change ships with a
  migration and a migration test. Destructive fallback is never used.
- WAL mode is on, and writes happen in transactions.
- An unfinished `ACTIVE` session reopens automatically on the next launch.
- Before any restore or import, an automatic local snapshot is written.

---

## 4. Smart suggestions (rule-based and explainable)

Each suggestion shows a short **"Why?"** line, e.g. "You hit 3×12 @ RPE 7 last
two sessions, so +2.5 kg."

- **Progressive overload (double progression).** Each routine exercise has a
  rep range, e.g. 8–12.
  - If all working sets reach the top of the range at or below the target RPE,
    the suggestion is to add the smallest weight step your equipment allows.
  - For bodyweight exercises, the suggestion is to add reps, then move to the
    next step in the progression chain.
  - If you miss the bottom of the range twice, the suggestion is to hold the
    weight or drop 5–10%.
  - The recommended weight and reps are pre-filled into the set rows.
- **e1RM.** Epley and Brzycki estimates, adjusted for RPE by adding reps in
  reserve.
- **Deload hint.** It appears if one of these happens:
  - e1RM flat or falling over 3+ sessions on several lifts
  - average RPE creeping up at the same load
  - 5+ weeks of hard training without a light week

  The deload suggestion is about 50% fewer sets at the same weights for one
  week.
- **"What should I train today?"**
  - Each muscle gets a fatigue score: hard sets (and sport sessions mapped to
    muscles) with an exponential decay, roughly 48 h half-life for small
    muscles and 72 h for large ones.
  - Muscles are ranked by readiness × their weekly volume deficit against a
    target of about 10 sets per muscle per week.
  - The suggestion is the routine (or a generated one) that fits the minutes
    you have, assuming about 2.5–3 min per set including rest.
  - Options are offered at 15, 30, and 45 minutes.

---

## 5. Exercise library

- **free-exercise-db** (public domain, Unlicense) has 876 exercises and 1,746
  images. Only 111 of them are bodyweight-only, so I'll add a curated
  home/calisthenics pack of about 60 exercises with progression chains.
  Examples: pike, archer, and pseudo-planche push-ups; table rows; towel rows;
  Nordic curls; pistol progressions.
- The dataset is seeded into Room on first launch in one transaction, and a
  dataset version allows safe updates later.
- **Equipment profile in Settings.** It filters exercises and programs.
  Default: bodyweight, resistance bands, and a pair of dumbbells.

---

## 6. Milestones

Each milestone ends with an installable APK, updated README and CHANGELOG
entries, and exact build and install steps.

| # | Milestone | Contents |
|---|---|---|
| M0 | **Foundation** | Gradle project, design system (dark theme, type scale, spacing, haptics), nav shell, Room + DI setup, CI that builds the APK, and a Linux setup guide |
| M1 | **Workout logging** | Seed the exercise DB, start an empty or routine workout, set rows (weight/reps/RPE, set types, warm-ups), supersets, notes, inline "last time", background rest timer, auto-save on every set, resume after the app is killed, history list, **basic JSON export** (an early safety net) |
| M2 | **Exercise library** | Search and filter by muscle and equipment, detail page with instructions and images, custom exercises, equipment profile, calisthenics pack |
| M3 | **Routines & programs** | Build, duplicate, reorder (drag), and folder routines; programs with weekly schedule; prebuilt programs: Full Body 3×/wk (30 min), PPL (home), Calisthenics Progressions, Express 15-min |
| M4 | **Smart suggestions** | Progression engine, deload hints, "Train today" card, with unit tests |
| M5 | **Progress & analytics** | PRs, e1RM and strength charts, weekly volume per muscle, streaks, calendar heatmap, body weight and measurements, progress photos, **demo dataset** |
| M6 | **Other activities** | Sport, cardio, and mobility logging; Health Connect steps and active minutes; activities count toward fatigue |
| M7 | **Nutrition lite** | Calories, protein, water, food log, custom foods, Open Food Facts barcode lookup with local caching |
| M8 | **Goals & habits** | Weekly targets, reminders (WorkManager + notifications), achievements |
| M9 | **Widgets & shortcuts** | Glance home widget (today's suggestion, streak, start button) and launcher shortcuts |
| M10 | **Backup & sync** | Full JSON/CSV export and import, Google Drive appDataFolder backup and restore (manual and automatic), versioned backups, merge by UUID and `updatedAt`, pre-restore snapshot, conflict report, and a Google Cloud OAuth walkthrough |
| M11 | **Polish & performance** | Baseline profile, cold-start measurement, accessibility pass (TalkBack, font scaling, contrast), animations, empty states, undo everywhere |

**Testing throughout:**
- JVM unit tests for `domain/`: progression, e1RM, the recovery engine, and
  merge/conflict logic
- Room DAO and migration tests
- a few Compose UI tests for the logging flow

---

## 7. Decisions with real tradeoffs

**A. Exercise images**

1. **Bundle compressed WebP images in the APK (recommended).** Fully offline,
   which is your stated priority. The APK grows by about 20–30 MB (estimated;
   the source images are about 95 MB as JPEG). Size doesn't matter much for a
   sideloaded app.
2. Bundle text only and download images on demand, with a "download all"
   button. The APK stays around 10 MB, but images are missing until you've been
   online.

**B. How you build day to day**

1. **Android Studio on Nobara (recommended).** One installer gets you the SDK,
   the emulator, wireless debugging, and a debugger. GitHub Actions also builds
   every push as a backup option.
2. Command-line tools only, plus GitHub Actions. Lighter, but debugging is
   harder.

**C. Backup restore behaviour** (needed at M10; listed now so you can think about it)

1. **Merge by record (recommended).** Records are matched by UUID and the newer
   `updatedAt` wins. If both sides changed a record, both versions are kept and
   flagged. A snapshot is always taken first.
2. Full replace with an automatic pre-restore snapshot. Simpler, and you can
   undo it by restoring the snapshot.
