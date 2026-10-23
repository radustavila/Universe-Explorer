# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Offline-first Android app around NASA's Astronomy Picture of the Day (APOD). Kotlin, Jetpack Compose (Material 3),
Hilt (KSP), Room, Retrofit + kotlinx.serialization, DataStore, WorkManager, Glance, Coil. Multi-module Gradle build
with convention plugins in the included `build-logic` build. `README.md` documents the design in depth.

## Commands

```bash
./gradlew test testDebugUnitTest      # all unit, Robolectric and Compose UI tests (no emulator)
./gradlew verifyRoborazziDebug        # screenshot tests against the goldens in src/test/screenshots/ (CI runs this too)
./gradlew recordRoborazziDebug        # rewrite the goldens after an intended UI change; review the PNG diff before committing
./gradlew lint                        # Android Lint; warnings are errors, so any new warning fails the build
./gradlew detekt                      # static analysis (config/detekt/detekt.yml) against each module's detekt-baseline.xml
./gradlew detektBaseline              # rewrite every module's detekt-baseline.xml from the current findings
./gradlew assembleDebug               # debug APK
./gradlew installDebug                # install on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest   # instrumented end-to-end test (needs a device)
ANDROID_SERIAL=<serial> ./gradlew :app:generateBaselineProfile   # regenerate the checked-in baseline profile
ANDROID_SERIAL=<serial> ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   # cold-start benchmark
```

Single module / single test:

```bash
./gradlew :data:nasa:testDebugUnitTest --tests "com.radustavila.nasaexplorer.data.nasa.repository.ApodRepositoryImplTest"
./gradlew :core:domain:test --tests "*SynchronizeApodsUseCaseTest"
```

Pure Kotlin modules (`:core:common`, `:core:model`, `:core:domain`, `:core:testing`) use `test`; Android modules use
`testDebugUnitTest`.

Detekt fails on any finding not in the module's `detekt-baseline.xml`. Fix new findings rather than baselining them;
regenerate a baseline (`./gradlew :<module>:detektBaseline`) only after fixing or deleting baselined code, and review
the diff so it only shrinks.

JDK notes: production bytecode targets Java 17, but test tasks are forced onto a JDK 21 toolchain (Robolectric needs it),
and the Gradle daemon JVM criteria in `gradle/gradle-daemon-jvm.properties` asks for JDK 25. If bare `./gradlew` fails
on JVM selection, point `JAVA_HOME` at a JDK 21 (e.g. the JetBrains Runtime 21 under `~/Library/Java/JavaVirtualMachines/`).
`local.properties` (copy from `local.properties.example`) must set `sdk.dir`. No API key is needed.

## Architecture

Clean Architecture, dependencies pointing inward:

- **Domain (pure Kotlin JVM, no Android on the classpath)**: `:core:common` (`AppError`, `DataResult`, dispatcher
  qualifiers, `TimeProvider`), `:core:model` (`Apod`, `MediaType`, `SyncState`, `ApodDeepLink`, `FavoriteItem`/
  `FavoriteSource`, `SyncSource`), `:core:domain` (use cases + contracts: `ApodRepository`, `FavoritesRepository`,
  `SyncScheduler`, `NetworkMonitor`, `SyncMetadataRepository`, `UserPreferencesRepository`, `NewApodNotifier`,
  `LatestApodWidgetUpdater`).
