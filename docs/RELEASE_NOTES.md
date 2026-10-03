## Forge 1.0.0

The first full release: a personal, free, offline-first fitness app for Android.

**Install:** download `app-release.apk` below and open it on your phone. Check it
against `SHA256SUMS.txt` if you like (`sha256sum -c SHA256SUMS.txt`).

**Coming from the debug app?** The release app (`app.forge.fitness`) is a separate app
from the debug one (`app.forge.fitness.debug`), with its own data. In the debug app:
Settings → Backup & restore → Export → *Backup (JSON)*. In the release app: Backup &
restore → *Restore from a file*. Then you can uninstall the debug app.

### What's in it
- **Workouts:** fast set logging with last time's numbers, rest timer, supersets,
  warm-ups, RPE, bodyweight-exercise loads, 876 exercises with images.
- **Routines & programs:** ready-made home and calisthenics programs or your own.
- **Smart suggestions:** progression, "what should I train?", lighter-week hints.
- **Progress:** strength charts, PRs, training calendar, body measurements, photos.
- **Live heart rate** from a Bluetooth strap (Amazfit Helio Strap with Heart Rate
  Push) during Forge workouts, with zones; no need to start anything in Zepp.
- **Sports & cardio**, plus sleep, HRV and steps from Health Connect for a daily
  readiness check. Workouts can be written back to Health Connect.
- **Food:** barcode scanning and search (Open Food Facts), label scanning, teen-safe
  targets.
- **Goals, habits and achievements**, with reminders.
- **Optional on-device AI** (Gemma 4 E2B): weekly summaries, plateau explanations,
  voice/text set logging. Runs entirely on the phone.
- **Widgets and shortcuts.**
- **Backup:** nightly to a Google Drive file you choose, daily snapshots on the
  phone, merge-restore, exact snapshot restore, CSV export.

No account, no ads, no analytics. Full details are in `CHANGELOG.md`.
