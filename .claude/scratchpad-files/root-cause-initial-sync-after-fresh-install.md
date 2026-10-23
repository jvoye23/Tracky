# Root Cause: Keine Projekte nach erstem Login (frischer Install)

**Datum:** 2026-08-10
**Branch:** `23-Fixing-Loading-initial-data-after-fresh-install`
**Status:** Analyse abgeschlossen und im Code verifiziert. Kein Fix implementiert.

---

## 1. Symptom

Nach einer frischen Installation der App:

- Der User meldet sich zum ersten Mal an → der Project-Overview-Screen bleibt leer, obwohl im Backend Projekte existieren.
- Der User schließt die App und öffnet sie erneut → die Projekte werden geladen und korrekt angezeigt.

Reproduzierbar bei jedem frischen Install (App-Daten gelöscht bzw. neu installiert).

---

## 2. Root Cause

`ProjectSyncManager` ist der **einzige** Ort in der gesamten App, der jemals `fetchProjects()` aufruft — also der einzige Pfad, über den Backend-Daten in die Room-DB gelangen. Sein Trigger feuert auf einem frischen Install genau einmal: **bevor der User eingeloggt ist.**

`composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/domain/sync/ProjectSyncManager.kt:36-55`

```kotlin
fun start() {
    if (started) return
    started = true

    combine(
        connectivityObserver.isConnected.debounce(1.seconds),
        appLifecycleObserver.isInForeground
    ) { online, foreground -> online && foreground }
        .distinctUntilChanged()          // ← Gate-Wert ändert sich beim Login nicht
        .filter { it }
        .onEach {
            syncRepository.syncPendingOperations()
            val now = timeProvider.nowInstant
            if (now - lastPull > 5.minutes) {
                syncRepository.fetchProjects()
                lastPull = now           // ← wird auch nach Fehlschlag gesetzt
            }
        }
        .launchIn(applicationScope)
}
```

Das Gate kennt den **Login-Zustand nicht**. Es kombiniert nur Konnektivität und Foreground-Status — zwei Werte, die sich durch eine Anmeldung nicht ändern.

### Zwei Defekte in einem Satz

1. **Das Sync-Gate kennt den Login-Zustand nicht.** Die einzige Trigger-Flanke (`false → true`) ist bereits verbraucht, wenn der User sich anmeldet. Wegen `.distinctUntilChanged()` emittiert das Gate danach nie wieder.
2. **`lastPull` wird unabhängig vom Erfolg gesetzt.** Der `EmptyResult<DataError>`-Rückgabewert von `fetchProjects()` wird nie ausgewertet. Der fehlgeschlagene Pre-Login-Pull verbrennt dadurch zusätzlich das 5-Minuten-Retry-Fenster.

---

## 3. Ablauf Schritt für Schritt

### Erster App-Start (frischer Install)

| # | Was passiert | Beleg |
|---|---|---|
| 1 | `TrackyApplication.onCreate()` ruft `get<ProjectSyncManager>().start()` auf — noch bevor irgendein Screen sichtbar ist. | `androidApp/.../TrackyApplication.kt:25` |
| 2 | Das Gate `online && foreground` wird sofort `true`, während der Login-Screen offen ist und in DataStore kein `AuthInfo` liegt. | `ProjectSyncManager.kt:40-45` |
| 3 | `fetchProjects()` läuft **unauthentifiziert**. `loadTokens` liefert `null`, der Server antwortet 401. | `HttpClientFactory.kt:54-61` |
| 4 | `lastPull = now` wird **trotzdem** gestempelt. Das 5-Minuten-Fenster ist verbrannt, obwohl nichts geladen wurde. | `ProjectSyncManager.kt:51` |
| 5 | Der User loggt sich ein. `LoginViewModel` schreibt die Session und navigiert weiter — **mehr nicht**. In der gesamten Auth-Kette gibt es keinen `fetchProjects()`-Aufruf. | `LoginViewModel.kt:87-90` |
| 6 | Weder Konnektivität noch Foreground haben sich geändert → `.distinctUntilChanged()` blockt jede weitere Emission. Der SyncManager läuft nie wieder an. | `ProjectSyncManager.kt:44` |
| 7 | Der Overview-Screen liest ausschließlich lokal: `getProjects()` → `OfflineFirstProjectRepository` → `RoomLocalProjectDataSource` → `ProjectDao.getProjectsWithTasks()`. **Room ist leer → leere Liste.** | `ProjectOverviewViewModel.kt:366`, `OfflineFirstProjectRepository.kt:66` |