- **Data**: `:data:nasa` (DTOs, Retrofit service, remote/local data sources, mappers, `ApodRepositoryImpl`,
  `ApodSyncWorker`, `WorkManagerSyncScheduler`, `ApodCacheEvictor`) on top of the API-agnostic `:core:network`
  (incl. per-host `RateLimitInterceptor`), `:core:database` (incl. the cross-source `favorite` table and
  `RoomFavoritesRepository`), `:core:datastore`, `:core:cache` (`CacheEvictor` + daily `CacheEvictionWorker`).
  `:data:launches` (Launch Library 2) keeps its own small `launches.db` (schema v5 migrates; older ones rebuild):
  the next 30 upcoming launches in `detailed` mode, refreshed from `ApodSyncWorker` and when the Launches screen
  opens at most once an hour (the card reads the first 3 of the same rows), and the last 20 past ones
  (`SyncSource.LAUNCHES_PAST`, only on screen open, at most every 6 h), one table told apart by `timeline`.
  Launch reminders (F27) live in the `launch_reminders` DataStore with one WorkManager job
  (`RemindOfLaunchesUseCase`): one `launches/{id}/` re-check about 60 min before NET, the notification 15 min
  before, re-armed after every refresh; `LaunchLibraryBudgetTest` keeps a heavy day under 15 calls an hour,
  shared with the card and the crew list. `:data:earthevents` (NASA EONET) keeps `earth_events.db`, refreshed by its own 3-hourly
  `EarthEventsSyncWorker` and when the events screen opens; the same database and worker hold the USGS
  earthquakes (F19: `2.5_week` + `significant_month`, `If-Modified-Since`, at most every 15 min,
  `SyncSource.EARTHQUAKES`), drawn on both Earth maps, and the opt-in strong-quake alert
  (`CheckEarthquakeAlertUseCase`, filtered on the device against the saved place). `:core:astro` (pure Kotlin) computes the sky offline:
  `AstronomyEngineSkyCalculator`, SGP4 passes, and the sky events calendar (`EventCalendar` behind
  `SkyEventCalendar`, `MeteorShowerTable`); `:data:skyevents` holds that calendar's reminders (DataStore + one
  WorkManager job, re-armed by `NasaExplorerApplication` whenever the saved place changes). `:data:forecast`
  (MET Norway cloud forecast, F11) keeps `forecast.db` per rounded place, honours `Expires`/`If-Modified-Since`,
  and runs its 3-hourly `StargazingAlertWorker` only while the clear-night alert is on; the score is
  `StargazingScorer` (`:core:domain`), the nights' darkness `AstronomyEngineDarkSkyCalculator` (`:core:astro`).
  `:data:news` (F14) parses NASA's RSS feeds and YouTube's Atom feed with one `FeedParser` into `news.db`, with no
  background job: the News screen and the Today card refresh it (3 h interval, one `SyncSource` per feed);
  stories open in a Custom Tab, videos in YouTube.
  `:data:comets` (F18) keeps `comets.db`: one daily SBDB query picks comets near perihelion, then Horizons gives
  each a geocentric ±60-day ephemeris (`SyncSource.COMETS`, daily `CometsSyncWorker` + screen open). Directions,
  rise/set and best time are computed on the device (`AstronomyEngineCometSkyCalculator`), so the place never
  leaves it. Comets reach the sky map (`ObserveSkyMapBodiesUseCase`) and their perihelia the sky events calendar
  (`ObserveCometPerihelionEventsUseCase`, merged in by the sky event use cases) only at magnitude ≤ 12
  (`ObserveCometMagnitudeLimitUseCase`); the Debug screen's "Show faint comets" switch lifts that cut-off.
  The Milky Way planner (F20, `:feature:milkyway`) is offline: `AstronomyEngineMilkyWayCalculator` (`:core:astro`,
  behind `MilkyWayCalculator`, bound in `:data:skyevents`) takes its nights from F11's `DarkSkyCalculator` and adds
  the core (Sgr A*, `GalacticCentre`), the Moon and the twilights (`Twilight`, shared with Sky tonight). The
  calendar lists the best night around each new Moon (`MilkyWayNightEvent`); the sky map opens on the core with
  `SkyMapRoute(showGalacticCentre = true)`.
  `:data:webb` (F24) keeps `webb.db`: STScI's JWST observing schedules page is read for its newest weekly
  file (names aren't predictable; the week before is read too while the newest hasn't started), parsed by
  the column-drift-tolerant `WebbScheduleParser` (`SyncSource.JWST_SCHEDULE`, daily `WebbScheduleSyncWorker`
  + screen open). `statusAt` (`:core:domain`) turns the week into `WebbNow` (current visit or slew/pause,
  up next, stale after 8 days); `:feature:webb`'s card sits in the Universe header, targets open
  `GallerySearchRoute`. Word it "scheduled": the plan is not a log.
  The daily quiz (F28, `:data:quiz` + `:feature:quiz`) is offline: `DailyQuizUseCase` takes 2 questions from the
  bundled bank (`quiz_bank_en.json` asset, data in its own words) and 3 from `@IntoSet QuizQuestionSource`s made
  from cached data, all seeded by the local date (the bank is walked 5 places a day, so no repeat within 30
  days); "Did you know" lines come from `@IntoSet FunFactSource`s. Questions the app words itself are a
  `QuizText.Phrase` (a `QuizPhrase` worded in `:feature:quiz`), never English in Kotlin. A feature adds its
  questions with its own source binding. Progress and the opt-in 18:00 reminder live in the backed-up
  `quiz_progress` DataStore.
  `:core:skymap` (pure Kotlin) is the sky map's maths and data: J2000 → horizontal frames, stereographic
  projection, label placement, and the bundled HYG stars + d3-celestial figures as Java resources, regenerated by
  `core/skymap/tools/build_sky_catalog.py` (licence notices next to the files; never Stellarium data, it is GPL).
  AR (F22, `:feature:skymap` `ar/`): in compass mode a CameraX preview fills the background (the `arCamera` slot of
  `SkyMapContent`, a stand-in in tests), the map switches to `SkyLens.RECTILINEAR` with the preview's field of view
  (`CameraFieldOfView`, from Camera2 focal length, sensor size and the crop) and the user's `SkyAlignment`. CAMERA is
  asked only when AR turns on; the picture is never recorded or saved.
