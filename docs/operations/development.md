# Development

The build comes from `uxm-gradle`: `uxm.paper-plugin` adds the Paper API, Error Prone, NullAway, Spotless,
the shaded jar with every bundled library relocated under `com.uxplima.uxmessentials.libs`, and `verifyJar`,
which fails when the jar carries what the server owns or lacks the plugin's own classes. Versions come
from the `uxm` catalog.

## Build and run

```sh
./gradlew build       # everything CI runs
./gradlew runServer   # a local server with the plugin, in run/
./gradlew :persistence-adapter:realDatabaseTest   # the repository tests on MySQL, MariaDB and PostgreSQL
```

A repository test class carries `@EveryBackend` and takes a `TestDatabase` in its `@BeforeEach`. The build runs it
on SQLite; `mysqlTest`, `mariadbTest` and `postgresqlTest` run it again on that server in a container, with the
plugin's own pool and migrations, and CI runs each in a job of its own. Without Docker those three tasks skip their
tests and say why; `-Puxm.requireDocker=true` makes them fail instead, as CI does.

## Release

release-please keeps a release pull request open on `main`. Before merging it, rewrite its `CHANGELOG.md`
entry for operators and keep the pull request body's first and last lines, which release-please reads.
Merging it tags the release; the release workflow builds the jar on the organization runner and attaches
it with a `SHA256SUMS` file to the GitHub release.
