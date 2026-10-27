# uxmEssentials

The all-in-one essentials suite for Paper 26.2: 35 modules, from homes, warps and economy to moderation, NPCs and holograms Modules: `api`, `bukkit-api`, `core`, `persistence-adapter`, `migration`, `bukkit-adapter`, `velocity-adapter`, `discord-adapter`, `redis-adapter`, `rest-adapter`. The Paper plugin is under `com.uxplima.uxmessentials`, built on uxmLib and shaded into one jar.

## Commands

```sh
./gradlew build           # format check, Error Prone, NullAway, tests, verifyJar
./gradlew spotlessApply   # format
./gradlew runServer       # local Paper server on the catalog's Minecraft version
```

## Ways to hurt yourself

- **Killing by pattern.** Never kill `java` by name; stop `runServer` with its console or a PID you started.
- **Shading what the server owns.** Adventure, Brigadier and slf4j come from Paper. `verifyJar` fails on them;
  do not exclude the check.
- **Blocking a server thread.** File and database IO goes through the uxmLib `Scheduler` async lane. Nothing
  uses `BukkitScheduler`, so Folia keeps working.
- **Breaking an operator's files.** A renamed or removed config key, command name or permission is a breaking
  change: `!` in the title and a `BREAKING CHANGE:` footer.

## Invariants

- Dependencies point inward: `bootstrap` wires `adapter`, `adapter` calls `application`, `application` uses
  `domain` and its ports. `domain` and `application` import no Bukkit or Paper type.
- Every package is `@NullMarked`.
- No text a player reads is written in Java: it is a message key with a line in every language file.
- `docs/internals/uxm-conventions.md`, `.editorconfig` and `.gitattributes` are synced from `UXPLIMA/.github`.

## Testing

There are no tests yet. New tests follow the repository standard: behavior through the public API, fakes for
ports, no assertion on structure. In-game behavior is checked with Craftwire on a development server.

## Pull requests

Titles are conventional commits with a scope: `core`, `command`, `config`, `menu`, `storage`, `message`,
`integration`, `build`, `ci`, `docs`, `release`. Before merging a release pull request, rewrite its
`CHANGELOG.md` entry for operators: Added, Changed, Fixed, Removed.