- **Presentation**: `:feature:*` (one screen each: Compose UI, ViewModel, immutable `UiState`, own type-safe
  `@Serializable` route + `NavGraphBuilder` extension), `:feature:widget` (Glance), `:core:notifications`,
  `:core:designsystem` (theme, shared components like `ApodCard`/`NasaAsyncImage`, `AppError`→`UiError` mapping),
  `:core:map` (`SpaceMap` over MapLibre: GIBS imagery + MapLibre-free overlay API; the only module that sees MapLibre).
- `:app` wires everything: nav host (one nested graph per tab: Today · Universe · Earth · Favourites · More) +
  `NavigationSuiteScaffold` (bar on compact width, rail from medium width or a landscape phone, drawer from 1600dp),
  list-detail panes on expanded windows (`ListDetail.kt`, see below), the registries features add one line to (`TodayCards.kt`, `UniverseHub.kt`/`EarthHub.kt`/`MoreHub.kt`,
  `FavoriteNavigation.kt`), Hilt bindings for dispatchers/clock/HTTP config/`BuildConfig.DEBUG`,
  WorkManager `Configuration.Provider` (default initializer removed in the manifest).

Boundary rules the code relies on:

- Feature modules never depend on each other or on `:data:nasa`; the `nasaexplorer.android.feature` convention plugin
  gives them only `:core:model/common/domain/designsystem` (+ `:core:testing` for tests). Only `:app` sees the full graph.
- DTOs and Room entities stay in the data layer; only domain models cross upward. Exceptions stay in the data layer;
  only `AppError` crosses upward (via `safeNetworkCall { }` / the local data source wrapper — both must rethrow
  `CancellationException`). The UI only sees `UiError`.
- Window size: `:app` computes `WindowAdaptiveInfo` once and provides `LocalWindowAdaptiveInfo` (`:core:designsystem`);
  features read it there. Grids use `GridCells.Adaptive` with the widths in `designsystem/adaptive/AdaptiveLayout.kt`.
