# Development

The build comes from `uxm-gradle`: `uxm.paper-plugin` adds the Paper API, Error Prone, NullAway, Spotless,
the shaded jar with every bundled library relocated under `com.uxplima.uxmessentials.libs`, and `verifyJar`,
which fails when the jar carries what the server owns or lacks the plugin's own classes. Versions come
from the `uxm` catalog.

## Build and run

```sh
./gradlew build       # everything CI runs
./gradlew runServer   # a local server with the plugin, in run/
./gradlew :core:generateReference   # rewrite the generated pages under docs/user
```

The command, permission and placeholder pages under `docs/user/` (`commands.md`, `permissions-*.md`,
`placeholders*.md`) are generated from `CommandSpecCatalog`, `PermissionCatalog` and `PlaceholderCatalog`. The build
fails while a committed page differs from what the catalogues render; change the catalogue, run
`:core:generateReference` and commit both. A new command also needs its `describe.<id>` line in every messages
file, which the command page quotes. On enable the plugin warns about any registered command the catalogue does not
describe, or describes with other aliases or another node.

## Release

release-please keeps a release pull request open on `main`. Before merging it, rewrite its `CHANGELOG.md`
entry for operators and keep the pull request body's first and last lines, which release-please reads.
Merging it tags the release; the release workflow builds the jar on the organization runner and attaches
it with a `SHA256SUMS` file to the GitHub release.