### Zweiter App-Start

Das Token liegt jetzt in DataStore. `start()` läuft in einem **neuen Prozess** erneut (`started`-Flag und `lastPull` sind Instanz-State und damit zurückgesetzt), das Gate feuert wieder — diesmal authentifiziert. Der Pull gelingt, `upsertProjects` schreibt nach Room, der Room-Flow emittiert, Projekte erscheinen.

Das erklärt das Symptom exakt.

---

## 4. Code-Belege

### Der Trigger läuft vor dem Login

`androidApp/src/main/kotlin/com/jvcs/androidapp/app/TrackyApplication.kt:25`
```kotlin
get<ProjectSyncManager>().start()
```

Zusätzlich als `single(createdAtStart = true)` registriert — `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/data/di/CoreDataModule.kt:55-63`.

iOS-Pendant: `composeApp/src/iosMain/kotlin/com/jvcs/tracky/core/data/di/KoinHelper.kt:21` — gleicher Ablauf, gleicher Defekt.

### Login triggert keinen Sync

`composeApp/src/commonMain/kotlin/com/jvcs/tracky/features/auth/presentation/login/LoginViewModel.kt:87-90`
```kotlin
).onSuccess { authInfo ->
    sessionStorage.set(authInfo)
    _state.update { it.copy(isLoggingIn = false) }
    eventChannel.send(LoginEvent.Success)
}
```
Identisch in den Google- (`:108-112`) und Apple-Pfaden (`:129-133`). Auch `NavigationRoot.kt:65-68` (`onLoginSuccess`) löst nichts aus.

### Kein Token vor dem Login

`composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/data/networking/HttpClientFactory.kt:54-61`
```kotlin
loadTokens {
    sessionStorage.observeAuthInfo().firstOrNull()?.let {
        BearerTokens(accessToken = it.accessToken, refreshToken = it.refreshToken)
    }
}
```

### Der UI-Datenpfad ist rein lokal

`composeApp/src/commonMain/kotlin/com/jvcs/tracky/features/project_tracker/data/OfflineFirstProjectRepository.kt:55-66`
```kotlin
// PULL: network → Room. Der Room-Flow, den die UI beobachtet, emittiert automatisch.
override suspend fun fetchProjects(): EmptyResult<DataError> { ... }

override fun getProjects(): Flow<List<Project>> {
    return localProjectDataSource.getProjects()   // Room only
}
```

Wichtig: `fetchProjects()` ist auf `SyncRepository` deklariert (`core/domain/sync/SyncRepository.kt:9`), **nicht** auf `ProjectRepository`. Kein ViewModel kann es also überhaupt aufrufen. Beide Interfaces werden von derselben `single`-Instanz bedient (`CoreDataModule.kt:44-53`, `binds arrayOf(...)`).

Kette bis zur UI:
`ProjectOverviewScreenRoot` → `collectAsStateWithLifecycle()` → `.onStart { getProjects() }` → `ProjectRepository.getProjects()` → `RoomLocalProjectDataSource.getProjects()` → `ProjectDao.getProjectsWithTasks()`
(`ProjectOverviewViewModel.kt:366-399`, `ProjectDao.kt:36-38`)

### Der Worker pullt nie

`composeApp/src/androidMain/kotlin/com/jvcs/tracky/core/data/sync/SyncWorker.kt:23-33`
```kotlin
override suspend fun doWork(): Result {
    return try {
        syncRepository.syncPendingOperations()   // push only — nie fetchProjects()
        Result.success()
    } ...
}
```

---

## 5. Ausgeschlossene Hypothesen

**WorkManager springt nicht ein.** Beide Requests in `AndroidSyncScheduler.kt` sind periodisch (`schedulePeriodicSync` 6h, `schedulePeriodicSyncOnStart` 15min, beide `ExistingPeriodicWorkPolicy.KEEP`). Periodische Arbeit läuft auf Android frühestens am Ende des ersten Intervalls. Es wird nirgends ein `OneTimeWorkRequest` enqueued — die `OneTimeWorkRequestBuilder`/`ExistingWorkPolicy`-Imports dort sind ungenutzte Überbleibsel. Und selbst wenn der Worker liefe, pullt er nicht (siehe oben).

