# Changelog

## [0.12.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.11.0...v0.12.0)

Upgrading: the `regions` module is gone and the new `guard` module protects regions itself. A server that used
`/regions` with WorldGuard imports its regions with `/guard import worldguard dry-run`, then `run`, and can remove
WorldGuard once they match. Grants of `uxmessentials.regions.*` move to the `uxmessentials.guard.*` nodes. The
`regions_*` placeholders keep their names.

### Added

- The guard module: region protection for Paper and Folia, on one server or a network. Regions of any shape with
  rules per action and role, behaviours, templates, menus, history and undo, inspect, backups, alerts and a
  WorldGuard importer. `/guard` (`/bolge`), `modules/guard/config.conf`.
- `security.allowed-operators`: only the listed players may be operators.
- One command-control entry can cover a whole plugin, and entries reach down to a command's arguments.

### Changed

- A player who types a command word they may not use reads "you don't have permission" in their language, and the
  word stays out of their command list and tab completion. Before, they read Minecraft's "Incorrect argument for
  command". A feature that is turned off answers with the new `command.unavailable` line.
- The kit cooldown refusal says how long is left (`kit.on-cooldown` takes `{time}`).
- Command-control edits apply on `/uxmess reload commandcontrol`.
- `/rtp` keeps out of guard regions under `respect-worldguard`, as it keeps out of WorldGuard regions.
- The Latin-script languages are written in small capitals.

### Fixed

- Command words may use the letters of every language.
- `/setspawn` is described as the current world's spawn.
- The example menu's title carries no colour.

### Removed

- The `regions` module, `/regions` and the `uxmessentials.regions.*` nodes.

## [0.11.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.10.3...v0.11.0)

### Added

- Correct and import playtime.
- Show each language's readers their own command words.
- Answer the shared leaderboard placeholders.
- Rank money earned and spent.
- Rank playtime, trades and prestige.
- Rank votes on the shared boards.
- Step back from chat features uxmChat runs.

### Changed

- The warp picker's effect and sound presets are named in every language's messages file.
- The network block names this backend with `server-name`; a file that still says `server-id` keeps working.
- The shipped `config.conf` lists `storage` right after `language`, and `menu` last.
- The SQLite database lives under data/ and moves there by itself on the first start. The config copy is in backups/.

### Fixed

- Point the plugin website at its uxplima.com product page.
- Join hologram redraws and parse shared words once.
- Send nametags only to clients that track the wearer.
- Mark vanished players for other plugins.
- Put navigation where the conventions say.
- Describe the new playtime subcommands in help.

## [0.10.3](https://github.com/UXPLIMA/uxm-essentials/compare/v0.10.2...v0.10.3) (2026-10-28)

### Fixed

- An amount with a leading zero, such as `0.500` or `0.001`, is read as a decimal. Before this, `/pay 0.500` paid 500.
- The permission list describes the kit cooldown and home permissions the way the plugin checks them.

## [0.10.2](https://github.com/UXPLIMA/uxm-essentials/compare/v0.10.1...v0.10.2) (2026-10-27)

### Added

- The bossbar module: bars that stay up, timed bars and announced bars. Bars reach placeholders, the API and REST, a rotating frame is read in each player's language, and a visibility event is published.
- The pause-menu link labels are read in each player's language, and the links are sent again when a player chooses another language.

### Changed

- Every skin name opens unless the operator restricts it, and the console can dress another player.
- Menus play the sounds `config.conf` names, and every shipped window is filled with the house filler.
- Releases are tagged `vX.Y.Z` again, and the jars and a `SHA256SUMS` file are attached to every release. Commands, permissions, configuration files and stored data stay as they were.

### Fixed

- The worlds module reaches every dimension of Paper 26.2. A world can be named by its key (`minecraft:the_nether`, `mypack:mining`), by the bare path of the nether or end (`the_nether`), or by the name the server gives it (`world_nether`, `mypack_mining`). A datapack dimension is found under its own namespace and loads from there on every start.
- `/worlds import` reads the environment from the dimension when none is given, instead of answering "Unknown or incomplete command".
- A player's HUD keeps drawing after the server drops one of their renders.

## 0.0.1 (2026-10-27)

### Changed

- Versions restart at 0.0.1 under the UXPLIMA repository standard. Commands, permissions, configuration files and stored data stay as they were.
- The jar and a `SHA256SUMS` file are attached to every release.
