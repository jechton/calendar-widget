# Android guide for this codebase

For someone who has never written an Android app. It explains the Android ideas this project uses and where each one
lives. Read it once, then open the files it names.

## 1. What an Android app is made of

An app is not one program with a `main`. It is a set of **components** that the system starts when it needs them.
Each is declared in `app/src/main/AndroidManifest.xml`, the app's table of contents. This app has four:

| Component | Class | What starts it |
|---|---|---|
| Application | `AgendaApp` | The system, once per process start, before anything else |
| Activity (a screen) | `MainActivity` | The launcher icon, or the widget asking for its settings |
| Broadcast receiver (widget) | `AgendaWidgetReceiver` | The system, when a widget is added, resized or updated |
| Broadcast receiver | `RefreshReceiver` | System events: calendar changed, clock changed, date rolled over |

The process can be killed at any time and restarted for a single event. So nothing important lives only in memory.
Settings go to disk (`SettingsStore`), and timed work goes to WorkManager (section 6).

Also in the manifest: `READ_CALENDAR`, the one **permission**. Android makes the user approve it at runtime.
`PermissionCard` in `MainActivity.kt` asks, and `CalendarRepository.hasPermission()` checks.

## 2. Two UI toolkits, one look

Both use the same idea: you write functions that describe the UI from the current data, and the framework calls them
again when the data changes. You never edit a view in place.

- **Jetpack Compose** draws the settings screen (`MainActivity.kt`, `ui/`). A function marked `@Composable` is a piece
  of UI. `Column`, `Row`, `Text`, `Switch` and so on are Compose pieces.
- **Jetpack Glance** draws the widget (`widget/AgendaWidget.kt`). It looks like Compose but is a separate set of
  `Column`, `Row`, `Text`. A widget runs in the launcher's process, so Android only accepts a frozen description
  (`RemoteViews`) of it. Glance builds that description for you. Compose pieces do not work inside a widget, and
  Glance pieces do not work in the app.

The settings screen shows a live preview by running the same widget code into a `RemoteViews` and putting that on
screen (`ui/WidgetPreview.kt`). So the preview is the real widget, not a copy.

**Compose terms you will see**

- `remember { ... }`: keep a value between redraws of that function.
- `mutableStateOf(x)`: a value that makes the UI redraw when it changes. `var draft by remember { mutableStateOf("") }`
  is how text fields keep their text.
- `Modifier`: a chain that sets size, padding, background, click handling. Order matters.
- `LazyColumn`: a scrolling list that only builds the rows on screen.

## 3. Where the data comes from

Android keeps all calendars in one shared database, the **Calendar Provider**. Google Calendar, Samsung Calendar and
others sync into it. This app reads it and never writes. That is why there is no sign-in.

You query it with a `ContentResolver`, not SQL directly. `data/CalendarRepository.kt` does this:

1. Build a URI (`CalendarContract.Instances.CONTENT_URI` plus start and end times).
2. Ask for a list of columns (the "projection").
3. Walk the returned `Cursor` row by row, reading each column by its position. The `Col` object names those positions.

"Instances" means each occurrence of an event, so a weekly meeting appears once per week in the window.

