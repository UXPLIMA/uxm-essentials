# Overview

uxmEssentials is split into the modules `api`, `bukkit-api`, `core`, `persistence-adapter`, `migration`, `bukkit-adapter`, `velocity-adapter`, `discord-adapter`, `redis-adapter`, `rest-adapter`. The Paper plugin in `bukkit-adapter` follows the hexagonal layout of the UXPLIMA plugin template under `com.uxplima.uxmessentials`:

- `bootstrap`
- `bossbar`
- `commandcontrol`
- `communication`
- `customcommands`
- `custommenus`
- `economy`
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
- `regions`
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
