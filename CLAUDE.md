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
- **Tests**: JUnit 5, AssertJ, Mockito, MockBukkit, ArchUnit. 1509 of them, and none is
  deleted, weakened or skipped.

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

**The numbered documents this repository's javadoc cites do not exist yet.** 183 references
point at twelve files, `docs/01-architecture.md` through `docs/14-ui-style.md` and
`docs/permissions.md`, and none of them is in the repository or in its history. They are being
written from the code that cites them, most cited first. Until one exists, read the guard that
enforces the rule instead: the guards are the half of the documentation that cannot go stale.

## 7. When in doubt

- Do not guess a version or an API. Read the catalogue, or read the uxmLib source.
- This is the estate's oldest and largest plugin and it is a public product. A rule here is
  usually older than the one in a small plugin, and where the two differ the newer one is
  usually right: say so rather than copying the older one into a new place.
