# Forge (working name)

A personal, free, offline-first Android fitness app: workout logging, exercise library,
routines and programs, rule-based progression suggestions, analytics, activities,
nutrition lite, goals, widgets, and optional Google Drive backup.

For personal use only. No accounts, no ads, no analytics, no paid APIs.

- **Plan:** [`docs/PLAN.md`](docs/PLAN.md) (stack, architecture, data model, milestones)
- **What changed:** [`CHANGELOG.md`](CHANGELOG.md)

## Status

| Milestone | State |
|---|---|
| M0 Foundation | done |
| M1 Workout logging | done (v0.2.0-m1) |
| M2 Exercise library + bodyweight loads | done (v0.3.1-m2) |
| M3 Routines & programs | done (v0.4.1-m3) |
| M4 Smart suggestions | done (v0.5.0-m4) |
| M5 Progress & analytics | done (v0.6.0-m5) |
| M6 Other activities + Health Connect | **done** (v0.7.0-m6) |
| M7 Nutrition lite | next |
| M8 Goals & habits | not started |
| M9 Widgets & shortcuts | not started |
| M10 Backup & sync | not started |
| M11 Polish & performance | not started |

---

## Using Forge (quick tour)

- **Start:** Today → **Start workout** → **Add exercises** (search, filter by muscle,
  or show only "My equipment"). Tap several exercises, then **Add**.
- **Routines & programs:** Today → *Routines & programs*.
  - *Browse ready-made programs*: Full Body Home, Home Push/Pull/Legs, Calisthenics
    Foundations, Express 15. Adding one copies its routines (so you can edit them)
    and makes it your **active program**.
  - Today then shows **"Today: Full Body B"** with a Start button, or tells you it's a
    rest day and when the next session is.
  - Routines rotate in order. Miss a day and the next one simply waits.
  - Change training days in the program card's ⋮ menu.
  - Build your own with *New routine*: drag ☰ to reorder, and set sets, rep range
    and rest per exercise. Supersets live in each exercise's ⋮ menu.
  - Duplicate, file in folders, or delete (with undo) from the routine's ⋮ menu.
  - Finished an ad-hoc workout you liked? Open it and tap 🔖 **Save as routine**.
- **Smart suggestions** (rule-based; each one explains itself under **Why?**)
  - **In a workout**, each exercise shows what to aim for, based on your last
    sessions:
    - "Go up to 22 kg" once you hit the top of your rep range on every set
      (RPE ≤ 9).
    - "Aim for 10 reps" while you're inside the range.
    - "Drop to 26 kg" after missing the bottom twice at the same weight.
    - "Ready for Diamond Push-Up" for bodyweight moves.
    - **Apply** fills your empty sets.
  - **Today → What should I train?**: pick 15/30/45/60 min.
    - Forge shows which muscles are recovered and under their weekly target.
    - It picks your best-fitting routine (your program's routine wins unless
      something else is clearly better for your recovery).
    - Or it builds a quick workout for the freshest muscles and starts it.
  - **Lighter-week hint**: after 6+ weeks straight, stalled lifts plus rising RPE,
    or several stalled lifts. Dismiss it for a week.
- **Progress tab**
  - Headline numbers: workouts in the last 30 days, week streak, and this week's
    volume vs last week.
  - A **training calendar** (darker = more volume; tap a day).
  - **Sets per muscle** this week against a target line.
  - **Recent personal records.**
  - **Strength charts** for your most-used exercises: drag across a chart to read
    any workout, with records and an every-workout table below.
  - **Body:** weight and measurement charts and logs.
  - **Progress photos:** camera or gallery; private to the app. Long-press two to
    compare side by side.
  - Finishing a workout lists any **new PRs**.
  - Nothing logged yet? *Load demo data* (on Progress, or in Settings) adds 12
    weeks of sample training. *Remove demo data* deletes exactly that.
- **Sports, runs and other activities**
  - Today → *Log a sport, run or other activity*, or History → *Log a sport or
    cardio session*. 28 sports and activities (football, AFL, basketball, netball,
    running, cycling, swimming, yoga…), with duration, effort 1–10 and distance.
  - They show up in History next to your workouts, and they **count towards
    recovery**: a hard 90-minute game tires your legs like about 7 hard sets, so
    "What should I train?" steers you to upper body the next day. The log screen
    shows exactly how much it counts.
- **Your watch or strap (Health Connect)**: Settings → *Health & watch*.
  - Works with an **Amazfit Helio Strap** through Zepp, or anything that shares
    with Health Connect. Setup steps for Zepp are on that screen and below.
  - Imports sessions your strap recorded (runs, football, …) as activities,
    without duplicates. A *Strength* session on the strap that overlaps a Forge
    workout just adds its **heart rate** to that workout.
  - Reads **sleep, HRV, resting heart rate and steps**. Each morning Forge
    compares them with *your own* last 4 weeks to give a **readiness** check on
    Today. On a low day, quick workouts drop to 2 sets per exercise.
  - Read-only, and the data never leaves your phone. Syncs whenever you open Forge
    (at most every 15 minutes) or with *Sync now*.
