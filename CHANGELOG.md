# Changelog

## [0.14.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.13.0...v0.14.0) (2026-11-18)


### Features

* **integration:** join the shared uxm placeholder expansion ([#424](https://github.com/UXPLIMA/uxm-essentials/issues/424)) ([5226944](https://github.com/UXPLIMA/uxm-essentials/commit/5226944b8b0857c7f8293d1bf479cf75abda73c0))

## [0.13.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.12.0...v0.13.0) (2026-11-18)

Upgrading: this release is built for networks and for every kind of player. Offline-mode accounts, players who are
offline, Bedrock players through Geyser with or without Floodgate, and players on another server of the network are
reached by the commands that used to answer only for players online here. Upgrade every backend and the proxy plugin
that share a database in the same window, back up the database first, and read
[docs/user/upgrading.md](docs/user/upgrading.md) before you start. Five changes need an operator:

- With `network.transport = "velocity"` or `"both"`, the proxy bus stays closed until every backend sets the same
  `network.bus.secret` of 32 or more random characters.
- Every exemption node moved under `uxmessentials.bypass.`, and our placeholders are `%uxm_essentials_<key>%`.
  [upgrading-renames.md](docs/user/upgrading-renames.md) has the node map and the LuckPerms commands. The old
  `%uxmessentials_<key>%` placeholders keep answering, with a warning per key, until a later release.
- `/eco giveall` and `/eco resetall` change every player who has joined a server of the database, not only the
  players online here. A scheduled `eco giveall <amount>` keeps its old meaning only with `--online`.
- Managed worlds are kept per server. Give every networked backend its own `network.server-name`; a backend of the
  previous release cannot save a world once one server has started on this release.
- `uxmessentials.warp.use.<warp>` now defaults to every player as documented, so every server warp opens to every
  player. Close a warp with a required permission of your own or by denying its node.

The update adds its tables and columns by itself on the first start.

### Added

- Offline and remote players: `/pay`, `/eco`, `/setrank`, `/invrestore`, `/home admin tp`, `/tpblock`, kit resets,
  `/vault` of another player, invsee edits, playerstate and itemworld reach a player who is offline or on another
  server. What has to happen in the player's game waits for their next join.
- Across the network: homes, server warps, `/tpa` and staff teleports, jail, freeze, kick, `/security`,
  `/staffmode`, `/clientinfo`, `/msg`, helpop, staff chat, mail, social spy and messaging switches, nicks, skins, vote
  payouts and vote parties reach every server. Lists, tab completion and the player pickers name the players of every
  server.
- Bedrock: forms and the xuid through Geyser on the proxy or on the backend, with or without Floodgate; a form for
  every click-only action, a typed PIN and a custom loan payment; Bedrock players who share a Geyser address are told
  apart; they wear their own skin first.
- Board head tags for lines the plugin draws itself (holograms, the scoreboard, the tab list, boss bars,
  nametags): `<uxm_essentials_head>` and `<uxm_essentials_top_<board>_<period>_<n>_head>` draw an offline-mode or
  Bedrock player in their own skin. See the Boards section of [reference.md](docs/user/reference.md).
- Moderation names accounts that never joined; ladder reasons read in each reader's language.
- `/eco sweeps` and `/eco sweeps cancel <id>` follow a giveall or resetall run
  (`uxmessentials.economy.admin.sweeps`).
- REST and the Java API look players up by name.
- Every module panel has a tile on the management hub.
- New settings:
  - `on-backend-missing` in `modules/customcommands/config.conf`: whether a priced custom command runs for free
    (`allow`, the default) or is refused (`deny`) while its economy is missing;
  - `hard-delete-after-days` under the player warp rent settings: delete a warp left archived for unpaid rent;
  - `server-switch` in `modules/communication/join-quit.conf`;
  - `lookup.name-case`, `case-variant-sanctions` and `case-variant-address`, for offline mode.

### Changed

- What players type is shown as they typed it: in menus, message files, join, quit and death lines, `/me`, `/afk`
  and custom command text actions. A `%placeholder%` a player typed is no longer filled in. Values from our
  placeholders show as words in our lines, apart from the ones marked `markup`.
- A menu password step, such as a player warp's, is asked as a secret. Menu files already on disk need
  `secret = true` added by hand.
- Custom command arguments go into a command only when they fit; a multi-word argument there needs
  `in-commands = "text"`. Console and operator steps follow only the custom commands config.
- On a network a server switch shows no join or quit line and no MOTD again.
- On offline mode `Steve` and `steve` are two players, and a ban, mute or jail follows a player into another spelling
  of their name.
- A PIN is ASCII digits only.
- `/tp`, `/tphere`, `/goto` and `/bring` stop at `/tptoggle`; `/tpo` and `/tpohere` still go through.
- A cooldown node is the wait rather than a cap; an RTP radius node only narrows `max-radius`; home and player warp
  world limits no longer lift the overall limit.
- Rank actions take `{uuid}`.
- Bans are checked at async pre-login, and storage, permission and world reads moved off the server thread.

### Fixed

- Money: refunds in the currency taken, for warps, homes, hops, `/rtp` and a paid teleport that never lands; vault
  refunds capped at what the vault cost; ledger ids safe across servers.
- Items: a failed or abandoned sale, a trade with a player who left and a closing vault keep their items.
- Votes are credited to the right Bedrock or offline-mode account, and late or doubled copies are dropped.
- Teleport warmups and cooldowns: one warmup at a time, cancelled on quit with a reason, cooldowns started at every
  phase.
- Moderation: a longer mute or a ladder step extends the running one, a staff ban never cuts the running one short,
  mutes and IP bans are guarded like bans, a mute that cannot be read fails closed, and old IP ban spellings lift.
- MySQL and MariaDB: item columns are `LONGTEXT`, and player name columns are wide enough for Floodgate names.
- The docs, the catalog and the config comments match the code.

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
