# Leagues Tasks (RuneLite plugin)

A RuneLite sidebar plugin that lets a player browse a unified list of every
task from past OSRS Leagues events (Twisted, Shattered Relics, Trailblazer
Reloaded, Raging Echoes), filter by region/difficulty/activity type, save
one task per difficulty tier, and mark tasks complete against a background
log.

Beyond browsing and manual completion, the plugin also:

- **Autocompletes tasks the client can verify for itself** — quest
  completion, skill/level thresholds, achievement diary tiers, combat
  achievements, boss kill counts (from in-client varps, or Jagex
  hiscores for bosses like Skotizo that have no varp), and per-tier clue
  scroll casket counts — via a two-step
  "Autocomplete Tasks" flow: a preview showing
  what would be marked, then a second click to confirm and write it. Nothing
  is written until that confirm.
- **Syncs progress to an external website** you configure and run yourself
  (e.g. a self-hosted [Personal RuneScape
  Tracker](https://github.com/Ronovo/Personal-RuneScape-Tracker) instance),
  either from the sidebar's Sync card or an in-game button on the
  collection log interface (collection log capture is in-game only).
- **Scopes completions and saved tasks per account**, so an alt's levels can never
  overwrite a main's progress.

Tasks that appeared in more than one league are merged into a single row
(2,269 unique tasks). IDs are content-derived slugs of the task name
(`defeat-vardorvis`, `the-frozen-door`).

## Project layout

```
scripts/
  generate_icon.py      one-time placeholder toolbar icon generator
  requirements.txt      python deps for generate_icon.py
src/main/java/com/leaguestasks/
  LeaguesTasksPlugin.java     @PluginDescriptor entry point, wires the sidebar
  LeaguesTasksPanel.java      the one PluginPanel; CardLayout coordinator for
                              the plugin's screens
  LeaguesTasksConfig.java     shared connection settings
  ConfigKeys.java             every persisted key, in one place
  ui/                         per-card Swing views: StartCard, PickerCard,
                              ListCard, BrowseCard, DetailCard, SyncCard,
                              SyncSettingsCard, AutocompleteCard, InfoCard,
                              shared TaskDetailsView + FilterGroup, plus shared
                              Swing pieces (SwingComponents, CardPanel, MenuCard,
                              Navigator, TextUtil, SavedTaskPrompt). This list is
                              a summary, not every file.
  model/
    TaskEntry.java          POJO matching leagues_tasks.json rows
    TaskRepository.java     loads the JSON, filters, and picks randomly
    SavedTaskStore.java     one saved task per difficulty tier, per account
    CompletedTaskStore.java background completion log, scoped per account
    CompletedTaskRecord.java one {taskId, completedAt, source} entry --
                             source is MANUAL or AUTO
  sync/                   ProgressSyncClient (posts to the tracker),
                          TaskAutoDetector (preview/apply autocomplete),
                          AutoDetectRuleEvaluators (one check per RuleType),
                          AutoDetectGameState + its factory (the game state
                          a pass reads), QuestNameIndex (wiki quest title ->
                          RuneLite Quest),
                          CombatAchievementIndex (bundled CA tier metadata),
                          AchievementDiaryCatalog + CombatAchievementCatalog
                          (varbit reads for diary tiers and CA tasks),
                          CollectionLogCapture + CollectionLogCatalog
                          (reads the collection log from the game cache),
                          CollectionLogSyncButton (in-game sync button)
src/main/resources/com/leaguestasks/
  icon.png                 sidebar toolbar icon
  leagues_tasks.json       the compiled unified task list (bundled resource)
  combat_achievement_index.json  slug/tier metadata for CA autocomplete rules
  welcome.txt, about.txt, tracker.txt  in-plugin InfoCard text
src/test/java/com/leaguestasks/
  LeaguesTasksPluginLauncher.java   dev-only launcher for `./gradlew run`
  LeaguesTasksPluginWiringTest.java asserts the Guice object graph resolves
  InMemoryConfig.java, CountDiff.java  shared test helpers
  ui/, model/, sync/           the test suite -- data integrity, repository
                               filtering, stores/account scoping, auto-detect
                               rules, sync client, collection log catalog
  sync/MockSyncServerLauncher.java
                               dev-only local HTTP listener, see below
```

## Data: leagues_tasks.json

`src/main/resources/com/leaguestasks/leagues_tasks.json` is the shipped task
list — a checked-in data file, edited by hand. It was originally assembled by
scraping the OSRS Wiki "`<League>/Tasks`" pages and merging tasks that share a
normalized name; that scraper has been retired now that the list is stable.
One schema per row:

```json
{
  "id": "achieve-your-first-level-10",
  "region": "General",
  "difficulty": "easy",
  "points": 10,
  "name": "Achieve Your First Level 10",
  "description": "Reach level 10 in any skill (not including Hitpoints)",
  "requirements": null,
  "requirementItems": null,
  "activityType": "Skilling"
}
```

Notes on the data:

- **Twisted League** and **Shattered Relics League** tasks have no `points`
  and are not region-restricted (`region` is always `"General"`) — that
  reflects those leagues' actual design. Where a later league published a
  real region/points value for the same task, that value is used.
- **`activityType`** was a best-effort classification (there is no source
  column on the wiki for most leagues); an early build also carried a
  per-row confidence marker, since dropped after every low-confidence entry
  was reviewed by hand. **Combat Achievements** is its own type for tasks
  about the CA system, separate from **Combat/Magic** skill combat. **Boss**
  and **Minigame** are separate types (never a combined `Boss/Minigame`).
- **`autoDetect`** rules tell the plugin how to mark a task done from game
  state; most tasks have none.

When adding or editing tasks, keep `DataIntegrityTest` /
`AutoDetectRuleIntegrityTest` green (they pin task and rule counts) and update
their expected values deliberately.

The bundled `combat_achievement_index.json` is a slim copy of the tracker's
`combat-achievement-metadata.json` (slug, tier, monster), read at runtime by
`CombatAchievementIndex`.

## Prerequisites

- **JDK 11** (RuneLite currently builds against Java 11). Set `JAVA_HOME`
  and `PATH` before running Gradle. Example on Windows with Eclipse Temurin
  (adjust the path to the JDK 11 build you installed):
  ```
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-11.0.x-hotspot"
  $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
  ```
- Gradle wrapper (`gradlew`/`gradlew.bat`) is committed — no separate
  Gradle install needed.

## Running the dev client

```
./gradlew run
```

This launches a local RuneLite client (via
`src/test/java/.../LeaguesTasksPluginLauncher.java`) with the plugin
sideloaded. Confirm the toolbar icon appears, the panel opens, filters
populate, "Pick Random Task" returns a task consistent with the selected
filters, and Show My List / Check Full List / marking a task complete work.

## Syncing to a website

The Sync card (and the in-game collection log button) post to
`LeaguesTasksConfig.serverUrl()` plus a section-specific `/api/sync/*` path,
authenticated with a bearer `apiToken()`. Configure **Server URL** and
**API Token** under **Sync Settings** in the sidebar — the same two fields
also appear in RuneLite's own config panel for this plugin, under the
**Tracker website** section. **Server URL is a base URL** (e.g.
`http://localhost:4123` or `https://tracker.example`, no trailing path) — the
plugin appends the correct path for each button, so don't include
`/api/sync/...` yourself. Only `http` and `https` are accepted, and plain
`http://` is accepted **only for localhost** — a non-localhost `http://` URL
is rejected rather than send your token in cleartext, so use `https://` for a
remote tracker. Redirects are not followed.

The tracker must have the same value in `SYNC_TOKEN` as this plugin's API
token when you self-host for friends. On a public hosted tracker, sign in on
the website and paste your **personal plugin token** instead — there is no
shared house password. Collection log sync is the **Sync** button on the in-game Collection
Log interface; there is no sidebar Collection Log button.

| Button | Endpoint |
|---|---|
| Sync Everything | `/api/sync/leagues` |
| Sync Tasks | `/api/sync/tasks` |
| Sync Quests | `/api/sync/quests` |
| In-game Collection Log Sync | `/api/sync/collectionlog` |
| Sync Achievement Diary | `/api/sync/diaries` |
| Sync Combat Achievements | `/api/sync/combatachievements` |

Each section-specific sync button sends only that section's data.

### What a sync sends

The plugin only ever sends data outward — it never reads the tracker's
response back into your local progress. A sync posts:

- your character name and account hash
- your quest completion states
- your collection log (only when captured from the in-game interface)
- your achievement diary tier progress
- your combat achievement progress
- your list of completed tasks

Nothing is sent until you have entered a Server URL and API Token and pressed
a Sync button. There is no default or shared server.

To exercise the Sync button against something real without a live website:

```
./gradlew runMockSyncServer   # in one terminal -- logs every request, returns 200
./gradlew run                 # in another -- point Server URL at http://localhost:8090
```

## Tests

```
./gradlew test
```

The test suite covers data integrity (2,269 rows, unique IDs/names), task
repository filtering, saved/completed task stores (including per-account
scoping), auto-detect rule evaluation, the sync client (URL scheme, no
redirects), achievement diary and combat achievement catalog varbit discovery,
the collection log catalog's struct-param reads, and the full Guice wiring graph.

`DataIntegrityTest` and `AutoDetectRuleIntegrityTest` pin the task count
(per region) and the rule count (per type) on purpose; a failure lists which
region or type moved. If you edit `leagues_tasks.json`, update their expected
values in the same commit. See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Installing

The plugin is not on the Plugin Hub yet. Until it is, run it from source:

1. Install **JDK 11** and set `JAVA_HOME` (see [Prerequisites](#prerequisites)).
2. Download this repository (**Code → Download ZIP**, then extract) or clone it.
3. In that folder run `./gradlew run` (`gradlew.bat run` on Windows). This
   builds the plugin and opens a RuneLite client with it already loaded.
4. Log in and open **Leagues Tasks** from its sidebar icon.

**Jagex accounts:** the client that `./gradlew run` starts can't use the Jagex
Launcher's login directly. Follow RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide once: add `--insecure-write-credentials` under **Client arguments** in
`RuneLite (configure)`, then launch RuneLite through the Jagex Launcher. That
writes `.runelite/credentials.properties`, which the dev client then uses.
Anyone with that file can log in as you, so never share it. Delete it, or use
"End sessions" on your account page, to revoke it.

**Why not copy a jar into `sideloaded-plugins`?** RuneLite only loads that
folder in developer mode, and developer mode is off for any client started
from the RuneLite or Jagex launcher. A copied jar is silently ignored.

**From the Plugin Hub** (once accepted): in RuneLite open the wrench
(**Configuration**) → **Plugin Hub**, search for **Leagues Tasks**, click
**Install**, then open it from the new sidebar icon.

A companion landing page lives at
<https://ronovo.github.io/Leagues-Task-Plugin/>.

## Known to-dos

- **Toolbar icon:** `icon.png` is a generated placeholder.
- **Screenshots:** none yet for this README or the landing page. Drop one at
  `docs/screenshot.png` and uncomment the screenshot block in `docs/index.html`.
- **Collection log cache ids:** `CollectionLogCatalog` uses literal enum/param
  ids (tabs enum, name/page/item params, the slot remap enum). RuneLite's
  `gameval` API has no named constants for enums or params, so unlike the
  other catalogs these can't be resolved by name. Re-check them if the
  collection log interface changes.

## License

BSD 2-Clause — see [`LICENSE`](LICENSE).