- **Log a set:** type weight and reps, then tap ✓.
  - Grey numbers in empty boxes are **last time's**. Tapping ✓ on an empty row uses
    them, so repeating last session is one tap per set.
  - Tap the set number to make it a warm-up, drop set or failure set, or to delete it.
  - Tap **RPE** to record how hard the set felt.
- **Rest timer:** starts automatically when you tick a set.
  - It keeps counting in the notification with the screen off. Use **+30s** or
    **Skip** there.
  - It buzzes when rest is over.
  - Set the rest time per exercise in the exercise's ⋮ menu, or set the default in
    Settings.
- **Exercise ⋮ menu:** **Easier / Harder variation** (moves along the progression
  ladder, e.g. push-up → diamond push-up), add warm-up sets (calculated from your working weight),
  notes, rest time, superset with the next exercise, move up or down, remove.
- **Owned weights:** in Settings → *Weights I own*, enter what you actually have
  (e.g. bag: 10, 15 kg).
  - They appear as quick-pick chips while logging.
  - Warm-up sets snap to them.
- **Bodyweight exercises:** add your bodyweight (and height) in Settings → Body.
  - Push-ups, pull-ups, dips, squats and 50+ other moves then show
    **≈ how many kg you actually lift**, e.g. a push-up is about 64% of your
    bodyweight and a pull-up about 95%.
  - The numbers come from published research, and each exercise's page says how
    sure we are.
  - A vest or bag goes in the "+kg" column and is counted correctly for the
    movement.
- **Exercise pages:** tap any exercise for:
  - an animated demo
  - instructions
  - its bodyweight load
  - where it sits in a **progression** (e.g. wall push-up → … → one-arm
    push-up)
  - your history with estimated 1-rep max
- **Your own exercises:** Exercises tab → **New exercise**.
  - Choose how it's logged and, for bodyweight moves, the closest movement type,
    so the load is calculated.
  - Archive ones you no longer use. "My exercises" shows them all, archived
    included.
- **Never lose a workout:** every tap is saved instantly.
  - If the app is closed or your phone dies, the workout is still there; tap
    **Resume** on the Today tab.
  - Settings → **Export data (JSON)** saves a full copy anywhere you like.

### Connecting an Amazfit Helio Strap (one-off, ~2 minutes)

1. Install **Zepp** and pair the strap if you haven't already.
2. In Zepp: **Profile → Add accounts** (on some versions *Profile → Settings →
   Data sharing*) → **Health Connect**. Turn sharing on and allow everything it asks
   to write.
3. In Forge: **Settings → Health & watch → Connect**. Allow the data you want
   (all of it is fine; Forge only reads).
4. Wear the strap to bed. Sleep and HRV are what power the readiness check, and it
   needs ~3 nights before it can compare against "your normal".
5. When you lift, also start a **Strength** workout on the strap. Forge then puts
   your heart rate on the Forge workout instead of importing a duplicate.

Health Connect only lets a newly connected app read the last 30 days, so older
strap history won't come across.

---

## Getting the app onto your phone

There are two ways to get the APK. Start with A: it needs no setup on your PC.

### A. Download the APK that GitHub builds for you (easiest)

Every time code is pushed, GitHub Actions builds the app.

1. On your phone or PC, open the repo on GitHub, then tap **Actions** → **Build APK**
   and pick the newest run that has a green tick.
2. Scroll down to **Artifacts** and download **forge-debug-apk**. It downloads as a
   `.zip` file.
3. Unzip it. Inside is `app-debug.apk`.
4. Install it (see "Installing an APK on your S25+" below).

### B. Build it yourself on Nobara

You only need to do the one-time setup below once.

**One-time setup**

1. Install Java 21, which Gradle runs on. Open a terminal and run:
   ```bash
   sudo dnf install java-21-openjdk-devel git
   java -version   # should say 21
   ```
2. **Install Android Studio.** Download the Linux `.tar.gz` from
   <https://developer.android.com/studio>, then extract it and start it:
   ```bash
   mkdir -p ~/apps && tar -xzf ~/Downloads/android-studio-*-linux.tar.gz -C ~/apps
   ~/apps/android-studio/bin/studio.sh
   ```
   - Use the official tarball rather than the Flatpak. The Flatpak sandbox often
     can't see your phone over USB.
   - In the setup wizard, choose **Standard**. That installs the Android SDK into
     `~/Android/Sdk`.
   - To add a launcher icon, use **Tools → Create Desktop Entry**.
3. **Tell your terminal where the SDK is.** Add these lines to the end of `~/.bashrc`,
   then open a new terminal:
   ```bash
   export ANDROID_HOME="$HOME/Android/Sdk"
   export PATH="$PATH:$ANDROID_HOME/platform-tools"
   ```