- List-detail: a list destination takes a `ListDetailHost` (`:core:designsystem`) from its nav extension; `:app`
  passes `listDetail(...)`, which opens items full screen while one pane fits and in a detail pane (its own nested
  `NavHost` with the feature's type-safe detail routes) on expanded windows. Pane graphs must not declare deep links.
- Contracts live in `:core:domain`; Hilt bindings live next to their implementations (`NasaDataModule`,
  `NotificationsModule`, `WidgetModule`, …). Inject dispatchers via `@Dispatcher(AppDispatcher.IO)`, never hardcode
  `Dispatchers.IO`. The Glance widget reads use cases through a Hilt `@EntryPoint`.
- When a feature needs another feature's UI (Settings shows Sky tonight's `ObserverLocationSection`), `:app` passes it
  in as a composable slot, as with `todayHeader`/`earthFooter`.
- Distances, speeds, temperatures, coordinates and clock times go through `UnitFormatter` (`rememberUnitFormatter()`,
  `formatLocalDateTime()`; `:core:designsystem` `units/`), which follows the user's metric/imperial and 12/24 h choice
  (`LocalUnitPreferences`, provided in `MainActivity`). No unit labels or English in Kotlin: store raw data, word it
  with string resources and plurals in the UI. Astronomical units (LD, light-years, Earth radii) are not converted.
- Notification switches use `rememberNotificationPermissionToggle` (`:core:designsystem` `notification/`).
- Settings measures storage through `@IntoSet StorageUsageSource` and "Clear cache" runs every `@IntoSet CacheCleaner`
  (contracts in `:core:domain` `storage/`); neither may touch favourites or their offline copies.
- Today's cards are the user's choice (Customize Today in `:feature:settings`, `today_cards` DataStore). A new card
  needs a `TodayCardId` (stored by name: never rename), its label in Customize Today and a line in `TodayCards.kt`.
- Backups include only the favourites database and the user-choice DataStores (`app/src/main/res/xml/`); a new
  DataStore holding user choices goes into both rule files and `BackupRulesTest`.

### Data flow (offline-first)

- **Room is the single source of truth.** ViewModels observe DAO-backed Flows through use cases; the network only writes
  into Room. Nothing in the UI waits on a network call.
- `ApodEntity` is keyed by publication date (ISO-8601 TEXT, sorts chronologically). Syncs merge with
  `ApodDao.upsertPreservingLocalState` (insert-ignore, then partial `@Update` via `ApodRemoteContent`, which omits
  the locally owned columns). Don't replace this with a plain `@Upsert`.
- Favourites of every source live in the `favorite` table keyed by `(source, item_id)` with a title/image snapshot
  (schema v4); `ApodEntity.is_favorite` is a dead legacy column. `ApodRepositoryImpl` sets `Apod.isFavorite` by
  combining reads with the APOD favourite ids. Cache evictors must never delete a favourite.
- `:core:offline` keeps an offline copy of each favourite's snapshot image in `filesDir/favorites/<source>/<id>.<ext>`
  (path and size on the `favorite` row, schema v7): `RoomFavoritesRepository` queues a `FavoriteImageDownloadWorker`
  on save and deletes the copy on remove, and `OfflineFavoriteImageInterceptor` makes the Coil loader serve the copy
  for the remote URL, so screens keep passing URLs. Not a cache: no evictor may touch it.
- Room schemas are exported to `core/database/schemas/` and checked in; bump the version, add a migration in
  `NasaDatabaseMigrations`, and cover it in `NasaDatabaseMigrationTest`.
- Every remote source records last success / last error / next allowed refresh per `SyncSource`; wrap refreshes in
  `RefreshSourceUseCase` (skips while not due, records the outcome). Rate limits are enforced per host by
  `RateLimitInterceptor` on the shared OkHttp client (429 → cooldown until `Retry-After`, local refusal →
  `AppError.RateLimited`); register a quota with `@IntoSet HostRateBudget`.
- Satellite passes (Sky tonight) are SGP4 on the device from CelesTrak elements in `sky.db`. The ISS refreshes at most
  twice a day (`SyncSource.CELESTRAK`); every other `SatelliteSet` the user picked at most once a day, one request
  each and never forced (`RefreshSatelliteSetsUseCase`), as CelesTrak asks. Many-satellite searches go through
  `SatellitePassPredictor.visiblePasses`, which skips daylight.
