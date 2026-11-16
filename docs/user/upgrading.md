# Upgrading

What an operator edits when a release renames something an operator's own files or permission grants depend on, or
needs a setting it did not need before.

## Velocity bus frames need a shared secret

A network on `network.transport = "velocity"` or `"both"` now signs every frame it sends through the proxy, so a
player's client cannot write one. Until `network.bus.secret` is set, those carriers send and accept nothing through
the proxy: cross-server messages, presence, teleport requests and cache updates stop on that carrier. The start log
names the key and `/uxmess doctor` reads `proxy OFF`. Redis is not affected.

1. Make one secret of 32 or more random characters, for example with `openssl rand -hex 32`. An example value from
   the documentation, fewer than 8 different characters, or a short pattern repeated, is refused like an empty one.
2. Put it in `config.conf` of every backend, under `network.bus.secret`. The update adds the empty key to your file.
   The proxy needs nothing new.
3. Upgrade every backend and the proxy jar together, then restart. A backend still on the old release sends unsigned
   frames, which the upgraded ones refuse; it still reads the signed frames they send.
4. Run `/uxmess doctor` on each backend once players are on: the bus line reads connected without `REFUSED`.

Keep the clocks of the backends in time: a frame signed more than 60 seconds away from the receiving clock is
refused. See [signed frames](network.md#signed-frames).

## Server warps are open to every player

`uxmessentials.warp.use.<warp>` is asked for every server warp. It is meant to default to every player, but the
server was never told about the node, so it answered operators only and a player who was not an operator could use
no server warp and saw none in `/warp list`. A node like it that nobody granted or denied now takes the default the
permission pages list, so every server warp is open to every player after the upgrade.

To keep a warp closed:

- give the warp a required permission, a node of your own (the `required_permission` column, see
  [warps.md](warps.md#costs-and-refunds)); not one under `uxmessentials.warp.use.`, which every player holds;
- or set `uxmessentials.warp.use.<warp>` to false for the groups that must stay out;
- or set `uxmessentials.warp.use.*` to false and grant single warps back.

A node completed from a name, such as a warp, a kit or a world, now takes its listed default even where the permission
plugin does not resolve wildcards, and a `.*` above it that you set still decides first. Without a permission plugin
that resolves wildcards, a literal `uxmessentials.*` grant therefore reaches every such named node but no fixed node.

## Cooldown tiers set the wait

A `<feature>.cooldown.<seconds>` node a player holds used to be compared with the wait in the config, and the shorter
one won, so a node could only shorten a wait. A held node now is the wait, the shortest node held wins, and the config
value applies only to a player who holds none. This covers poses, skin, `/rtp`, the shared teleport clock, custom
commands and the menu `cooldown:` labels ([permission pages](permissions-gameplay.md)). Three things change:

- A node longer than the config value now lengthens the wait. A `uxmessentials.poses.cooldown.30` that did nothing
  before now makes its holder wait 30 seconds between poses.
- Changing the config value no longer reaches a player who holds a node. Their node decides.
- A kit's `permission-cooldowns` group named by a number, such as `"60" = 60`, is skipped with a warning in the log:
  its node is also the kit cooldown node `uxmessentials.kit.cooldown.60`, so its holders got a 60 second cap on every
  kit. The entry stays in the kit file; rename the group to a word and move the grant.

Kits, a teleport command with its own `cooldowns.<command>` value and a warp with its own cooldown are not affected:
one node covers all of them, so there it still only lowers each value ([teleport](teleport.md#cooldowns),
[kits](kits.md)).

An example with LuckPerms: the `default` group holds `uxmessentials.tp.cooldown.10` and `default-cooldown` was lowered
to 5. Before the upgrade everyone waited 5 seconds; after it every rank that inherits `default` waits 10.

What to check, for each group (`lp group <group> permission info`, starting with `default`) and for single players:
look for `cooldown.<seconds>` nodes longer than the config value of their feature. Lower the node or remove it, and
let the config value apply again.

## Exemptions and placeholders follow the shared names

Every exemption node now starts with `uxmessentials.bypass.`, and every placeholder reads `%uxm_essentials_<key>%`,
the names every UXM plugin uses.

### What to edit

1. Move every grant of an old exemption node to its new name in your permission plugin. Bukkit has no node
   aliases, so an old grant simply stops working; the plugin cannot carry it over for you. The LuckPerms commands are
   [below](#moving-the-grants-with-luckperms).
2. Replace `%uxmessentials_` with `%uxm_essentials_`, and `%rel_uxmessentials_` with `%rel_uxm_essentials_`, wherever
   a placeholder of this plugin is written: this plugin's own files under `plugins/uxmEssentials/` and the files of
   every other plugin that shows one (tab list, scoreboard, hologram, chat, menu or Discord plugins).
3. Edit by hand any old node written as a requirement: a menu's `permission:` condition, a custom command's
   `requirements` or `permission`, or a node another plugin checks on our behalf. Those strings are passed to the
   permission check as written and are not translated.
4. Nothing to do for `pay.tax.bypass-permission`, `baltop.exempt-permission` and `spawn.exempt-permission`: a file
   that still names an old node there is read as the new one. A node of your own in those settings is kept as written.

### What the new names change for wildcards

- A feature wildcard no longer reaches its exemptions. `uxmessentials.home.*` used to include
  `uxmessentials.home.bypass.cost`; it does not include `uxmessentials.bypass.cost.home`. Grant the exemption on its
  own, or a `uxmessentials.bypass.` wildcard.
- The other way round, one wildcard now spans features. `uxmessentials.bypass.cost.*` waives the vault, home, player
  warp, custom command and per-command charges together; `uxmessentials.bypass.safety.*` lands at every unsafe
  destination; `uxmessentials.bypass.*` lifts every rule. Check who holds such a wildcard before you grant it.

### The old placeholders keep answering for now

`%uxmessentials_<key>%` and `%rel_uxmessentials_<key>%` still answer in this release, so nothing goes blank on the
first start. The first time each key is read, the log names it and what to write instead, which gives you the list
of what to edit:

```text
event=papi_placeholder_deprecated read=%uxmessentials_homes_count% use=%uxm_essentials_homes_count% ...
```

After 256 distinct keys one `papi_placeholder_deprecated_more` line says there are more. The plugin does not rewrite
any file for you: most of these placeholders live in other plugins' files, which are not ours to edit, and a rewrite
of your own files would race with an editor or a reload. A later release removes the old form, so search every plugin
folder for `uxmessentials_` before then.

PlaceholderAPI finds an expansion by the text before the first underscore, so the new form registers the identifier
`uxm`, and PlaceholderAPI keeps one expansion per identifier. If another plugin already holds `uxm`, the log shows
`event=papi_expansion_identifier_taken`, `/uxmess doctor` warns on its placeholders line, and only the old form
answers. If another plugin takes `uxm` later, the log shows `event=papi_expansion_evicted`. Tell us which plugin it is.

### Node names

40 fixed nodes moved:

| Old node | New node |
|---|---|
| `uxmessentials.world.access.bypass` | `uxmessentials.bypass.access.world` |
| `uxmessentials.economy.baltop.exempt` | `uxmessentials.bypass.baltop` |
| `uxmessentials.pwarp.bypass.ban` | `uxmessentials.bypass.ban.pwarp` |
| `uxmessentials.vault.bypass-blacklist` | `uxmessentials.bypass.blacklist.vault` |
| `uxmessentials.world.command-bypass` | `uxmessentials.bypass.blocked-commands.world` |
| `uxmessentials.commandcontrol.channelhide.bypass` | `uxmessentials.bypass.channel-hide` |
| `uxmessentials.communication.chat.bypass` | `uxmessentials.bypass.chat-lock` |
| `uxmessentials.communication.clearchat.exempt` | `uxmessentials.bypass.clear-chat` |
| `uxmessentials.commandcontrol.spam.bypass` | `uxmessentials.bypass.command-spam` |
| `uxmessentials.commandcontrol.bypass` | `uxmessentials.bypass.commandcontrol` |
| `uxmessentials.customcommand.cooldown.bypass` | `uxmessentials.bypass.cooldown.customcommand` |
| `uxmessentials.kit.cooldown.bypass` | `uxmessentials.bypass.cooldown.kit` |
| `uxmessentials.poses.cooldown.bypass` | `uxmessentials.bypass.cooldown.poses` |
| `uxmessentials.rtp.cooldown.bypass` | `uxmessentials.bypass.cooldown.rtp` |
| `uxmessentials.skin.cooldown.bypass` | `uxmessentials.bypass.cooldown.skin` |
| `uxmessentials.tp.cooldown.bypass` | `uxmessentials.bypass.cooldown.tp` |
| `uxmessentials.economy.bypasscmdcost` | `uxmessentials.bypass.cost.command` |
| `uxmessentials.customcommand.cost.bypass` | `uxmessentials.bypass.cost.customcommand` |
| `uxmessentials.home.bypass.cost` | `uxmessentials.bypass.cost.home` |
| `uxmessentials.pwarp.bypass.cost` | `uxmessentials.bypass.cost.pwarp` |
| `uxmessentials.vault.free` | `uxmessentials.bypass.cost.vault` |
| `uxmessentials.guard.bypass.gamemode` | `uxmessentials.bypass.gamemode.guard` |
| `uxmessentials.world.gamemode.bypass` | `uxmessentials.bypass.gamemode.world` |
| `uxmessentials.guard.bypass` | `uxmessentials.bypass.guard` |
| `uxmessentials.spawn.join.exempt` | `uxmessentials.bypass.join-spawn` |
| `uxmessentials.warp.bypass.lock` | `uxmessentials.bypass.lock.warp` |
| `uxmessentials.moderation.lockdown.bypass` | `uxmessentials.bypass.lockdown` |
| `uxmessentials.pwarp.bypass.password` | `uxmessentials.bypass.password.pwarp` |
| `uxmessentials.warp.bypass.password` | `uxmessentials.bypass.password.warp` |
| `uxmessentials.moderation.exempt` | `uxmessentials.bypass.punishment` |
| `uxmessentials.home.bypass.unsafe` | `uxmessentials.bypass.safety.home` |
| `uxmessentials.pwarp.bypass.safety` | `uxmessentials.bypass.safety.pwarp` |
| `uxmessentials.warp.bypass.safety` | `uxmessentials.bypass.safety.warp` |
| `uxmessentials.world.bypass.safety` | `uxmessentials.bypass.safety.world` |
| `uxmessentials.security.bypass` | `uxmessentials.bypass.security` |
| `uxmessentials.economy.tax.bypass` | `uxmessentials.bypass.tax` |
| `uxmessentials.world.voidrescue.exempt` | `uxmessentials.bypass.void-rescue` |
| `uxmessentials.customcommand.warmup.bypass` | `uxmessentials.bypass.warmup.customcommand` |
| `uxmessentials.tp.warmup.bypass` | `uxmessentials.bypass.warmup.tp` |
| `uxmessentials.pwarp.bypass.whitelist` | `uxmessentials.bypass.whitelist.pwarp` |

A cooldown label an operator chose keeps its name and moves to its own branch, so a label can never lift a feature's
cooldown: `uxmessentials.cooldown.bypass.<label>` is now `uxmessentials.bypass.cooldown.label.<label>`, for example
`uxmessentials.bypass.cooldown.label.customcommand.example` for the custom command `example`.

Earlier pages suggested `uxmessentials.cooldown.bypass.tp`, `.rtp`, `.kit` or `.poses` for a feature's cooldown. That
node only ever skipped a menu or custom command label of that name, never the feature, so it maps to
`uxmessentials.bypass.cooldown.label.<name>` like any other label. To skip a feature's cooldown, grant
`uxmessentials.bypass.cooldown.<feature>`, such as `uxmessentials.bypass.cooldown.tp`. `uxmessentials.bypass.cooldown.*`
covers every feature and every label together.

### Moving the grants with LuckPerms

`bulkupdate` changes every user and group at once. Run each line in the console, then confirm it with the
`/lp bulkupdate confirm <id>` line LuckPerms prints. Take a backup first with `/lp export before-bypass-rename`.

```text
/lp bulkupdate all update permission uxmessentials.bypass.access.world "permission == uxmessentials.world.access.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.baltop "permission == uxmessentials.economy.baltop.exempt"
/lp bulkupdate all update permission uxmessentials.bypass.ban.pwarp "permission == uxmessentials.pwarp.bypass.ban"
/lp bulkupdate all update permission uxmessentials.bypass.blacklist.vault "permission == uxmessentials.vault.bypass-blacklist"
/lp bulkupdate all update permission uxmessentials.bypass.blocked-commands.world "permission == uxmessentials.world.command-bypass"
/lp bulkupdate all update permission uxmessentials.bypass.channel-hide "permission == uxmessentials.commandcontrol.channelhide.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.chat-lock "permission == uxmessentials.communication.chat.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.clear-chat "permission == uxmessentials.communication.clearchat.exempt"
/lp bulkupdate all update permission uxmessentials.bypass.command-spam "permission == uxmessentials.commandcontrol.spam.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.commandcontrol "permission == uxmessentials.commandcontrol.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.cooldown.customcommand "permission == uxmessentials.customcommand.cooldown.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.cooldown.kit "permission == uxmessentials.kit.cooldown.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.cooldown.poses "permission == uxmessentials.poses.cooldown.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.cooldown.rtp "permission == uxmessentials.rtp.cooldown.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.cooldown.skin "permission == uxmessentials.skin.cooldown.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.cooldown.tp "permission == uxmessentials.tp.cooldown.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.cost.command "permission == uxmessentials.economy.bypasscmdcost"
/lp bulkupdate all update permission uxmessentials.bypass.cost.customcommand "permission == uxmessentials.customcommand.cost.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.cost.home "permission == uxmessentials.home.bypass.cost"
/lp bulkupdate all update permission uxmessentials.bypass.cost.pwarp "permission == uxmessentials.pwarp.bypass.cost"
/lp bulkupdate all update permission uxmessentials.bypass.cost.vault "permission == uxmessentials.vault.free"
/lp bulkupdate all update permission uxmessentials.bypass.gamemode.guard "permission == uxmessentials.guard.bypass.gamemode"
/lp bulkupdate all update permission uxmessentials.bypass.gamemode.world "permission == uxmessentials.world.gamemode.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.guard "permission == uxmessentials.guard.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.join-spawn "permission == uxmessentials.spawn.join.exempt"
/lp bulkupdate all update permission uxmessentials.bypass.lock.warp "permission == uxmessentials.warp.bypass.lock"
/lp bulkupdate all update permission uxmessentials.bypass.lockdown "permission == uxmessentials.moderation.lockdown.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.password.pwarp "permission == uxmessentials.pwarp.bypass.password"
/lp bulkupdate all update permission uxmessentials.bypass.password.warp "permission == uxmessentials.warp.bypass.password"
/lp bulkupdate all update permission uxmessentials.bypass.punishment "permission == uxmessentials.moderation.exempt"
/lp bulkupdate all update permission uxmessentials.bypass.safety.home "permission == uxmessentials.home.bypass.unsafe"
/lp bulkupdate all update permission uxmessentials.bypass.safety.pwarp "permission == uxmessentials.pwarp.bypass.safety"
/lp bulkupdate all update permission uxmessentials.bypass.safety.warp "permission == uxmessentials.warp.bypass.safety"
/lp bulkupdate all update permission uxmessentials.bypass.safety.world "permission == uxmessentials.world.bypass.safety"
/lp bulkupdate all update permission uxmessentials.bypass.security "permission == uxmessentials.security.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.tax "permission == uxmessentials.economy.tax.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.void-rescue "permission == uxmessentials.world.voidrescue.exempt"
/lp bulkupdate all update permission uxmessentials.bypass.warmup.customcommand "permission == uxmessentials.customcommand.warmup.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.warmup.tp "permission == uxmessentials.tp.warmup.bypass"
/lp bulkupdate all update permission uxmessentials.bypass.whitelist.pwarp "permission == uxmessentials.pwarp.bypass.whitelist"
```

A cooldown label you granted on its own needs its own line, for example:

```text
/lp bulkupdate all update permission uxmessentials.bypass.cooldown.label.daily "permission == uxmessentials.cooldown.bypass.daily"
```

To move one group by hand instead:

```text
/lp group staff permission unset uxmessentials.economy.tax.bypass
/lp group staff permission set uxmessentials.bypass.tax true
```

On a network, run the commands against every LuckPerms storage your servers use: once per database that backends
share, and on each server that keeps its own. The proxy checks `uxmessentials.bypass.commandcontrol` and
`uxmessentials.bypass.command-spam` itself, so the proxy's LuckPerms needs those two moved as well.

`/uxmess permissions` lists every node the running build checks.