Database reads must not run on the main thread, or the screen freezes. Hence `withContext(Dispatchers.IO)`: a
**coroutine** (Kotlin's way to run slow work without blocking). `suspend fun` marks a function that may pause this way.

## 4. How a widget redraw works

```
 trigger (see section 6)
    |
    v
 AgendaWidget.provideGlance        widget/AgendaWidget.kt
    |  load(): CalendarRepository.snapshot()
    |            |-- events()            reads the Calendar Provider
    |            '-- EventFilter.apply()  drops hidden and finished events, merges duplicates
    |  EventFilter.groupByDay()          one DayRow per day
    v
 composables draw the rows (DaySection / ListDaySection, EventCard / ListEvent)
```

`domain/EventFilter.kt` is the part to read first if you want to understand the behavior. It has no Android code,
so `EventFilterTest` runs on a laptop in milliseconds. Most rules (dedupe, synonyms, hide-after-end) live there.

Taps on the widget are **Actions**. `actionStartActivity(Intent(...))` opens another screen. An **Intent** is a message
saying "show this" or "do this". For example, `viewEventAction` sends `ACTION_VIEW` for an event and Android opens it in
whichever calendar app the user has.

## 5. Settings flow

```
 Switch/Slider in the UI
    |  edit { it.copy(daysAhead = 5) }
    v
 SettingsViewModel.edit           SettingsViewModel.kt
    |-- SettingsStore.save()      writes JSON to SharedPreferences
    |-- redrawWidgets()           real widget on the home screen updates
    '-- state.update(...)         the settings screen redraws
```

- `AgendaSettings` (`domain/Model.kt`) is one immutable data class holding every setting. `copy(...)` makes a changed
  version. Adding a setting means adding a field with a default there, plus a control in `MainActivity.kt`.
  Old saved data still loads because missing fields take their default.
- A **ViewModel** keeps screen state alive across rotation. The screen reads `viewModel.state` (a `StateFlow`) and
  redraws when it changes. The screen never changes state itself, it calls `edit`.
- **SharedPreferences** is Android's tiny key-value file per app. Here it holds a single JSON string.

## 6. Keeping the widget fresh

A widget cannot run a loop. `work/Refresh.kt` uses **WorkManager**, which runs jobs later, survives reboots, and
lets the system batch them to save battery. Four triggers all end in `RefreshWorker`, which calls `redrawWidgets`:

| Job | Fires when |
|---|---|
| `watchCalendar` | The calendar database changes (a content trigger) |
| `enqueueBoundary` | An event is due to disappear (end time plus delay), or midnight |
| `ensurePeriodic` | Every 15 minutes, as a safety net |
| `RefreshReceiver` | Clock, time zone or date changes |

After each run the worker schedules the next boundary job, so the widget redraws exactly when the content changes.

## 7. Build files

- `build.gradle.kts` (root and `app/`): Gradle build scripts. They set the SDK versions, enable Compose, and list
  libraries. `minSdk = 26` means Android 8.0 and up.
- `gradle/libs.versions.toml`: one place for library versions.
- `app/src/main/res/`: resources. `drawable/` holds icons, `values/` holds strings and colors, `xml/` holds the widget's
  size and picker settings (`agenda_widget_info.xml`).
- Gradle generates a class `R` that names every resource, e.g. `R.drawable.ic_videocam`.
- Commands: `./gradlew testDebugUnitTest` runs the tests, `assembleDebug` builds the APK, `ktlintCheck` checks style.

## 8. Reading order

1. `domain/Model.kt`: the data. `CalEvent`, `AgendaSettings`.
2. `domain/EventFilter.kt` and `EventFilterTest.kt`: the rules.
3. `data/CalendarRepository.kt`: how events get in.
4. `widget/AgendaWidget.kt`: how they are drawn.
5. `work/Refresh.kt`: when it redraws.
6. `SettingsViewModel.kt`, then `MainActivity.kt` and `ui/`: the settings screen.

## 9. Glossary

- **APK**: the installable app file.
- **Context**: a handle to the running app, needed to read resources, files, and system services. Many functions take one.
- **Dp / sp**: density-independent pixels for sizes, scaled pixels for text. Use them instead of raw pixels.
- **Flow / StateFlow**: a stream of values over time. `StateFlow` always holds the latest one.
- **Glance**: the library for widgets in a Compose style.
- **Intent**: a message that starts a screen or asks the system to do something.
- **Material 3 / Material You**: Google's design system. "Dynamic color" takes the palette from the wallpaper.
- **ProGuard / R8**: shrinking tools. Disabled here (`isMinifyEnabled = false`).
- **RemoteViews**: the frozen UI description Android uses to show a widget from another process.