- **All APOD syncing goes through one unique WorkManager job** (`WorkManagerSyncScheduler` → `ApodSyncWorker` →
  `SynchronizeApodsUseCase`): app start, pull-to-refresh, debug button and the 12 h periodic sync share constraints,
  backoff and observability. Never sync from a Composable or ViewModel directly. The worker retries only when
  `AppError.isRetryable`; `SyncErrorCodec` round-trips the `AppError` through WorkManager `Data`, and `WorkInfo` is
  mapped to `SyncState`. After success, `AnnounceLatestApodUseCase` refreshes the widget and posts the opt-in new-entry
  notification.
- **Universal search is local-only.** `UniversalSearchUseCase` asks every `SearchableSource` (Hilt `@IntoSet`, one per
  data module, or a feature module when the names are the app's own words, as with `:feature:events`; fakes in
  `:core:testing`) concurrently, each with a time limit; a failing source only lands in
  `failedSources`. `SearchText` holds the shared matching/ranking/highlight rules. `:app`'s `SearchNavigation.kt` maps
  each `SearchTarget` to a route; online library search goes through the gallery (`GallerySearchRoute`).
- `apod.nasa.gov/apod/apYYMMDD.html` and `astropix.html` open the app (unverified intent filter); `MainActivity`
  rewrites them to `nasaexplorer://apod/{date}` and hands other pages back to the browser. Launcher shortcuts
  (`res/xml/shortcuts.xml`) use the features' `nasaexplorer://` deep links.
- **Pagination is by 7-day date windows**, not pages. `oldestRequestedDate` is persisted in DataStore; windows are
  clamped to `Apod.FIRST_PUBLICATION_DATE` (1995-06-16).

### NASA API

`api.nasa.gov/planetary/apod` now returns placeholders, so the app reads `https://science.nasa.gov/wp-json/wp/v2/apod-basic`
(base URL is a `BuildConfig` field in `data/nasa/build.gradle.kts`). `ApodBasicDto.toApodDto` adapts its HTML fields to
the `ApodDto` shape the rest of `:data:nasa` uses. The endpoint answers HTTP 400 when the window's end is past the latest
published entry (between UTC midnight and publication); the repository retries the window shifted back one day.
Malformed entries are dropped during mapping; a window with nothing usable becomes `AppError.EmptyResponse`.

## Build logic

Module `build.gradle.kts` files are short because `build-logic/convention` does the work. Plugin IDs:
`nasaexplorer.android.application`, `.android.library`, `.android.compose`, `.android.feature`, `.android.hilt`,
`.android.room`, `.android.roborazzi`, `.android.test`, `nasaexplorer.jvm.library`, `nasaexplorer.detekt` (applied automatically by the application/library/jvm
plugins). Shared SDK levels, Java 17 target, lint settings and the JDK 21 test launcher are in `KotlinAndroid.kt`.
Versions come from `gradle/libs.versions.toml`; project dependencies use type-safe accessors (`projects.core.domain`).
A new module must be added to `settings.gradle.kts`.

## Testing

- Tests are written against shared fakes in `:core:testing` (`FakeApodRepository`, `FakeFavoritesRepository`,
  `FakeSyncScheduler`, `FakeTimeProvider`, …, plus `TestApods` builders and `MainDispatcherRule`), not mocks.
  `FixtureServer` serves recorded JSON from `src/test/resources/fixtures/` through MockWebServer. When adding a domain contract,
  add its fake there.
- Robolectric provides the Android runtime for Room (real in-memory SQLite), WorkManager, notifications and Compose UI
  tests; network tests use MockWebServer; ViewModel flows use Turbine.
