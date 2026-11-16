# Guard

Guard is the region protection module: regions with rules per action and role, behaviours, templates, menus,
history, a WorldGuard importer and network scopes. The operator manual is the guard section of the published
docs; how the engine works is in [../internals/guard.md](../internals/guard.md). This page points to what an
operator grants and reads.

## Permissions

Every guard node, with what it allows and its default, is under [guard in permissions-server.md](permissions-server.md#guard).

## Placeholders

Every guard key, with what it renders and an example, is under the guard module in [placeholders.md](placeholders.md). Each `regions_` key is also readable as `guard_`, for example `%uxm_essentials_guard_here%`.
