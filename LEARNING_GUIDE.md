# Ghid de recapitulare Android – NASA Explorer

> Fișier de stare pentru sesiunile de învățare. Dacă dai `/clear`, spune lui Claude:
> **„Citește LEARNING_GUIDE.md și continuă de la primul pas nebifat."**

## Context

- Proiect: NASA Explorer, app Android care arată APOD (Astronomy Picture of the Day), offline-first.
- Utilizator: dezvoltator Android care face o recapitulare a programării Android. Codebase-ul e folosit ca exemplu de arhitectură modernă.
- Limba conversației: română.
- Stack: Kotlin, Jetpack Compose, Hilt, Room, Retrofit, DataStore, WorkManager, Coil, Navigation, Gradle Kotlin DSL cu convention plugins.
- Build: bare `./gradlew` nu merge, vezi memoria „Gradle JAVA_HOME override" (prefix cu path-ul jbr-21).

## Goal

Parcurgem proiectul pas cu pas, de jos în sus (fundație, apoi UI). Pentru fiecare pas:
1. Claude citește fișierele reale și explică cu `file:line`.
2. Explică **de ce** e aranjat/scris așa, nu doar ce face.
3. Răspunde la întrebări, apoi pune 2-3 întrebări de verificare.
4. La final, pasul se bifează aici și se notează ce a rămas neclar.

## Reguli de lucru

- Un pas = o sesiune scurtă. Nu sărim peste pași fără motiv.
- Codul din repo nu se modifică în timpul ghidării, decât dacă cere utilizatorul explicit.
- Bifează cu `[x]` când pasul e înțeles. Scrie în „Note" ce a rămas de revăzut.

## Structura modulelor (referință rapidă)

| Modul | Rol |
|---|---|
| `app` | Activity, NavHost, wiring Hilt, ImageLoader |
| `feature/feed`, `details`, `favorites`, `debug` | Ecrane: UI Compose + ViewModel + UiState |
| `core/domain` | Use case-uri + interfețe (repository, NetworkMonitor, SyncScheduler) |
| `core/model` | Modele de domeniu (`Apod`, `MediaType`, `SyncState`) |
| `core/common` | `DataResult`, `AppError`, dispatchers, `TimeProvider` |
| `core/network` | Retrofit/OkHttp, mapare erori, monitor conectivitate |
| `core/database` | Room: entity, DAO, migrări, schemas |
| `core/datastore` | Metadate de sync (DataStore) |
| `core/designsystem` | Temă + componente Compose reutilizabile |
| `core/testing` | Fake-uri, date de test, `MainDispatcherRule` |
| `data/nasa` | Implementare repository, DTO, mapper-e, sync worker |
| `build-logic/convention` | Convention plugins Gradle |

## Progres

### Pasul 1: Build și Gradle
- [x] `settings.gradle.kts`: module, `TYPESAFE_PROJECT_ACCESSORS`, `FAIL_ON_PROJECT_REPOS`
- [x] Version catalog (`gradle/libs.versions.toml`)
- [x] `build-logic/convention`: de ce există, ce plugin face ce (Application, Library, Feature, Compose, Hilt, Room, Detekt, JvmLibrary)
- [x] Cum arată un `build.gradle.kts` de modul după ce convention plugins fac munca grea

### Pasul 2: Arhitectura pe module
- [x] Graful de dependențe și regula „cine poate depinde de cine"
- [x] De ce `domain` e Kotlin pur, fără Android
- [x] De ce `feature` nu depinde de `data`
- [x] Beneficii: timp de build, izolare, teste

### Pasul 3: Model și domain
- [x] `Apod`, `MediaType`, `SyncState`
- [x] `DataResult` și `AppError` (de ce nu aruncăm excepții prin straturi)
- [x] Interfețe: `ApodRepository`, `SyncMetadataRepository`, `NetworkMonitor`, `SyncScheduler`
- [x] Use case-uri: ce justifică existența lor, când sunt prea mult
- [x] `AppDispatchers`, `TimeProvider` (injectare pentru testabilitate)

### Pasul 4: Network
- [x] `NasaApodService` (Retrofit) și DTO-uri
- [x] `RetrofitFactory`, `NetworkConfiguration`
- [x] `NetworkErrorMapper`: din excepții HTTP/IO în `AppError`
- [x] `ApodRemoteDataSource` ca abstracție peste Retrofit
- [x] `AndroidNetworkMonitor` (ConnectivityManager în `Flow`)

### Pasul 5: Database
- [x] `ApodEntity`, `ApodDao`, `NasaDatabase`
- [x] Migrări și `schemas/*.json`, de ce le păstrăm în git
- [x] `ApodLocalDataSource` / `RoomApodLocalDataSource`
- [x] Teste Room și test de migrare

### Pasul 6: Repository (offline-first)
- [x] `ApodRepositoryImpl`: Room = single source of truth
- [x] Mapper-e DTO → Entity → Model
- [x] Fluxul: UI observă DB, refresh scrie în DB
- [x] Paginare spre date mai vechi (`LoadOlderApodsUseCase`)
- [x] Favorite

### Pasul 7: Sync în fundal
- [x] `ApodSyncWorker` (WorkManager, `HiltWorker`)
- [x] `WorkManagerSyncScheduler` din spatele interfeței `SyncScheduler`
- [x] `SyncErrorCodec`: de ce codăm erori în output data
- [x] `SyncMetadataDataSource` (DataStore) și `ObserveSyncStateUseCase`

### Pasul 8: Injectare de dependențe (Hilt)
- [x] `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`
- [x] Module: `@Binds` vs `@Provides`, scope-uri (`SingletonComponent`)
- [x] Cum e împărțit DI pe module (`DatabaseModule`, `NetworkModule`, `NasaDataModule`, `AppModule`)
- [x] Înlocuirea modulelor în teste (`TestDataModules`, `NasaTestRunner`)