- Screenshot tests (`*ScreenshotTest`, Roborazzi on Robolectric native graphics) live in modules applying
  `nasaexplorer.android.roborazzi` and capture through `:core:screenshot-testing` (`captureScreen`/`captureComponent`
  in `ScreenshotDevices.COMPACT`/`EXPANDED` windows), which renders in `NasaTheme` (the app's only theme, dark) with
  fake Coil images and UTC clocks, so no network. Feed them fakes and fixed dates/instants. Goldens are stored at half
  size; the 1 % change threshold absorbs macOS/Linux anti-aliasing. If CI still differs, its `test-reports` artifact
  holds the actual and diff images under `build/outputs/roborazzi/`.
- `:baselineprofile` generates the release baseline/startup profile checked in under `app/src/release/generated`
  (regenerate after big changes to startup or Today) and benchmarks cold start. Today reports itself fully drawn
  (`ReportDrawnWhen` in `FeedScreen`) once cached rows are on screen; keep startup work off the main thread
  (`NasaExplorerApplication` registers periodic jobs on the IO dispatcher once per installed APK, OkHttp's startup
  initializer is removed). Debug builds run StrictMode (log only).
- `app/src/androidTest` holds a Hilt end-to-end test (`NasaTestRunner` → `HiltTestApplication`, data modules swapped in
  `di/TestDataModules.kt`).

## Roadmap

New features (F0–F18 built, F19–F28 next, F29 after the first release; M1–M4 must land before ads or premium) are specified in a Claude Docs doc linked from `docs/ROADMAP.md`;
that doc is the source of truth. Read the feature's brief there before implementing; don't copy briefs or status into
the repo.

## Workflow (the owner's standing rules)

1. **Prompt → questions only if needed.** Ask for context only when the request is unclear or a decision is genuinely
   the owner's; otherwise pick the sensible default, say so, and go.
2. **Implement** on a branch off `develop` (parallel sessions each in their own worktree, never editing the same files).
3. **Test yourself, end to end.** Locally, check only the modules you changed: their tests, `lint` and `detekt`
   (`./gradlew :feature:sky:testDebugUnitTest :feature:sky:lint :feature:sky:detekt`, `test` for pure Kotlin modules),
   `verifyRoborazziDebug` for those with screenshot tests (record and review the goldens after an intended UI
   change), plus `:app:testDebugUnitTest` when you touched `:app`. `./gradlew assembleDebug`, which the emulator
   install needs anyway, compiles every dependent module. CI runs the full suite and the R8 `assembleRelease` on the PR,
   so don't repeat them locally unless you changed build logic, Gradle files or R8 keep rules. Add `--max-workers=4`
   when several builds run at once; the 32 GB host runs out of memory otherwise. Then test on
   emulators: **up to 3 in parallel**, `adb -s <serial>` always, uninstall before installing another branch (Room
   versions differ). Never use `Pixel_9_Pro_XL_API_37` (fails to boot) or `Legacy_API_35` (holds the owner's work
   app). Cold-boot an emulator whose `system_server` died under load. The physical phone is used only when the owner
   says it is connected for testing: back up app data first and only `install -r` over it, never uninstall, and never
   run Gradle `connected*` tasks on it (they uninstall the app afterwards).
4. **Merge yourself when everything passed:** open a PR to `develop`, wait for CI to be green, merge it
   (`gh pr merge <n> --merge`), fast-forward the local `develop`, then remove the merged worktrees and their build
   output. Don't merge anything that failed or wasn't tested; report it instead.
5. **Report status:** what changed, how it was tested (tests, devices, numbers), PR links, and anything left open.

## Repo notes

- Main/integration branch is `develop`; CI (`.github/workflows/ci.yml`) runs three parallel jobs on JDK 21: unit and
  Roborazzi screenshot tests, lint + detekt, and `assembleRelease` (R8-minified, debug-signed). It runs for PRs and
  pushes to `develop`/`main` and skips changes that only touch Markdown or `docs/`. PRs read the Gradle caches that
  `develop` runs save.
- `LEARNING_GUIDE.md` is a Romanian step-by-step study log for guided review sessions; when the user is working through
  it, explain code rather than change it unless asked.
- Some README sections are stale (e.g. "only one database version" — there are now three schema versions and
  migrations; "no androidTest sources" — there is one). Trust the code.
