# Overview

uxmEssentials is split into the modules `api`, `bukkit-api`, `core`, `persistence-adapter`, `migration`, `bukkit-adapter`, `velocity-adapter`, `discord-adapter`, `redis-adapter`, `rest-adapter`. The Paper plugin in `bukkit-adapter` follows the hexagonal layout of the UXPLIMA plugin template under `com.uxplima.uxmessentials`:

- `bootstrap`
- `bossbar`
- `commandcontrol`
- `communication`
- `customcommands`
- `custommenus`
- `economy`
- `guard`
- `holograms`
- `homes`
- `invrollback`
- `itemworld`
- `kits`
- `messaging`
- `migration`
- `moderation`
- `nametags`
- `npc`
- `playerstate`
- `playerwarps`
- `poses`
- `presence`
- `ranks`
- `scoreboard`
- `security`
- `servertweaks`
- `shared`
- `skin`
- `staff`
- `survival`
- `tablist`
- `teleport`
- `trade`
- `vanish`
- `vaults`
- `villagers`
- `vote`
- `warps`
- `worlds`

- `domain` holds values and rules and imports nothing from Paper.
- `application` holds the use cases and the ports they need.
- `adapter` turns commands, events and menus into use case calls and implements the ports with Paper,
  uxmLib and storage.
- `bootstrap` is the composition root: every object is built there and receives its collaborators through
  its constructor.

Configuration is read into immutable snapshots and swapped whole on reload. Work that touches files or a
database runs on the uxmLib `Scheduler` async lane; work on a player or a block runs on its region thread.

## Player names

A name a sender types is resolved through the kernel `PlayerLookup`: the players connected here, then the plugin's
own name index (every account seen on any server of the database), then the server's user cache. On an offline-mode
server `Steve` and `steve` are two accounts with two uuids and Paper lets both play at once, so the indexes keep every
account under a lower-cased name. A spelling that matches one account exactly is that account; a name several
accounts hold only in other cases is answered with `command.ambiguous-player`, which lists the spellings, and never
with one of them. A name one account holds in another case is that account only when the name store confirms nobody
else holds it, since the memory keeps only the most recently seen names; the store is read off the tick only, so on
the tick such a name answers unknown. The user cache matches any case and is held to the exact spelling. Online
mode keeps one owner per name, the one seen last. Network presence follows the same rule, without the store.

Which mode applies is `LiveNameCase`, read on every question. `lookup.name-case` forces it; on `auto` it starts from
the server's own report (`ServerNameCase`) and switches to sensitive at the first login with a version 3 (name
derived) uuid, because a backend behind an offline-mode proxy still reports Paper's online proxy defaults.
`NameCaseEvidenceListener` feeds it at pre-login, ahead of the login checks. The server's own by-name methods
(`getPlayerExact`, `getOfflinePlayerIfCached`) match in any case and keep one entry per lower-cased name, so they
cannot tell case variants apart; `OnlineByName` resolves a connected player through the lookup instead.

## Migrations

The Flyway scripts under `persistence-adapter/src/main/resources/db/migration` run on every backend and are written
in the DDL SQLite, MySQL/MariaDB and PostgreSQL all accept; the jOOQ classes are generated from them. A limit only one
backend has, such as the 64 KB `TEXT` column of MySQL and MariaDB, is lifted by a script under `db/vendor/<backend>`
(today `db/vendor/mariadb`), which `FlywayMigrationRunner` adds to the locations for that backend only. Such a script
takes the next free version like any other, and only changes storage: never a name, a column or whether a column takes
NULL. `MariadbItemColumnsTest` holds the MariaDB scripts to the shared schema, and fails on a new text column until it
is either widened there or named as one that never holds items.

## With uxmChat

When uxmChat is installed, the chat features both plugins have are left to it: chat format, private
messages, social spy, mail, helpop, the announcer, join, quit and death lines, broadcast, clear chat, `me`,
the chat toggle, ignore, nicknames and the staff list. `UxmChatStepBack` asks uxmChat's API service
(`com.uxplima.chat.api.UxmChat`) which features it runs; uxmChat reports a feature only while its module is
on and its command is enabled, so one it has turned off stays here. The joiner's MOTD and the page sent to a
player who died stay with this plugin either way.

Listeners and the announcer ask on every event. This plugin starts before the worlds and uxmChat after
them, so until the server ticks an installed uxmChat counts as running every chat line; after that, one that
never started runs nothing. Commands are decided when the server builds its command tree, after every
plugin has enabled, so a uxmChat that failed to start takes no command with it. A module switched on either
side reaches the commands on the next command reload, not on a module reload alone. `/help` lists only the
commands this plugin registered. `/mute` stays with this plugin's moderation.
