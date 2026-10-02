# Forge (working name)

A personal, free, offline-first Android fitness app: workout logging, exercise library,
routines and programs, rule-based progression suggestions, analytics, activities,
nutrition lite, goals, widgets, and optional Google Drive backup.

For personal use only. No accounts, no ads, no analytics, no paid APIs.

## Status

**Step 1: Plan drafted, waiting for approval.** See [`docs/PLAN.md`](docs/PLAN.md).

| Milestone | State |
|---|---|
| M0 Foundation | not started |
| M1 Workout logging | not started |
| M2 Exercise library | not started |
| M3 Routines & programs | not started |
| M4 Smart suggestions | not started |
| M5 Progress & analytics | not started |
| M6 Other activities | not started |
| M7 Nutrition lite | not started |
| M8 Goals & habits | not started |
| M9 Widgets & shortcuts | not started |
| M10 Backup & sync | not started |
| M11 Polish & performance | not started |

## Stack

Kotlin + Jetpack Compose, Room (SQLite), Hilt, WorkManager, Glance, Health Connect.
The reasons for this choice are in [`docs/PLAN.md`](docs/PLAN.md#1-stack-decision).

## Building, installing and resuming work

Setup instructions for Linux (Nobara), the Android SDK, and installing on a phone
will be added in M0.

To resume work at any time:

1. Read the "Status" table above.
2. Read [`CHANGELOG.md`](CHANGELOG.md).
3. Read the next milestone in [`docs/PLAN.md`](docs/PLAN.md#6-milestones).

## Credits

The exercise data comes from [free-exercise-db](https://github.com/yuhonas/free-exercise-db),
which is public domain (Unlicense).