4. **Get the code:**
   ```bash
   git clone https://github.com/flick256/Workout-app-real.git
   cd Workout-app-real
   git checkout claude/personal-fitness-app-u1j8wr
   ```

**Build**

- **In Android Studio:** **File → Open** the `Workout-app-real` folder, wait for
  "Gradle sync" to finish, plug in your phone, and press the green ▶ Run button.
  This builds and installs in one step.
- **From the terminal:**
  ```bash
  ./gradlew assembleDebug        # first build downloads things; allow ~5–10 min
  # The APK ends up in: app/build/outputs/apk/debug/app-debug.apk
  ./gradlew test                 # runs all unit tests
  ```

### Installing an APK on your S25+

**Option 1: copy the file and tap it**

1. Copy `app-debug.apk` to the phone, or download it there directly.
2. Open it from **My Files**.
3. Android will ask you to allow installs from that app (My Files or Chrome). Allow
   it, then go back and tap **Install**.
4. If **Auto Blocker** is on (Settings → Security and privacy → Auto Blocker), it
   blocks sideloading. Turn it off while you install, and turn it back on afterwards
   if you like.

**Option 2: USB or Wi-Fi with adb**

1. Enable Developer options on the phone:
   - Go to **Settings → About phone → Software information** and tap
     **Build number** 7 times.
   - Then go to **Settings → Developer options** and turn on **USB debugging**.
     For cable-free installs, turn on **Wireless debugging** too.
2. Plug the phone in, tap **Allow** on the phone, then run:
   ```bash
   adb devices                                   # your phone should be listed
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   The `-r` flag replaces the existing app and **keeps your data**.

### About signing keys (important, read once)

Android only lets an update install over an existing app if both are signed with
the **same key**.

**Debug builds**
- They use `app/debug.keystore`, which is committed to the repo on purpose.
- Because of that, APKs you build on your PC and APKs from GitHub Actions can be
  installed over each other without losing data.
- Debug builds are called **"Forge"** with the package `app.forge.fitness.debug`.
- They are a bit slower than release builds. That's fine for testing.

**Release builds (optional)**
- These are faster: R8-optimized, with a cold start under 2 s. Use one as your
  everyday app later on.
- Release builds need your own private key.
- Keep that key file **and its passwords** somewhere safe, such as a password
  manager and a USB stick. If you lose it, you can't update the release app without
  uninstalling it, which loses data unless you've made a backup.

To create the key (one time):

```bash
keytool -genkeypair -v -keystore ~/forge-release.jks -alias forge \
  -keyalg RSA -keysize 4096 -validity 36500
```

To build releases on your PC, create `keystore.properties` in the project folder.
Git ignores it.

```properties
storeFile=/home/YOU/forge-release.jks
storePassword=...
keyAlias=forge
keyPassword=...
```

Then run `./gradlew assembleRelease`.

To have GitHub build releases too, open the repo's **Settings → Secrets and
variables → Actions** and add four secrets:

| Secret | Value |
|---|---|
| `FORGE_KEYSTORE_BASE64` | output of `base64 -w0 ~/forge-release.jks` |
| `FORGE_KEYSTORE_PASSWORD` | your store password |
| `FORGE_KEY_ALIAS` | `forge` |
| `FORGE_KEY_PASSWORD` | your key password |

After that, every run also uploads a **forge-release-apk** artifact.

> If this repo is public, anyone could sign an APK with the committed debug key.
> It can only replace the *debug* app, and only if someone gets you to install it,
> so the risk is small. Making the repo private removes it entirely.

---

## Project layout

```
app/                Android app (Kotlin + Jetpack Compose)
  src/main/kotlin/app/forge/fitness/
    MainActivity.kt   single activity: splash, edge-to-edge, theme
    ui/theme/         design system: colours, type, spacing, shapes
    ui/components/    shared widgets (BigButton, ForgeCard, EmptyState, haptics…)
    ui/navigation/    bottom-nav shell and routes
    feature/…         one package per screen (today, history, exercises, progress, settings)
    data/db/          Room database, entities, converters
    data/prefs/       settings stored with DataStore
    di/               Hilt dependency-injection modules
  schemas/          exported Room schemas (committed; used for migration tests)
domain/             pure Kotlin: maths and rules with fast unit tests
  bodyweight/       bodyweight load model (profiles, sources, matcher)
  suggest/          progression, recovery, train-today and deload rules
  program/          prebuilt programs, rotation schedule, time estimates
  dataset/          free-exercise-db parser + Forge home pack and progressions
tools/              fetch_exercise_images.py: rebuilds the bundled WebP images
docs/PLAN.md        the plan
```

## Resuming work

1. Check the **Status** table above and the top of [`CHANGELOG.md`](CHANGELOG.md).
2. The next milestone's scope is in [`docs/PLAN.md`](docs/PLAN.md#6-milestones).
3. Run `./gradlew test` to confirm everything still passes before changing anything.

## Credits

The exercise data comes from [free-exercise-db](https://github.com/yuhonas/free-exercise-db),
which is public domain (Unlicense).
