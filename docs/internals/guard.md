# Guard internals

The guard module is the region protection of uxmEssentials. This page is for whoever changes its code: how a check
is answered, why it is built that way, and the traps that cost time before. The operator manual is the guard
section of the published docs.

## Where the code lives

| Package | What it holds |
|---|---|
| `core/.../guard/domain` | Regions, shapes, rules, targets, behaviours, templates, the action tree. No Bukkit |
| `core/.../guard/domain/engine` | The compiler, the per world index, chunk columns, cells, region sets, the explainer |
| `core/.../guard/application` | `GuardService` (every edit, history, undo, restore, sync), `GuardConfig`, message keys, ports |
| `persistence-adapter/.../persistence/guard` | `JooqGuardStore` and the region JSON; tables come from Flyway `V88` |
| `bukkit-adapter/.../guard/adapter` | `GuardRuntime` (the check and its feedback), `GuardWiring`, listeners, movement, menus, the command, the WorldGuard importer, backups |
| `rest-adapter/.../RegionsRoutes.java` | The read and check routes under `/api/v1` |
| `api/.../query/UxmGuardQuery.java` | The public query other plugins call |

## How a check is answered

1. A listener turns a Bukkit event into an action id (`break`, `use.container`, `world.explosion.creeper`) and a
   subject (the player, or nature). `ActionKinds` and `Causes` hold those mappings.
2. `GuardRuntime` reads the current `GuardSnapshot`, one volatile read. The world's `WorldIndex` answers with a
   bit test whether any rule in that world names the action or one of its parents. If none does, the check
   returns at once.
3. The index finds the chunk column by a hash probe, the 16 block section by an array read, and gets the
   section's `RegionSet`. An exact cell (wholly inside its regions) answers directly; a mixed cell tests its few
   candidates and memoizes the set for each combination it meets.
4. The region set is walked in precedence order: higher priority first, at equal priority the smaller region
   first, then by id. Regions of one priority where none contains another form a tie group, and inside a group
   a deny beats an allow. The first group that says anything decides; when none does, the world defaults do.
5. Inside one region the compiled table is read for the action, then its parents up the action tree. The
   target rank is a role or nonmember first, then a permission or condition, then everyone; a deny wins within
   a rank.

Nothing is allocated for an allowed check. Explanations (`/guard why`, `/guard test`, inspect) go through
`Explainer`, which walks the same path and records each step; it is only called when asked.

## Why it is compiled

A region's stored rules are short and readable; a check needs one array slot. `Compiler` flattens each region
once: the parent chain from the root down, each level's template first and then the level's own rules, so a
child writes only what differs. The result is one table per region indexed by the dense action numbers of
`ActionTree` (a parent always has a smaller index than its children, and the same ids always get the same
numbers).

An edit recompiles only the regions that changed, and carries over every chunk column away from the change. The
new `GuardSnapshot` is swapped in one step, so a check on any thread, on Paper or on any Folia region thread,
reads a consistent world without a lock. Columns are built lazily the first time a chunk is asked about, and a
chunk without regions keeps nothing.

`./gradlew :core:guardBench` measures compile and check times against a naive scan; the published Performance
page quotes its numbers.

## Listener families

`ListenerRegistry` registers a family (building, interaction, damage, items, nature, explosions, hoppers) only
while a rule needs it, and unregisters it when none does. Nature, explosions and hoppers also carry the border
test (an effect may not cross from outside a region into it), so they are on as soon as any world has a region
with a rule or is locked. A new listener belongs to a family, and its action roots must be listed there, or it
is never registered.

`InspectListener`, `WandListener`, `LockListener`, the movement and the behaviour listeners are fixed and always
registered.

## Movement and behaviours

`MovementListener` does nothing for a move inside one block, one region set lookup for a move to another block,
and runs rules and behaviours only when the region set changes. `PlayerTracks` keeps each player's set and what
the behaviours took over (flight, speeds, game mode, player time and weather), to give it back when no region
overrides it any more and on quit.

A global timer every `movement.check-ticks` (10) runs `tracks.tick` for each player on that player's own scheduler.
It catches up on moves no event reported: teleports by other plugins, vehicles, and Folia, where a fake player
fires no `PlayerMoveEvent`.

`tracks.reapply(player)` puts the current behaviours on again without messages. `refresh` acts only on a change
of set, so a state change that keeps the set (toggling bypass) must call `reapply`.

## Quiet refusals

`GuardRuntime.denied(..., tell)` with `tell = false` cancels and does nothing else: no message, no alert, no log
row, no inspect line. It is used for refusals the player did not choose: picking up an item, pressure plates,
turtle eggs, farmland and entity trampling. Without it a player standing next to a dropped item gets a message
and a log row every tick. Movement refusals are also checked quietly; `PlayerTracks.refuse` sends their own
message (the region's `enter.deny-message` or `exit.deny-message`).

`deny-messages = false` on a region silences its refusals through the same path.

## Command lists

`CommandWords` compares a typed command with `commands.deny` and `commands.allow` by every name of the command,
not the typed word only, since aliases are separate entries in the command map. The deny side is broad (any
shared name, whoever registered it); the allow side and the exemption that keeps `/guard` usable everywhere are
strict (a shared name and the same owner through `PluginIdentifiableCommand`). Two security reviews found an
alias walking through a list; the tests in `CommandWordsTest` hold both directions.

## Networks

Every edit is one transaction (region plus history), then a "region changed" message on the network bus.
`GuardService.poll` runs every `network.poll-seconds` and reads regions changed since the last look, so a lost
message delays an edit and never loses it. A server compiles only the scopes it loads (`server:<own name>` and
the sets in `network.shared-sets`).

## Backups and restore

`FileGuardBackups` writes every loaded region as one JSON file, sorted by world and id, and skips the write when
the content equals the newest file, so restarts do not rotate real backups out. `GuardService.restoreBackup`
puts each region back as one history entry (`backup-restore`), skips locked regions unless the editor may edit
locked ones, and skips regions whose scope this server does not load. The command takes a `before-restore`
backup first.

## The WorldGuard importer

`WorldGuardImporter` reads `regions.yml` files or WorldGuard's SQL tables, never the running plugin. The flag
tables are its two maps of flags to actions and flags to behaviours; the published import page lists them and
must change with them. WorldGuard's "members only build" default becomes explicit rules, because a guard region
without rules does nothing. Every run is one batch; `undo` removes the batch. A name that already exists is
skipped, never overwritten.

## Traps

- **A world with no rules answers UNSET, not allow.** Callers that need a yes or no (claims, RTP) treat UNSET as
  "no guard opinion" and fall through to their own checks.
- **Inspect cancels the click at LOWEST.** Anything that must see the block click before guard does not exist;
  do not move inspect later or other plugins act on the inspected block.
- **The exemption looks up `uxmessentials:guard`.** Renaming the command or the plugin breaks the exemption
  silently; `CommandWordsTest` covers the comparison, not the lookup.
- **Placeholders in behaviour text.** `PlayerTracks.expand` replaces `{player}` and runs PlaceholderAPI when it
  is present. Do not expand player supplied text through it.
- **Two protections at once.** While WorldGuard is installed both plugins cancel; a test that passes with guard
  alone can fail with both.
- **Bots on Folia** do not fire move events. Entry and exit tests with Craftwire bots rely on the tick catch-up,
  so wait at least `movement.check-ticks` before asserting.
