# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Offline-first Android app around NASA's Astronomy Picture of the Day (APOD). Kotlin, Jetpack Compose (Material 3),
Hilt (KSP), Room, Retrofit + kotlinx.serialization, DataStore, WorkManager, Glance, Coil. Multi-module Gradle build
with convention plugins in the included `build-logic` build. `README.md` documents the design in depth.

## Commands

```bash
./gradlew test testDebugUnitTest      # all unit, Robolectric and Compose UI tests (no emulator) — what CI runs
./gradlew lint                        # Android Lint; warnings are errors, so any new warning fails the build
./gradlew detekt                      # static analysis (config/detekt/detekt.yml); not run by CI
./gradlew assembleDebug               # debug APK
./gradlew installDebug                # install on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest   # instrumented end-to-end test (needs a device)
```

Single module / single test:

```bash
./gradlew :data:nasa:testDebugUnitTest --tests "com.radustavila.nasaexplorer.data.nasa.repository.ApodRepositoryImplTest"
./gradlew :core:domain:test --tests "*SynchronizeApodsUseCaseTest"
```

Pure Kotlin modules (`:core:common`, `:core:model`, `:core:domain`, `:core:testing`) use `test`; Android modules use
`testDebugUnitTest`.

JDK notes: production bytecode targets Java 17, but test tasks are forced onto a JDK 21 toolchain (Robolectric needs it),
and the Gradle daemon JVM criteria in `gradle/gradle-daemon-jvm.properties` asks for JDK 25. If bare `./gradlew` fails
on JVM selection, point `JAVA_HOME` at a JDK 21 (e.g. the JetBrains Runtime 21 under `~/Library/Java/JavaVirtualMachines/`).
`local.properties` (copy from `local.properties.example`) must set `sdk.dir`. No API key is needed.

## Architecture

Clean Architecture, dependencies pointing inward:

- **Domain (pure Kotlin JVM, no Android on the classpath)**: `:core:common` (`AppError`, `DataResult`, dispatcher
  qualifiers, `TimeProvider`), `:core:model` (`Apod`, `MediaType`, `SyncState`, `ApodDeepLink`), `:core:domain` (use
  cases + contracts: `ApodRepository`, `SyncScheduler`, `NetworkMonitor`, `SyncMetadataRepository`,
  `UserPreferencesRepository`, `NewApodNotifier`, `LatestApodWidgetUpdater`).
- **Data**: `:data:nasa` (DTOs, Retrofit service, remote/local data sources, mappers, `ApodRepositoryImpl`,
  `ApodSyncWorker`, `WorkManagerSyncScheduler`) on top of the API-agnostic `:core:network`, `:core:database`,
  `:core:datastore`.
- **Presentation**: `:feature:*` (one screen each: Compose UI, ViewModel, immutable `UiState`, own type-safe
  `@Serializable` route + `NavGraphBuilder` extension), `:feature:widget` (Glance), `:core:notifications`,
  `:core:designsystem` (theme, shared components like `ApodCard`/`NasaAsyncImage`, `AppError`→`UiError` mapping).
- `:app` wires everything: nav host + bottom bar, Hilt bindings for dispatchers/clock/HTTP config/`BuildConfig.DEBUG`,
  WorkManager `Configuration.Provider` (default initializer removed in the manifest).

Boundary rules the code relies on:

- Feature modules never depend on each other or on `:data:nasa`; the `nasaexplorer.android.feature` convention plugin
  gives them only `:core:model/common/domain/designsystem` (+ `:core:testing` for tests). Only `:app` sees the full graph.
- DTOs and Room entities stay in the data layer; only domain models cross upward. Exceptions stay in the data layer;
  only `AppError` crosses upward (via `safeNetworkCall { }` / the local data source wrapper — both must rethrow
  `CancellationException`). The UI only sees `UiError`.
- Contracts live in `:core:domain`; Hilt bindings live next to their implementations (`NasaDataModule`,
  `NotificationsModule`, `WidgetModule`, …). Inject dispatchers via `@Dispatcher(AppDispatcher.IO)`, never hardcode
  `Dispatchers.IO`. The Glance widget reads use cases through a Hilt `@EntryPoint`.

### Data flow (offline-first)

- **Room is the single source of truth.** ViewModels observe DAO-backed Flows through use cases; the network only writes
  into Room. Nothing in the UI waits on a network call.
- `ApodEntity` is keyed by publication date (ISO-8601 TEXT, sorts chronologically). Syncs merge with
  `ApodDao.upsertPreservingLocalState` (insert-ignore, then partial `@Update` via `ApodRemoteContent`, which omits
  `is_favorite`) so a sync can never clobber favourites. Don't replace this with a plain `@Upsert`.
- Room schemas are exported to `core/database/schemas/` and checked in; bump the version, add a migration in
  `NasaDatabaseMigrations`, and cover it in `NasaDatabaseMigrationTest`.
- **All syncing goes through one unique WorkManager job** (`WorkManagerSyncScheduler` → `ApodSyncWorker` →
  `SynchronizeApodsUseCase`): app start, pull-to-refresh, debug button and the 12 h periodic sync share constraints,
  backoff and observability. Never sync from a Composable or ViewModel directly. The worker retries only when
  `AppError.isRetryable`; `SyncErrorCodec` round-trips the `AppError` through WorkManager `Data`, and `WorkInfo` is
  mapped to `SyncState`. After success, `AnnounceLatestApodUseCase` refreshes the widget and posts the opt-in new-entry
  notification.
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
`.android.room`, `nasaexplorer.jvm.library`, `nasaexplorer.detekt` (applied automatically by the application/library/jvm
plugins). Shared SDK levels, Java 17 target, lint settings and the JDK 21 test launcher are in `KotlinAndroid.kt`.
Versions come from `gradle/libs.versions.toml`; project dependencies use type-safe accessors (`projects.core.domain`).
A new module must be added to `settings.gradle.kts`.

## Testing

- Tests are written against shared fakes in `:core:testing` (`FakeApodRepository`, `FakeSyncScheduler`,
  `FakeTimeProvider`, …, plus `TestApods` builders and `MainDispatcherRule`), not mocks. When adding a domain contract,
  add its fake there.
- Robolectric provides the Android runtime for Room (real in-memory SQLite), WorkManager, notifications and Compose UI
  tests; network tests use MockWebServer; ViewModel flows use Turbine.
- `app/src/androidTest` holds a Hilt end-to-end test (`NasaTestRunner` → `HiltTestApplication`, data modules swapped in
  `di/TestDataModules.kt`).

## Roadmap

New features (F0–F10: Universe / Earth / Launches) are specified in a Claude Docs doc linked from `docs/ROADMAP.md`;
that doc is the source of truth. Read the feature's brief there before implementing; don't copy briefs or status into
the repo.

## Repo notes

- Main/integration branch is `develop`; CI (`.github/workflows/ci.yml`) runs unit tests, lint and `assembleDebug` on JDK 21.
- `LEARNING_GUIDE.md` is a Romanian step-by-step study log for guided review sessions; when the user is working through
  it, explain code rather than change it unless asked.
- Some README sections are stale (e.g. "only one database version" — there are now three schema versions and
  migrations; "no androidTest sources" — there is one). Trust the code.
