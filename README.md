<p align="center">
  <img src="docs/images/banner.jpg" alt="uxmEssentials: every essential a server needs, in one plugin" width="100%">
</p>

<p align="center">
  <a href="https://github.com/UXPLIMA/uxm-essentials/releases/latest"><img alt="Release" src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2FUXPLIMA%2Fuxm-essentials%2Fmain%2F.release-please-manifest.json&query=%24%5B%27.%27%5D&label=release&prefix=v&color=4c9a2a"></a>
  <a href="https://github.com/UXPLIMA/uxm-essentials/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/UXPLIMA/uxm-essentials/actions/workflows/ci.yml/badge.svg"></a>
  <img alt="Paper | Folia 26.2+" src="https://img.shields.io/badge/Paper%20%7C%20Folia-26.2%2B-62b47a">
  <img alt="GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue">
</p>

**uxmEssentials** is the essentials suite for Paper and Folia: 37 modules in one jar, from homes, warps, kits and
an economy to moderation, region protection, holograms and NPCs. Each module has its own config file and its own
switch, so a server takes only what it needs and leaves the rest off.

Nothing else is required: uxmLib is inside the jar. Vault and PlaceholderAPI are used when present.

**[Download](https://github.com/UXPLIMA/uxm-essentials/releases/latest)** ·
**[Manual](https://docs.uxplima.com/minecraft/plugins/uxmessentials)** ·
**[Report a bug](https://github.com/UXPLIMA/uxm-essentials/issues)**

## What it does

<img src="docs/images/management-hub.jpg" alt="The management hub: a tile for each module panel, each opening that panel" width="100%">

| Area | Modules |
|---|---|
| **Getting around** | Teleport (`/tpa`, `/back`, `/rtp`, `/spawn`), homes, warps, player warps, worlds |
| **Money and items** | Economy with several currencies, `/sell` and `/worth`, kits, vaults, trades, villager trade rules |
| **Chat and people** | Private messages, mail, broadcasts, AFK, nicknames, `/whois`, votes and vote parties |
| **Staff** | Bans, mutes, jails and warnings, staff mode and staff chat, vanish, inventory rollback, command control |
| **The server's look** | Scoreboard, tab list, boss bar, name tags, holograms, NPCs, poses, skins, custom menus and commands |
| **Protection** | Guard: regions with rules per action and role, templates and a WorldGuard importer |
| **Player state** | `/fly`, `/heal`, `/feed`, `/gamemode`, `/speed`, `/endersee` and the rest of the classics |

`/uxmess gui` opens the hub above: a tile for each module panel, shown to whoever holds that panel's own
node. [docs/user/management-hub.md](docs/user/management-hub.md) lists every tile and the modules that have no panel.

### Holograms and NPCs without another plugin

Holograms are native Display entities: text, items and blocks, scaled, turned and shown to whom you choose.
NPCs are fake players with any skin, a click command and a pose. Both are made in game with `/hologram` and
`/npc`, or in their menus.

### An economy other plugins can use

Several currencies, payments, bank deposits and loans, tax, `/sell` with item worths and a balance top list. It
publishes itself to Vault, so every shop and job plugin pays in it.

### One server or a network

Thirteen modules keep their data in step across servers: the economy, homes, warps, player warps, vaults, bans
and mutes, ignore lists, vanish, guard regions, holograms, NPCs, trades and votes. Some things also act across
servers: a player warp on another server takes the player there, two players on different servers can trade, a ban
kicks the player wherever they are, and vanish hides a player on every server. Add the Velocity jar on the proxy,
or the Redis jar with no proxy at all. Optional jars bring a Discord bridge and a REST and WebSocket API.
[docs/user/network.md](docs/user/network.md) lists what each module shares and what stays on one server.

### Moving in from another plugin

With the migration module switched on, `/uxmess import` reads EssentialsX (homes, balances, mail, warps, kits from
one merged `kits.yml`, jail sentences and mutes; not the jail locations in `jail.yml`), LiteBans (bans, mutes and
warnings), player warps from AxPlayerWarps, AthelionPlayerWarps and OlziePlayerWarps, holograms from
DecentHolograms and FancyHolograms, worlds from Multiverse, and balances from PlayerPoints or the running Vault
economy. Guard imports WorldGuard regions with a dry run and an undo of the last run.
[docs/user/import.md](docs/user/import.md) says what each importer reads and what it leaves behind.

Every line a player reads is in `messages/`, in twelve languages: English, Turkish, German, Spanish, French,
Portuguese, Russian, Ukrainian, Polish, Japanese, Korean and Chinese.

## Install

1. Download `uxmEssentials-<version>.jar` from the [latest release](https://github.com/UXPLIMA/uxm-essentials/releases/latest)
   and check it against `SHA256SUMS`.
2. Put it in `plugins/` and start the server.
3. Turn modules on and off with `enabled` in `plugins/uxmEssentials/modules/<module>/config.conf`, then restart
   the server; `/uxmess reload` applies the other settings. `/uxmess doctor` checks the setup.

Paper or Folia 26.2 or later, and Java 25. SQLite out of the box; MySQL, MariaDB and PostgreSQL with the
`storage` block in `config.conf`. The files it writes are listed in [docs/user/reference.md](docs/user/reference.md).

## Build

```sh
./gradlew build       # format check, static analysis, the shaded jar and its check
./gradlew runServer   # a local Paper server with the plugin
```

More in [docs/README.md](docs/README.md). Every picture on this page was taken in game on a test server.

By [UXPLIMA](https://github.com/UXPLIMA) · GPL-3.0
