# uxmEssentials

A modern, modular essentials plugin for Paper 1.21+ servers. Homes, warps, teleports, economy,
kits, moderation, vaults and the rest of the day-to-day toolkit — built clean, Folia-ready, and
fully translatable.

It is organised as independent feature modules. Turn any module off and it wires nothing: no
commands, no listeners, no database tables, no runtime cost. Everything a player sees can be
re-worded or recoloured, and every command can be renamed, aliased or disabled from config.

## Highlights

- **17 feature modules**, each toggleable on its own: homes, warps, teleport, economy, kits,
  player-state, messaging, presence, moderation, item utilities, vaults, communication, holograms,
  player-warps, scoreboard/tab, vote rewards, and Discord account-linking.
- **~130 commands** with the muscle-memory aliases you already type (`/tp`, `/tpa`, `/back`, `/home`,
  `/warp`, `/pay`, `/baltop`, `/msg`, `/r`, `/kit`, `/vault`, `/gm`, `/heal`, `/feed`,
  `/fly`, `/vanish`, `/invsee`, `/ban`, `/mute`, `/jail` …).
- **GUI-first homes**: `/home` opens a slot grid — click an empty cell to set a home there, click a
  home to teleport, rename, relocate, delete, or pick a custom icon. No name-juggling commands.
- **In-game menus** for kits, warps and homes — click to claim, teleport, or manage — with the
  layout (rows, icons) editable from config.
- **Real economy**: balances are database-backed (they survive world rollbacks, never the PDC),
  multi-currency-capable, with `/pay`, `/baltop`, `/worth`, `/sell`, a Vault/Treasury provider and
  per-rank limits via numbered permission nodes.
- **Storage that scales**: SQLite out of the box (zero setup), or point it at MySQL/PostgreSQL to
  share homes, warps, economy, vaults and moderation data across a network.
- **Translatable**: every message is a key in a per-language catalog (English and Turkish ship
  in the box) rendered with MiniMessage; per-player locale is respected.
- **Folia-ready from line one**: all scheduling goes through region/entity-aware schedulers.
- **Config you can actually navigate**: a small root `config.conf` plus one folder per module under
  `modules/`, with a map at the top of the root file telling you where everything lives.

## Requirements

- Paper (or a fork) 1.21+
- Java 21

## Installation

1. Drop `uxmEssentials.jar` into `plugins/`.
2. Start the server once. The data folder fills in with `config.conf`, the `modules/` tree, the
   message catalogs and the command catalog.
3. Edit what you want, then apply it with `/uxmess reload <module>` or a restart.

Optional companion jars:

- `uxmEssentials-velocity.jar` — proxy-side broker for cross-server homes/warps/economy sync.
- `uxmEssentials-discord.jar` — JDA bridge for audit/economy notices and `/discordlink`.

## Configuration

The data folder looks like this:

```
plugins/uxmEssentials/
├─ config.conf                 # storage + locale + a map of where everything lives
├─ modules/
│  ├─ teleport/  config.conf · rtp.conf
│  ├─ economy/   config.conf · currencies.conf
│  ├─ kits/      config.conf · gui/kits-menu.conf · kits/<kit>.conf
│  ├─ warps/     config.conf · gui/warps-menu.conf
│  ├─ homes/     config.conf · gui/home-list.conf · gui/home-actions.conf · gui/icon-selector.conf
│  ├─ communication/ config.conf · join-quit.conf · announcer.conf · info-pages.conf
│  └─ … one folder per module
├─ messages/messages_<lang>.conf   # all player-facing text
└─ commands/commands.conf          # rename / alias / disable any command
```

Each module's `config.conf` starts with `enabled` and holds only that module's settings. Player
text lives in `messages/`; command names and aliases live in `commands/`. Nothing is overwritten
once written, so your edits survive restarts and upgrades.

## Commands & permissions

Command names, aliases and enabled-state are all editable in `commands/commands.conf`. Permission
nodes are keyed to the command's stable id (not its renameable name) under `uxmessentials.*`, with
numbered nodes for quotas and tiers (e.g. `uxmessentials.home.limit.<n>`,
`uxmessentials.teleport.cooldown.<seconds>`). The admin root is `/uxmess` (`/uxmessentials`, `/uxe`)
— `/uxmess reload <module>`, `/uxmess import essentialsx`, `/uxmess doctor`.

Running a command with missing arguments prints a coloured usage line instead of the vanilla error.

## Migrating from EssentialsX

`/uxmess import essentialsx` runs a one-shot, dry-run-first, backup-first import of EssentialsX
data. Run it once after install.

## Building from source

```bash
./gradlew build                       # full build + checks
./gradlew :bukkit-adapter:shadowJar   # the deployable plugin jar
./gradlew :bukkit-adapter:runServer   # a Paper dev server for testing
```

## License

GPL-3.0. See [LICENSE](LICENSE).
