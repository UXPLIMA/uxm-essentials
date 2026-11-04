# Guard

Guard is the region protection module: regions with rules per action and role, behaviours, templates, menus,
history, a WorldGuard importer and network scopes. The operator manual is the guard section of the published
docs; how the engine works is in [../internals/guard.md](../internals/guard.md). This page lists what an operator
grants and reads.

## Permissions

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.guard` | Hot-reload / inspect the guard module (region protection). | op |
| `uxmessentials.guard.use` | /guard and /guard help, actions: the base of every guard command. | true |
| `uxmessentials.guard.here` | /guard here: the regions you stand in and your role there. | true |
| `uxmessentials.guard.member` | /guard member <region> <player> <member\|guest\|clear>: owners and managers edit their own region's roster. | true |
| `uxmessentials.guard.why` | /guard why <action>: explain the verdict for an action where you stand. | true |
| `uxmessentials.guard.define` | /guard define, redefine, wand, pos1, pos2, point, clear, expand: draw and define regions. | op |
| `uxmessentials.guard.remove` | /guard remove and restore: delete a region or bring a removed one back. | op |
| `uxmessentials.guard.info` | /guard info <region>: a region's shape, rules, roles and behaviours. | op |
| `uxmessentials.guard.list` | /guard list [world]: list the regions of a world. | op |
| `uxmessentials.guard.rule` | /guard rule <region> <action> <target> <allow\|deny\|clear>: edit a region's rules. | op |
| `uxmessentials.guard.role` | /guard role <region> <player> <role\|clear>: give any role on any region. | op |
| `uxmessentials.guard.set` | /guard set <region> <behaviour> <value>: set a region behaviour. | op |
| `uxmessentials.guard.priority` | /guard priority <region> <value>: set a region's priority. | op |
| `uxmessentials.guard.parent` | /guard parent <region> <parent\|clear>: inherit rules from another region. | op |
| `uxmessentials.guard.family` | /guard family <region> <name\|clear>: group regions so their borders open to each other. | op |
| `uxmessentials.guard.tag` | /guard tag <region> add\|remove\|bulk: tag regions and edit them by tag. | op |
| `uxmessentials.guard.template` | /guard template <region> <template\|clear>: apply a rule template. | op |
| `uxmessentials.guard.scope` | /guard scope <region> <server\|set:name>: choose which servers a region applies on. | op |
| `uxmessentials.guard.lock` | /guard lock and unlock; edit and WorldEdit inside a locked region. | op |
| `uxmessentials.guard.bypass` | /guard bypass: toggle ignoring every guard rule. | op |
| `uxmessentials.guard.bypass.gamemode` | Keep your own game mode in a region whose gamemode behaviour would change it. | false |
| `uxmessentials.guard.show` | /guard show and hide: draw region borders for yourself. | op |
| `uxmessentials.guard.near` | /guard near [radius]: list regions around you. | op |
| `uxmessentials.guard.test` | /guard test <player> <action> [region]: check a verdict for another player. | op |
| `uxmessentials.guard.inspect` | /guard inspect: click a block to read what a player with no role may do there, and see why each of your own refusals happened. | op |
| `uxmessentials.guard.history` | /guard history <region>: the change history of a region. | op |
| `uxmessentials.guard.undo` | /guard undo <history id>: undo one change from a region's history. | op |
| `uxmessentials.guard.log` | /guard log [player]: recent denied actions. | op |
| `uxmessentials.guard.status` | /guard status: engine counters and memory use. | op |
| `uxmessentials.guard.import` | /guard import worldguard: import WorldGuard regions, with dry-run and undo. | op |
| `uxmessentials.guard.backups` | /guard backups [restore <file>]: list the region backups, or put a backup's regions back. | op |
| `uxmessentials.guard.reload` | /guard reload: reload the guard config and recompile every world. | op |
| `uxmessentials.guard.menu` | See the region list in /guard menu; without it a region owner or manager gets the roles window of the region they stand in. | op |
| `uxmessentials.guard.notify` | Receive staff alerts when players repeatedly hit guard denials. | op |

## Placeholders

Registered as `%uxmessentials_<key>%` when PlaceholderAPI is installed. Every `regions_` key is also readable as
`guard_`, for example `%uxmessentials_guard_here%`.

| Key | Module | Value | Scope |
|---|---|---|---|
| `regions_allowed_<action>` | guard | Whether the player may do a guard action where they stand, such as regions_allowed_break. | session |
| `regions_available` | guard | Whether the guard module is on (yes/no). Every regions_ placeholder is also readable as guard_. | global |
| `regions_count` | guard | How many regions cover the player at once. | session |
| `regions_here` | guard | The region the player is standing in, highest priority first when they overlap. | session |
| `regions_here_members` | guard | Who may build in the region the player is standing in, comma separated. | session |
| `regions_here_owners` | guard | Who owns the region the player is standing in, comma separated. | session |
| `regions_here_priority` | guard | That region's priority, which is what decides an overlap. | session |
| `regions_here_role` | guard | The player's role in the region they stand in (owner, manager, member, guest), or empty. | session |
| `regions_inside` | guard | Whether the player is standing in a protected region (yes/no). | session |
| `regions_world_count` | guard | How many regions are defined in the world the player is in. | session |
