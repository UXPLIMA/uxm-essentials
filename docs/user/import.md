# Importing from other plugins

The migration module copies data from the plugins uxmEssentials replaces: EssentialsX, LiteBans, three player warp
plugins, DecentHolograms, FancyHolograms, Multiverse-Core, and the balances of a Vault economy or PlayerPoints.
It is a one-shot tool: nothing runs until an operator types `/uxmess import`. WorldGuard regions are imported by the
guard module with its own command, described at the end of this page.

## Before you start

- Back up the database yourself. Before a live run the importer copies only the `.conf` files at the top of
  `plugins/uxmEssentials/` to `plugins/uxmEssentials-backups/pre-import-<time>/`; it does not copy the database.
- Leave the old plugin's folder in `plugins/`. Every source except Vault and PlayerPoints reads files or a database,
  not the running plugin, so the old plugin's jar can already be removed.
- Locations are matched to worlds by name against the worlds loaded on this server at the moment of the import.
  A home, warp, player warp or hologram in a world that is not loaded is left out. Import Multiverse worlds first,
  restart so they load, then import the rest.
- Do a dry run first and read the log.

## Turning the importer on

The module ships switched off. Set `enabled = true` in `plugins/uxmEssentials/modules/migration/config.conf` and
restart the server. The file is read once at startup, so every change to it, including the conflict policies and
the database settings below, needs a restart; `/uxmess reload` does not apply it. While the module is off,
`/uxmess import <source>` answers that the migration module is off. Turn it off again when you are done.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Switches the importer on. |
| `source-path` | `"Essentials"` | The EssentialsX folder, relative to `plugins/`. Only the EssentialsX source reads it. |
| `on-conflict` | `"skip"` | What happens to a record that already exists. See [When a record already exists](#when-a-record-already-exists). |
| `balance-policy` | `"skip-if-present"` | Whether an imported balance replaces one a wallet already holds. |
| `litebans { ... }` | | `jdbc-url`, `username`, `password`, `table-prefix` (default `"litebans_"`). |
| `axplayerwarps { ... }` | | `jdbc-url`, `username`, `password`. |
| `athelionplayerwarps { ... }` | | `data-file`, relative to `plugins/` (default `"PlayerWarps/data.yml"`). |
| `olzieplayerwarps { ... }` | | `jdbc-url`, `username`, `password`. |

An `on-conflict` or `balance-policy` value the importer does not know logs a warning naming the file and key, and
the safe default is used.

## Running an import

```
/uxmess import                        # usage, with the list of sources
/uxmess import --list                 # every source, its name and what it imports
/uxmess import <source> --dry-run     # report what would happen, write nothing
/uxmess import <source>               # import
```

`dry-run` without the dashes works too, and the source name is not case sensitive. An unknown name is refused with
the list of sources. The command answers at once that the import is running off the main thread; the result is
written to the server log, not to chat (see [Reading the result](#reading-the-result)). Run one import at a time and
wait for its `migration_import_finish` line before the next.

A dry run makes the same decisions as a live run against the current data, conflicts included, and writes the same
log lines, so the two can be compared line by line; its start and finish lines say `dry_run=true`. It takes no
backup.

| Source | Imports | Reads from |
|---|---|---|
| `essentialsx` | homes, balances, mail, warps, kits, mutes, jail sentences | `plugins/<source-path>/`: `userdata/`, `warps/`, `kits.yml` |
| `litebans` | bans, IP bans, mutes, warnings | the `litebans` database, or an H2 file under `plugins/LiteBans/` |
| `axplayerwarps` | player warps | the `axplayerwarps` database, or an H2 file under `plugins/AxPlayerWarps/` |
| `athelionplayerwarps` | player warps | `plugins/PlayerWarps/data.yml` (the `data-file` key) |
| `olzieplayerwarps` | player warps | the `olzieplayerwarps` database, or a SQLite file under `plugins/PlayerWarps/data/` |
| `decentholograms` | holograms | `plugins/DecentHolograms/holograms/*.yml` |
| `fancyholograms` | holograms | `plugins/FancyHolograms/holograms.yml` |
| `multiverse` | worlds | `plugins/Multiverse-Core/worlds.yml` |
| `vault` | balances | the Vault economy running now |
| `playerpoints` | balances | the PlayerPoints plugin running now |

## EssentialsX

Each `userdata/<uuid>.yml` gives one player:

- **Homes.** Every home whose world is loaded. Homes are placed in slots in alphabetical order of their EssentialsX
  name, the first in slot 1, and the name becomes the home's label (cut to 32 characters). All of a player's homes
  are imported even when they are more than the player's home limit; see [homes.md](homes.md) for what such a
  player can do.
- **Balance.** `money`, paid into the default currency of the economy module.
- **Mail.** Each message. The sender, send time and read flag are carried over only for entries EssentialsX wrote in
  its newer format (`legacy: false`); an older plain-text entry has none of them. Colour codes are removed. Mail
  EssentialsX had already expired is dropped, and mail older than `mail-retention-days` of the messaging module is not
  written, because the next sweep would delete it; raise the retention before importing to keep it. With the messaging
  module off, no mail is written.
- **Mute.** A muted player stays muted, until the same time or for good, with the mute reason. The issuer is
  recorded as `EssentialsX` and the issue time is unknown.
- **Jail sentence.** A jailed player stays jailed in the jail of the same name: until the same time, for the same
  online time left, or for good.
- **One-time kit claims.** A player who already took a kit whose `delay` is negative keeps it claimed, with the time
  in `timestamps.kits` of their file. Claims of repeatable kits and of kits `kits.yml` no longer has are not read. The
  claim goes into the database whatever `on-conflict` says, and never twice. With the network on, every server sees
  it at once. On a single server it reaches the player's data when they next join, or at once if they are online
  during the import; `/kit reset` clears it again.

Each `warps/<name>.yml` becomes a warp of that name, free and open to everyone. Each entry of `kits.yml` becomes a
kit (see [What is not imported](#what-is-not-imported) for what a kit loses). An unreadable file, a warp in a world
that is not loaded, and a kit that cannot be converted are each skipped with a warning naming them; the rest of the
import goes on.

### Kit permissions

EssentialsX hands a kit only to players with `essentials.kits.<name>`. Every imported kit keeps that gate: its file
gets `permission = true`, so claiming it needs `uxmessentials.kit.<name>` (the name in lower case) on top of
`uxmessentials.kit.use`. Operators hold every `uxmessentials.kit.<name>` node by default and nobody else does, so after
the import only operators can claim the kits until the new nodes are granted.

| EssentialsX node | uxmEssentials node |
|---|---|
| `essentials.kit` | `uxmessentials.kit.use` (everyone by default) |
| `essentials.kits.<name>` | `uxmessentials.kit.<name>` (operators by default) |

A kit whose name is also one of the plugin's own kit nodes (`use`, `edit`, `others`, `preview` or `reset`) would sit
behind that node, so it keeps `essentials.kits.<name>` as its `permission-node` instead, and the import log names it.

Grant each kit to the groups that held its EssentialsX node, with LuckPerms for example:

```
/lp group default permission set uxmessentials.kit.starter true
/lp group vip permission set uxmessentials.kit.vip true
```

To move the existing grants of one kit in a single step, contexts and expiry included, rename the node with a bulk
update from the server console (LuckPerms takes bulk updates from the console only) and confirm it with the command
LuckPerms prints. Back up first with `/lp export`. The update renames rather than copies, so run it only once
EssentialsX is gone from every server that shares this LuckPerms storage, and once per kit:

```
/lp export before-kit-nodes
/lp bulkupdate all update permission uxmessentials.kit.vip "permission == essentials.kits.vip"
```

Do not replace `essentials.kits.*` with `uxmessentials.kit.*`: that wildcard also grants the staff nodes under it,
such as `uxmessentials.kit.edit`, `uxmessentials.kit.others` and `uxmessentials.kit.reset`, and every
`uxmessentials.kit.cooldown.<group>` wait. Grant the kits one by one. To keep using the EssentialsX node for a kit
instead, set `permission-node = "essentials.kits.<name>"` in its file.

A kit file with `permission = false` is open to every player with `uxmessentials.kit.use`. With
`on-conflict = "skip"` an import keeps such a kit as it is and names it in the log. To find these kits:

```
grep -lE 'permission\s*=\s*false' plugins/uxmEssentials/modules/kits/kits/*.conf
```

Set `permission = true` in each file and restart the server: kit files are read at startup, and `/uxmess reload` does
not read them again. The permission button in `/kit editor` changes the flag without a restart. Importing again with
`overwrite` also gates them, but replaces each kit and any change made to it since.

## LiteBans

The importer reads the `bans`, `mutes` and `warnings` tables with the configured prefix. Leave `jdbc-url` empty to
open the LiteBans H2 file under `plugins/LiteBans/` read-only. An H2 file written by an older LiteBans may not open
with the engine uxmEssentials carries; then point `jdbc-url` at the database instead:

```
litebans {
  jdbc-url = "jdbc:mariadb://localhost:3306/litebans"
  username = "litebans"
  password = "secret"
  table-prefix = "litebans_"
}
```

Only rows LiteBans still marks active are imported. Reasons, issue times, expiry times and the name of the staff
member are kept. A permanent ban is stored the way `/ban` stores one. An IP ban is imported by its address, whatever
player it names; a wildcard (range) IP ban is skipped. A ban, mute or warning whose player is not a real UUID
(LiteBans writes `#offline#` and similar) is skipped. A table that cannot be read is logged and skipped.
`table-prefix` must contain only lowercase letters, digits and `_`; any other value stops the LiteBans import with an
error in the log.

## Player warps

All three sources write through the same path as `/setpwarp`:

| Source | Carried over |
|---|---|
| AxPlayerWarps | owner, location, category, price, access (public, whitelist or private), ratings, whitelist, blacklist as warp bans, favourites, visit counts |
| AthelionPlayerWarps | owner, location, category, admission price, a password (stored hashed) or closed status, ratings, blocked players as warp bans, visit counts |
| OlziePlayerWarps | owner, location, category, cost, access (password, whitelist, locked or public), ratings, whitelist, managers as warp members, bans with their reason, favourites, visit counts |

Prices are recorded in the default currency. Athelion and Olzie do not store the owner's name, so those warps take
the name this server knows the owner by. An owner who has not joined this server yet is shown by uuid until they
join, and then by name. A warp name is turned into a valid player warp name (lowercase letters, digits, `_` and `-`,
3 to 32 characters); when another player already owns that name, the warp gets `name2`, `name3` and so on. A warp
whose world is not loaded or that names no owner account is left out.

## Holograms

DecentHolograms and FancyHolograms holograms keep their name, position and the text lines of the first page. They
get the look, visibility and refresh of a hologram made with `/hologram create`. Pages, click actions, view ranges,
rotation, blank spacer lines and lines too long for a hologram line are dropped. A DecentHolograms icon or item line
is imported as plain text. FancyHolograms item and block holograms, and holograms in a world that is not loaded, are
left out.

## Multiverse worlds

Each world in `worlds.yml` becomes a world of the worlds module, with its environment, seed, generator, alias and
auto-load, and, where Multiverse's value differs from the worlds module's own default, PvP, difficulty, forced game
mode, player limit, a money entry fee and the spawn point. Other Multiverse settings are not read. A world whose name
the worlds module cannot hold is skipped with a warning. The import only registers the world: it loads on the next
start when `auto-load-registered` is on in `modules/worlds/config.conf` and the world's own auto-load is on. See
[worlds.md](worlds.md) for where Paper keeps world folders.

## Vault and PlayerPoints balances

Both sources read the plugin while it runs, for every player this server has seen. A player with a balance above
zero gets that balance in the default currency; one PlayerPoints point is one unit. If the registered Vault economy
is uxmEssentials itself, there is nothing to read and the run imports nothing. To keep PlayerPoints as its own
currency instead of copying it, see the `backend` key in [economy.md](economy.md).

## When a record already exists

`on-conflict` decides what happens to a home in an occupied slot, and to a warp, kit, hologram or world with the
same name:

| Value | Effect |
|---|---|
| `skip` | The existing record stays. The default, and safe to run again. |
| `overwrite` | The imported record replaces the existing one. |
| `merge` | Accepted, and handled exactly like `overwrite`. |

For moderation it works per player: with `skip`, a player who already has any mute or jail gets neither imported,
a player who already has an active ban keeps it, and an address that already has an active IP ban keeps it.

`balance-policy` decides balances. An import always sets a balance, it never adds to one.

| Value | Effect |
|---|---|
| `skip-if-present` | A wallet that holds any amount other than zero in the default currency, whatever put it there, is left alone. The default. |
| `overwrite` | Every imported wallet is set to the source balance. Use it only on an economy nobody has used yet. |

Some records ignore both settings:

- **Player warps** are never replaced. A warp whose name, or a numbered form of it, already belongs to the same
  owner counts as imported and is skipped.
- **Warnings** are never duplicated: one with exactly the same issuer, reason, issue time and expiry is skipped.
- **Mail** is never duplicated: a message with the same sender, text and, when known, send time is skipped.

## Running it again

Every write is keyed (a home by player and slot, a warp, kit, hologram or world by name, a ban by player or
address, a kit claim by player and kit), so running the same import again with the default policies adds nothing
and changes nothing, and an import that stopped part way can simply be run again. Balances are set, never added, so
they cannot be counted twice. A kit claim cleared with `/kit reset` since the last run is recorded again.

Homes are matched by slot, not by name. Each run places a player's homes in alphabetical order from slot 1, and
with `skip` a home is written only when its slot is empty. Importing a second EssentialsX folder for the same
players, for example from a second server, therefore writes only the homes that fall on empty slots and skips the
rest, even when their names differ.

## Reading the result

The import writes structured lines to the audit log channel `com.uxplima.uxmessentials.audit`, which appears in the
server log:

| Line | When |
|---|---|
| `event=migration_import_start` | At the start: who ran it, the source, `dry_run`, the source path and the backup folder name. |
| `event=migration_import_<kind>` | One per record: `user`, `warp`, `player_warp`, `kit`, `jail`, `mute`, `ban`, `ip_ban`, `warn`, `hologram`, `world`. |
| `event=migration_import_finish` | At the end, with the counts and `duration_ms`. |

Each record line has `ok=`, and every one but the `jail` and `mute` lines has `conflict=` (`written`, `skipped`,
`overwritten`, `partial` or `failed`). A `user` line also says which parts landed: `homes_written`, `homes_kept`,
`balance` (`written`, `kept` or `none`), `mail_written`, `mail_kept`, and when they apply `mail_expired`,
`mail_past_retention` and `mail_skipped=messaging_disabled`, and for a player with one-time kit claims
`kit_claims_written` and `kit_claims_kept` (already recorded). `partial` means some of the player's parts were kept
back. The finish line counts `users`, `warps`, `kits`, `jails`, `mutes`, `bans`, `ip_bans`, `warns`, `skipped`,
`partial` and `failed`; player warps, holograms and worlds appear only on their own record lines. One record that
fails is counted and the run goes on. A run that cannot continue logs `migration import failed for source <source>`.

Entries a source leaves out never reach these counts. Some are logged as warnings (EssentialsX files, warps and
kits, LiteBans tables, Multiverse worlds, malformed player warps); others are left out without a line (homes,
holograms and player warps in a world that is not loaded). Compare the counts with the old plugin when the numbers
matter.

## What is not imported

- **Kit cooldowns.** Only the claims of one-time kits are read, so no cooldown of a repeatable kit is running after
  the import.
- **Kit contents beyond plain items.** A kit keeps its name, its `delay` as the cooldown (a negative delay becomes
  `one-time = true`) and its items with amount, damage and enchantments. Item names, lore and other item data are
  dropped; money lines, command lines and items whose material does not exist are left out without a warning.
- **The EssentialsX `kits/` folder.** Only a combined `kits.yml` is read.
- **EssentialsX jail locations.** Jail sentences name a jail, but `jail.yml` is not read. Create each jail again with
  `/setjail` under the same name.
- **Other EssentialsX data:** nicknames, the spawn, ignore lists and the rest of userdata.
- **LiteBans kicks and removed punishments.**

## WorldGuard regions

The guard module imports WorldGuard regions with its own command; the migration module does not need to be on.

```
/guard import worldguard dry-run        # write a report, change nothing
/guard import worldguard run            # import
/guard import worldguard undo           # remove the regions of the last import
/guard import worldguard run sql        # read WorldGuard's SQL store instead of its files
```

The last word is `yaml` (the default) or `sql`. `yaml` reads `plugins/WorldGuard/worlds/<world>/regions.yml`; `sql`
reads the database named by `regions.sql` in `plugins/WorldGuard/config.yml`. Both read the files, not the running
plugin.

- A dry run writes `plugins/uxmEssentials/guard/import-report.txt`: how many regions, rules and behaviours would be
  made, and a note for every flag or player that is not converted. A run writes the same report.
- A run first saves every existing region as a `before-import` backup (`/guard backups`), then stores the regions.
  A region whose name already exists is skipped, never overwritten.
- `undo` removes the regions of the last run only.
- Only one import runs at a time; a second request is refused.

Region names are lowercased and other characters become `-`. `__global__` becomes `wg-global` at priority
`-100000`. Owners and members become the owner and member roles, and groups become `group.<name>` permissions. A
player stored by name is found through the server's player cache; one the server has never seen is left out and
noted. WorldGuard lets only members build in a region without flags; that becomes explicit rules, because a guard
region without rules does nothing. State flags such as `pvp`, `chest-access`, `tnt` or `entry` become rules for
their `-group`. `greeting`, `farewell`, their `-title` forms, `entry-deny-message`, `exit-deny-message`,
`game-mode`, `invincible`, `time-lock`, `weather-lock`, `blocked-cmds`, `allowed-cmds`, `heal-amount`,
`feed-amount`, `fall-damage`, `firework-damage`, `natural-hunger-drain`, `exp-drops`, an empty `deny-message`,
`mob-spawning` and `deny-spawn` become behaviours. See [guard.md](guard.md) for rules, roles and behaviours.

## On a network

With a shared database, run each import once, from one server. Homes, balances, mail, warps, moderation, player
warps, holograms and one-time kit claims go into the shared database and every server sees them. Once a live import
ends, the importing server tells the others: warps reload at once, holograms reload by name over the following
minutes (16 every 10 seconds), and homes, balances and player warps are picked up as each server's cache refreshes.
A good time to import is before players join.

Three kinds of data stay with one server:

- **Kits** are files. They are written to `modules/kits/kits/` on the server that ran the import; copy the files to
  the other servers.
- **Multiverse worlds** are recorded under the importing server's name. Run the `multiverse` import on each server
  that had its own `worlds.yml`. See [worlds.md](worlds.md).
- **WorldGuard regions** get the scope set by `network.new-region-scope` in `modules/guard/config.conf`: with the
  default `server` they apply only on the server that imported them. Run the guard import on each server, or name a
  shared set first.

LiteBans and the player warp plugins usually kept one database for the whole network already; import it once.
See [network.md](network.md) for the shared database itself.

## Permissions

| Node | Allows | Default |
|---|---|---|
| `uxmessentials.admin` | The `/uxmess` root. | op |
| `uxmessentials.admin.import` | `/uxmess import`. | op |
| `uxmessentials.guard.use` | The `/guard` root. | everyone |
| `uxmessentials.guard.import` | `/guard import worldguard`. | op |
| `uxmessentials.guard.backups` | `/guard backups`, to put the `before-import` backup back. | op |

The `/uxmess` nodes are in [permissions-server.md](permissions-server.md); the guard nodes are in
[guard.md](guard.md).
