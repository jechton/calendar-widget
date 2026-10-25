# CLAUDE.md

Agenda: an Android home screen calendar widget (Kotlin, Compose, Glance). Same tooling as kimai-app.

## Build

```sh
./gradlew ktlintCheck testDebugUnitTest assembleDebug
```

Needs JDK 17+ and Android SDK 35 (`nix develop`, or set `ANDROID_HOME`). Outside Nix pass
`-Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2`. Maven Central can answer 429 in the cloud
sandbox: rerun with `--max-workers=2`. Commits and PR titles use Conventional Commits.

## Architecture

- `domain/EventFilter` holds all rules and has no Android imports. Order: drop hidden calendars and finished events,
  then dedupe. A copy that is finished under its own calendar's rule never wins a merge.
- Dedupe key: regex-stripped lowercase title + begin + end + all-day. Invalid regexes are ignored, and a pattern that
  strips the whole title falls back to the raw title.
- `CalendarRepository` reads `CalendarContract.Instances`. All-day instances come as UTC midnights and are converted to
  local midnights there, so the rest of the code works in one zone.
- `AgendaWidget.provideGlance` reads settings and calendars itself on every update. `RefreshWorker` redraws and
  schedules the next run at `EventFilter.nextChange` (earliest end + offset, or midnight).
- Widget settings are global (one `AgendaSettings` JSON in SharedPreferences), edited in `MainActivity`.

## Not done

Google Tasks (no public Android provider). Per-widget settings.