### Pasul 9: UI
- [x] `core/designsystem`: temă, tipografie, componente (`ApodCard`, `StateContent`, `NasaAsyncImage`)
- [x] `UiError` și afișarea erorilor
- [x] Pattern ViewModel + `UiState` + `StateFlow` (feature `debug` ca exemplu simplu)
- [x] Feed: listă, pull to refresh, paginare
- [x] Details: imagine, video player, dialog full-size
- [x] Favorites
- [x] Navigation: `NasaNavHost`, `TopLevelDestination`, `*Navigation.kt` per feature
- [x] `MainActivity`, `NasaApp`, `NasaExplorerApplication`, `ImageLoaderModule`, `VideoFrameFetcher`

### Pasul 10: Teste
- [ ] Fake-uri vs mock-uri (`core/testing`)
- [ ] `MainDispatcherRule` și testarea coroutines/Flow
- [ ] Teste de ViewModel și use case
- [ ] Teste de repository, worker, mapper
- [ ] `androidTest` end-to-end
- [ ] CI (`.github/workflows/ci.yml`) și Detekt (`config/detekt/detekt.yml`)

## Note și puncte de revăzut

_(se completează pe parcurs: concepte neclare, întrebări rămase, idei de exersat)_

-

## Jurnal sesiuni

| Data | Ce am parcurs | Observații |
|---|---|---|
| 2026-10-01 | Plan creat | Începem cu Pasul 1 |
| 2026-10-01 | Pasul 1 complet | `includeBuild` vs `buildSrc` (buildSrc nu există în proiect, e doar alternativa). Convention plugins fac 3 lucruri: aplică plugin-uri, configurează `android {}`, adaugă dependențe. Întrebările de verificare nu au fost date, de revăzut dacă e nevoie: `api` vs `implementation`, de ce feature nu vede `data`. |
| 2026-10-02 | Pașii 2 și 3 bifați | Marcați de utilizator ca înțeleși. Următorul: Pasul 4 (Network). |
| 2026-10-03 | Pasul 4 bifat | Marcat de utilizator ca înțeles. Următorul: Pasul 5 (Database). |
| 2026-10-03 | Pasul 5 bifat | Întrebările de verificare au fost răspunse de Claude (utilizatorul a cerut): upsert fără REPLACE, pași pentru migrarea v4, rethrow CancellationException. Următorul: Pasul 6 (Repository). |
| 2026-10-03 | Pasul 6 bifat | Întrebări răspunse de Claude: cursor DataStore vs MIN(date), avans cursor doar la succes, isInFeed=false. Următorul: Pasul 7 (Sync). |
| 2026-10-03 | Pasul 7 bifat | Marcat de utilizator ca înțeles. Următorul: Pasul 8 (Hilt). |
| 2026-10-03 | Pasul 8 bifat | Întrebări răspunse de Claude: `@Binds` vs `@Provides` (abstract vs corp), fake `@Singleton` în teste (aceeași instanță pentru app și test), DAO cache-uit de Room, `Lazy<ImageLoader>`. Următorul: Pasul 9 (UI). |
| 2026-10-04 | Pasul 9: designsystem, UiError, pattern ViewModel bifate | Răspunsuri date de Claude: `@StringRes` în `UiError`, culori `internal`, state hoisting în `ApodCard`, `stateIn(WhileSubscribed)` vs lifecycle, `initialValue` vs ultima valoare (`replayExpirationMillis`), `Screen`/`Content` split. Următorul: 9.3 Feed. |
| 2026-10-04 | Pasul 9.3 Feed bifat | Răspunsuri date de Claude: `localState` + `combine` vs un singur `MutableStateFlow`, protecție dublu `onLoadMore` (`distinctUntilChanged` + gardă `isLoadingMore`, cursă doar multi-thread), banner/snackbar doar cu conținut. Următorul: 9.4 Details. |
| 2026-10-04 | Pasul 9.4 Details bifat | Răspunsuri date de Claude: `sealed` vs `data class` UiState, player în `remember` (resursă) vs `rememberSaveable` (stare), `configChanges` în manifest (rotirea nu recreează Activity-ul), `status` derivat din player deci `remember`. Următorul: 9.5 Favorites. |
| 2026-10-04 | Pasul 9.5 Favorites bifat | Răspunsuri date de Claude: fără update optimist (single source of truth, Room local rapid), `isLoading` inițial `true` (evită flash de EmptyState), `onErrorDismissed` după `showSnackbar` (cheia `LaunchedEffect` + dedup `StateFlow`). Următorul: 9.6 Navigation. |
| 2026-10-04 | Pasul 9.6 Navigation bifat | Răspunsuri date de Claude: callback `onApodClick` vs `navController` în feature (evită feature → feature), `saveState`/`restoreState` (scroll + stare tab), `hierarchy` în `isOn` (robust la graf-uri nested), bara ascunsă pe Details (nu e în `TopLevelDestination`). Următorul: 9.7 MainActivity/NasaApp/Application/ImageLoader. |
| 2026-10-04 | Pasul 9.7 bifat | Răspunsuri date de Claude: scos `WorkManagerInitializer` (altfel init automat cu factory implicit, `@HiltWorker` nu se poate construi), `dagger.Lazy` pentru ImageLoader/OkHttpClient (startup mai rapid, graf creat la prima imagine), `RuntimeException` → `IOException` în fetcher (Coil tratează IOException ca eroare de load, nu crash). Pasul 9 complet. Următorul: Pasul 10 (Teste). |
