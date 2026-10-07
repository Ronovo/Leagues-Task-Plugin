# Contributing

## Prerequisites

- **JDK 11** — RuneLite builds against Java 11. Set `JAVA_HOME` / `PATH` to a JDK 11 build
  before running Gradle.
- The Gradle wrapper (`gradlew` / `gradlew.bat`) is committed; no separate Gradle install.

## Common tasks

| Command | What it does |
|---|---|
| `./gradlew test` | Run the unit tests. |
| `./gradlew build` | Compile, run tests, and produce the plugin jar under `build/libs/`. (RuneLite only loads a jar from `sideloaded-plugins` in developer mode, so use `./gradlew run` to try it.) |
| `./gradlew run` | Launch a local RuneLite client with the plugin sideloaded. |
| `./gradlew runMockSyncServer` | Local HTTP listener that logs every sync POST and returns 200, for exercising the Sync buttons without a real tracker. |

## The data file: `src/main/resources/com/leaguestasks/leagues_tasks.json`

One object per row; the schema is documented in the README. It is hand-edited.

`DataIntegrityTest` and `AutoDetectRuleIntegrityTest` **pin exact counts** — the total task
count (2,269) broken down per region, and the per-`RuleType` auto-detect rule breakdown (673
rules across 18 types). A failure lists each region or rule type whose count moved.
This is deliberate: it catches an accidental edit to a large hand-maintained file. If you
change the data on purpose, update the expected values in those two tests **in the same
commit**, and say why in the commit message.

## Auto-detect rules

Rule types live in one enum, `com.leaguestasks.model.RuleType`. Each rule in the data file
has a `"type"` matching an enum constant; the per-type check is in
`com.leaguestasks.sync.AutoDetectRuleEvaluators`. Adding a rule type means: a new `RuleType`
constant, an evaluator entry, and updated integrity-test counts.

## Style

Match the surrounding code. There is no Checkstyle gate; the Plugin Hub verification only
requires that `build.gradle` is **tab-indented**.

## Submitting

Open a PR against `main`. For Plugin Hub submission the repo must be public and the
submission points at a specific 40-character commit hash.