**Der Ktor-Token-Cache ist nicht die Primärursache.** `BearerAuthProvider` cached das Ergebnis von `loadTokens` im Speicher, aber `KtorAuthService` ruft bei Login-/Register-/Social-Erfolg `clearTokenCache()` auf (`KtorAuthService.kt:28-33`, `:43`, `:55`, `:63`, `:71`). Relevant bleibt die **Reihenfolge**: `clearTokenCache()` läuft innerhalb von `login()`, also *bevor* `LoginViewModel` das neue Token via `sessionStorage.set(authInfo)` schreibt. Solange ein späterer Sync-Trigger erst *nach* dem DataStore-Write feuert, liest `loadTokens` korrekt neu. Beim Fix ist darauf zu achten.

**Die Race-Condition beim Session-Write ist nicht ursächlich.** `sessionStorage.set()` ist suspending und kehrt erst nach abgeschlossenem DataStore-Write zurück (`DataStoreSessionStorage.kt:35-42`); das `LoginEvent.Success` wird erst danach gesendet.

---

## 6. Nebenbefunde im Working Tree (nicht gefixt)

Beides stammt aus den unfertigen `localUser`-Änderungen, nicht aus dem Sync-Bug:

**`composeApp/src/jvmTest/.../ProjectOverviewViewModelTest.kt:70` kompiliert nicht.** `composeApp:jvmTest` ist damit aktuell rot:
```kotlin
val viewModel = ProjectOverviewViewModel(
    projectRepository = repository,
    timeManager = TimeManager(CoroutineScope(dispatcher)),
    timeProvider = FakeTimeProvider(),,     // ← doppeltes Komma
)                                           // ← sessionStorage + authService fehlen
```

**Doppelte Zuweisung von `state.projects`** — `ProjectOverviewViewModel.kt:60-73`. Das neue `combine` überschreibt `projects` mit der **rohen, ungefilterten** Liste (`projects.map { it.toProjectUi() }`), nachdem `getProjects()` dort bereits die gefilterte, sortierte und mit Timer-Info angereicherte Liste hineingeschrieben hat. Der Room-Flow wird dadurch zweimal collected. Die gerenderten Listen (`pinnedProjects` / `otherProjects`) kommen weiterhin aus `_state` und bleiben korrekt — nur das exponierte `state.projects` ist jetzt die ungefilterte Liste.

---

## 7. Lösungsrichtung (Notiz, noch nicht umgesetzt)

1. **Login-Zustand ins Sync-Gate aufnehmen.** `ProjectSyncManager` bekommt `SessionStorage` und kombiniert einen dritten Flow, z. B. `observeAuthInfo().map { it?.user?.id }.distinctUntilChanged()`. Auf frischem Install ist das Gate beim Cold Start `false` (kein unnützer 401-Pull), und nach dem Login kippt es `false → true` → Pull mit gültigem Token.
   - Auf `userId` statt auf `!= null` zu mappen hat den Vorteil, dass ein Token-Refresh (schreibt neues `AuthInfo`) das Gate nicht unnötig neu triggert, ein **User-Wechsel** aber schon.
2. **`lastPull` nur bei Erfolg stempeln** — `fetchProjects()` gibt `EmptyResult<DataError>` zurück, also `.onSuccess { lastPull = now }`.
3. **Pull-Fenster bei User-Wechsel ignorieren**, damit ein Re-Login nicht an der 5-Minuten-Sperre hängen bleibt.
4. **Testbarkeit beachten:** `ConnectivityObserver` und `AppLifecycleObserver` sind `expect class`, keine Interfaces — sie lassen sich nicht direkt faken. Für einen Unit-Test des Gates muss die Kombinationslogik in eine eigene Funktion extrahiert werden, die die drei `Flow`s als Parameter nimmt. Ein Test für `ProjectSyncManager` existiert bisher nicht.
5. **Offene Produktfrage:** Soll der Overview während des ersten Pulls einen Ladeindikator zeigen, statt kurz den Empty-State („keine Projekte") zu blitzen?
