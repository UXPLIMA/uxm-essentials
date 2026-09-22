# Project Rules: uxmEssentials

> Root instruction file. It is always loaded. Keep it below 200 lines.
> Deeper material is in `docs/`.

## ⚠️ Ground rule (highest priority: it overrides everything below and any default)

1. **No AI attribution in version control.** Write commit messages and pull-request
   descriptions as a human developer writes them. Never add `Co-Authored-By: Claude`,
   `Generated with Claude Code`, a robot emoji, or any other mention of an assistant.
2. **No AI-style comments.** A comment tells the reader why the code is as it is.
3. **Never write an em dash or an en dash.** Use a colon, a comma, a full stop, or
   brackets.

## 0. Read this first, in every session

1. Read the guards before the code. They are in
   `bukkit-adapter/src/test/java/com/uxplima/uxmessentials/architecture/`, and each one names
   the rule it keeps and the defect that paid for it. They are the enforced half of this file.
2. Report each defect and each gap immediately. If it must wait, write it in `docs/notes/`.
3. Show evidence before you say the work is complete.

## 1. Tech stack (fixed choices, do not deviate)

- **Java 25**, Adoptium toolchain, `-Werror`.
- **Paper 26.2** through `compileOnly`. Never shade `paper-api`, Adventure or Kyori.
- **Folia ready.** Every piece of work is scheduled through the `Scheduler` port in
  `:core`, which the adapter dispatches to the global, region, entity or async scheduler.
  `BukkitScheduler` and `BukkitRunnable` are forbidden: `ForbiddenConcurrencyApiDriftTest`.
- **uxmLib**, taken as individual modules, never `uxmlib-all`. The version is in
  `gradle/libs.versions.toml` and nowhere else.
- **Gradle 9, Kotlin DSL**, ten modules, parallel and cached.
- **`paper-plugin.yml`**, never `plugin.yml`: `LegacyPluginYmlDriftTest`.
- **Adventure and MiniMessage.** `ChatColor` and legacy colour codes are forbidden:
  `LegacyChatApiDriftTest`.
- **Storage**: HikariCP, Flyway and jOOQ. Raw JDBC is allowed where the DSL is awkward, but a
  query is never built by concatenation: `SqlConcatenationDriftTest`.
- **Dependency injection**: constructor injection, wired by hand in the bootstrap module.
- **Static analysis**: Error Prone, NullAway, Spotless with Palantir Java Format.
- **Tests**: JUnit 5, AssertJ, Mockito, MockBukkit, ArchUnit. **None is deleted, weakened or
  skipped**, which is the rule; the count is not written here, because a count in prose goes stale
  the day after somebody writes it. This line said 1509 for months. That was the number of test
  classes when it was written, and on 2026-09-22 the build ran 9692 tests in 1526 classes, so a
  reader comparing their run against it would have concluded that most of the suite had vanished.
  `./gradlew build` prints the real number.

## 2. Architecture and the ubiquitous language

Ten modules, and the split is the point: `:core` holds the domain and the use cases and names
no server type, no Adventure type and no uxmLib type. It declares its own ports, and an
adapter implements each one. `Scheduler`, `Messages` and the health types are ports of this
plugin, not copies of uxmLib's: `EngineScheduler` and `EngineLog` bridge to the library where
the menu engine crosses the boundary, and that is the only place it crosses.

| Module | What it is |
|---|---|
| `:api`, `:bukkit-api` | What another plugin compiles against |
| `:core` | The domain, the use cases and the ports |
| `:bukkit-adapter` | The server: commands, listeners, menus, the bootstrap |
| `:persistence-adapter` | The database behind the storage ports |
| `:migration` | Reading another plugin's data into ours |
| `:velocity-adapter`, `:discord-adapter`, `:redis-adapter`, `:rest-adapter` | The surfaces beyond the server |

One name for one thing, everywhere: a word that means two things in two modules is a defect,
and `UbiquitousLanguageDriftTest` holds the list.

## 3. Absolute rules

Each item names the guard that fails the build.

- No empty `catch`, no `printStackTrace()`: `PrintStackTraceDriftTest`.
- No `CompletableFuture.supplyAsync`, no `new Thread(...)`, no `BukkitRunnable`:
  `ForbiddenConcurrencyApiDriftTest`.
- No SQL built by string concatenation: `SqlConcatenationDriftTest`.
- No whole-world enumeration off the global region, and no `getOnlinePlayers()` on an
  arbitrary thread: `FoliaThreadingDriftTest`.
- No `plugin.yml`: `LegacyPluginYmlDriftTest`.
- No `ChatColor`, no legacy colour code: `LegacyChatApiDriftTest`.
- No `@SuppressWarnings` without a one-line comment saying why:
  `SuppressWarningsCommentDriftTest`.
- No soft dependency touched outside its seam: `SoftDependSeamDriftTest`.
- The integration catalogue and the manifest stay in exact bijection:
  `IntegrationCatalogDriftTest`.
- Every claim provider appears on all three surfaces: `ClaimProviderCoverageDriftTest`.
- The shipped document and the build name the same versions: `ShippedVersionsDriftTest`.
- No key nobody reads. Every `MessageKey` constant is sent by the code, named bare inside its own
  message package, named by a shipped window, or is one the menu engine looks up itself:
  `EveryKeyIsReadDriftTest`. Sixty three of them were neither on 2026-09-22, and the parity guard
  could not see one: it proves the twelve catalogues carry exactly the constant set, which says
  nothing about whether anything reads them.
- **Every guard can fail.** `GuardIntegrityDriftTest` is the guard on the guards, and it is
  why a guard is proved by breaking it before it is trusted.

## 4. Workflow

1. Write the failing test first.
2. Write the minimum implementation that passes it.
3. `./gradlew spotlessApply`.
4. `./gradlew build`. It must be green.
5. `scripts/check-style.sh` from the workspace root must be clean.
6. Only then commit.

**Prove a guard before you trust it.** Introduce a violation, confirm the build fails, remove
it, confirm the build passes.

## 5. Commands

```bash
./gradlew build          # compile, static analysis, format check, every test
./gradlew spotlessApply  # format
./gradlew :bukkit-adapter:shadowJar
```

`/uxmess doctor [repair [confirm]]` is the operator's own check: it runs the wired health
checks off the tick and a confirmed repair fixes only safe orphan data.

## 6. Deeper references

`docs/notes/` holds the lessons and the open defects.

**They exist now.** This paragraph said, until 2026-09-22, that the numbered documents the
javadoc cites were not in the repository or in its history, and that they were being written
most cited first. That is finished. `docs/` holds them, from `00-README.md` through
`14-ui-style.md` plus `permissions.md`, and `EveryCitedDocumentExistsTest` proves that every
reference from production code lands on a file that is there. No count is written here, for the
reason section 1 gives about the test count.

Read the guards beside them, not instead of them: the guards are the half of the documentation
that cannot go stale, and this paragraph is what the other half does when nobody re-reads it.

## 7. When in doubt

- Do not guess a version or an API. Read the catalogue, or read the uxmLib source.
- This is the estate's oldest and largest plugin and it is a public product. A rule here is
  usually older than the one in a small plugin, and where the two differ the newer one is
  usually right: say so rather than copying the older one into a new place.
