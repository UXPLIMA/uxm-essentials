# Moderation

The moderation module bans, mutes, warns, kicks, jails and freezes players, keeps a history of every sanction, and
can lock the server so that only staff may join. Its settings are in
`plugins/uxmEssentials/modules/moderation/config.conf`. Every ban, mute, warning and jail is kept in the database,
so on a network a sanction holds on every server that shares it.

## Commands

Every node below defaults to operators. The full list, with the review and module nodes, is in
[permissions-server.md](permissions-server.md#moderation).

| Command | Permission | What it does |
|---|---|---|
| `/ban <player> [reason]` | `uxmessentials.moderation.ban` | Bans a player permanently. |
| `/tempban <player> <duration> [reason]` | `uxmessentials.moderation.tempban` | Bans a player for a time. |
| `/unban <player>` | `uxmessentials.moderation.ban` | Lifts a ban, permanent or timed. |
| `/mute <player> [duration] [reason]` | `uxmessentials.moderation.mute` | Mutes a player; no duration is a permanent mute. |
| `/tempmute <player> <duration> [reason]` | `uxmessentials.moderation.mute` | Mutes a player for a time. |
| `/unmute <player>` | `uxmessentials.moderation.unmute` | Lifts a mute. |
| `/warn <player> [reason]` | `uxmessentials.moderation.warn` | Gives a warning that stays until cleared. |
| `/tempwarn <player> <duration> [reason]` | `uxmessentials.moderation.warn` | Gives a warning that lapses after a time. |
| `/warns <player>` | `uxmessentials.moderation.warn` | Lists the warnings still in effect. |
| `/unwarn <player>` | `uxmessentials.moderation.warn` | Clears all of a player's warnings. |
| `/kick <player> [reason]` | `uxmessentials.moderation.kick` | Kicks a player. |
| `/kickall [reason]` | `uxmessentials.moderation.kick` | Kicks everyone on this server except you and exempt players. |
| `/punish <player> <template>` | `uxmessentials.moderation.templates` | Applies a configured template (see [Templates](#templates)). |
| `/banip <player\|ip> [reason]` | `uxmessentials.moderation.banip` | Bans an address permanently. |
| `/tempbanip <player\|ip> <duration> [reason]` | `uxmessentials.moderation.banip` | Bans an address for a time. |
| `/unbanip <ip>` | `uxmessentials.moderation.banip` | Lifts an IP ban. |
| `/jail <player> <jail> [duration] [reason]` | `uxmessentials.moderation.jail` | Puts a player in a jail. |
| `/unjail <player>` | `uxmessentials.moderation.unjail` | Releases a jailed player. |
| `/togglejail <player> [jail] [reason]` | `uxmessentials.moderation.togglejail` | Releases the player if jailed, otherwise jails them. |
| `/setjail <name> [at <world> <x> <y> <z> [yaw pitch]]` | `uxmessentials.moderation.jail` | Defines a jail at your position or at the one given. |
| `/jail del <name>` | `uxmessentials.moderation.jail` | Deletes a jail made with `/setjail`. |
| `/jails`, `/jailedplayers` | `uxmessentials.moderation.jail` | Lists the jails, and the players in them. |
| `/freeze <player>`, `/unfreeze <player>` | `uxmessentials.moderation.freeze` | Holds a player in place, and lets them go. |
| `/lockdown [on\|off]` | `uxmessentials.moderation.lockdown` | Lets only bypass holders log in. No argument flips it. |

The lookup commands are listed under [History and lookups](#history-and-lookups).

## Durations and reasons

A duration is a number and a unit with no space, and units can be joined: `30m`, `2h`, `1d12h`, `2w`. The units
are `s`, `m`, `h`, `d` and `w` (seven days). There are no months or years; write `4w` or `52w` instead.

Where a duration is optional (`/mute` and `/jail`), `perm` or `permanent` means no end. The second argument of
`/mute` is always read as the duration, so a permanent mute with a reason is written with `perm`:

```
/mute Alex perm spamming in chat
/tempban Alex 7d griefing at spawn
/jail Alex spawnjail 30m
```

`/tempban`, `/tempmute`, `/tempwarn` and `/tempbanip` need a real duration and refuse `perm`.

The reason is everything after the last argument, spaces included. It is stored with the sanction, shown to the
player and written to the history.

### How long staff may punish

Grant `uxmessentials.moderation.ban.maxduration.<seconds>` or `uxmessentials.moderation.mute.maxduration.<seconds>`
to cap how long a staff member may ban or mute. The largest number held wins, and without a node there is no cap. A
longer request is shortened to the cap and the staff member is told. The ban cap also applies to `/ban` and to a
template: a capped staff member's permanent ban becomes a timed ban of the capped length. Jails are not capped.

```
/lp group helper permission set uxmessentials.moderation.mute.maxduration.86400
/lp group mod permission set uxmessentials.moderation.ban.maxduration.604800
```

## Who cannot be punished

A player holding `uxmessentials.bypass.punishment` cannot be banned, tempbanned, muted, warned, kicked, jailed or
frozen, by anyone: the command refuses and says so. The node defaults to operators. A holder is also left online by
`/kickall` and by an IP ban on their address, may still log in from a banned address, and is not stopped in chat or
by `muted-blocked-commands` while muted.

## Bans

`/ban` and `/tempban` kick the player at once and refuse every later login until the ban ends or `/unban` lifts it.
A new ban on a player who is already banned replaces the old one. The player sees the reason, and for a timed ban
how long is left, on the kick screen.

With `address-strictness = "STRICT"`, a `/ban` also IP-bans every address the player is known to have connected
from, so they cannot come back on a new account from the same connection. The default `NORMAL` bans only the
account. STRICT catches anyone else on those addresses too (a household, a school network), so leave it off unless
you want that. If the ban was shortened by a duration cap, the IP bans end with it.

## Mutes

A muted player cannot chat, and cannot use this plugin's private messages, mail or helpop. The commands in
`muted-blocked-commands` are also refused while muted, which covers other plugins' and the game's own message
commands. Write each as the bare command word:

```
muted-blocked-commands = ["me", "msg", "tell", "w", "whisper", "mail", "helpop", "r", "reply"]
```

`/mute` refuses a player who is already muted; `/unmute` first to change a mute. A timed mute ends on its own.

## Warnings and the escalation ladder

`/warn` adds a warning that stays until `/unwarn`; `/tempwarn` adds one that lapses after its duration. Only
warnings still in effect count. The staff member is told the player's new total.

When a warning brings the total to a number listed in `warnings.actions`, a sanction is applied on its own, issued
by the server rather than the staff member:

```
warnings {
  actions = ["3:tempmute:1h", "5:tempban:1d"]
}
```

Each step is `count:action[:duration]`:

| Action | What it does | Duration |
|---|---|---|
| `mute` | Permanent mute. | none |
| `tempmute` | Timed mute. | required |
| `kick` | Kick. | none |
| `tempban` | Timed ban. | required |
| `ban` | Permanent ban. | none |

A step fires when the total equals its count exactly, so with the default a player is muted for an hour at their
third warning and banned for a day at their fifth, and nothing happens at the fourth. A warning given with `-s`
escalates silently. A step with a bad count, an unknown action or a missing duration is skipped with a warning in
the log. An empty list turns escalation off.

## Kicks

`/kick` disconnects a player on this server with an optional reason. On a network, a player connected to another
server cannot be kicked from here; the command says which server they are on. `/kickall` kicks everyone on this
server but you and exempt players.

## Jail

A jail is a named place. A jailed player is taken there, and taken back every time they join, until the sentence
ends or `/unjail` releases them. A release teleports them to the spawn of the world they are in.

### Defining jails

`/setjail <name>` saves your position as a jail, or moves a jail of that name. `/setjail <name> at <world> <x> <y>
<z>` saves a position you type, which also works from the console. These jails are kept in the database and removed
with `/jail del <name>`. Names are not case sensitive.

A jail can also be written in the config. It must be listed in `jails` and given a place in a `jail` block:

```
jails = ["spawnjail"]
jail {
  spawnjail { world = "world", x = 0.0, y = 64.0, z = 0.0, countdown = "online-only" }
}
```

`/jails` lists both kinds. When both have the same name, the one made with `/setjail` wins. A config jail is removed
by editing the file. `/togglejail` without a jail name uses the first jail in the `jails` list.

### Time

`/jail` without a duration jails until `/unjail`. A timed jail counts down in one of two ways, set by
`jail-countdown` and overridden for one jail by its own `countdown` key:

| Mode | Meaning |
|---|---|
| `online-only` (default) | Only time spent online counts. The time is counted when the player logs out, and a sentence that has run out ends then, so the next join does not send them back. |
| `wall-clock` | The sentence ends at a fixed moment, online or not. The player is not moved when it ends; the jail simply stops holding them. |

`/jail` refuses a player who is already jailed; `/unjail` first to move someone to another jail.

### What a jailed player can do

The jail is not a fenced region. A jailed player can still chat, run commands, build and walk, but cannot use the
plugin's teleports: `/home`, `/warp`, player warps, `/spawn`, `/back`, `/rtp`, teleport requests and teleports to
other servers are all refused. Rejoining puts them back in the cell. Build walls around the cell, or combine the jail
with a mute or a region, if they must not walk out.

### Jails on a network

A jail made with `/setjail` on a networked server under its own `network.server-name` records that server. A
player jailed anywhere on the network is sent to that server by the proxy and put in the cell there, whether they
are online when jailed or join later. `/jail` and `/unjail` work on a player connected to any server.

If the jail's server is not running, a server that has the jail's world loaded applies it locally. Otherwise the
player stays where they are, still jailed (no teleports), is told so, and the move is tried again after 30 seconds,
then after doubling waits of up to 5 minutes, until it works or they are released. Holders of
`uxmessentials.moderation.broadcast.receive` on every server are told, at most once every 5 minutes for each player.
A frozen player is not sent away until they are unfrozen.

A jail set on a standalone server, or under the shipped name `server-1`, records no server. The first networked
start under a real name claims every such jail whose world is loaded there, and logs `jail_server_claim`. A
`jail_server_mismatch` warning names jails whose world is loaded on one server while the jail names another; run
`/setjail <jail>` on the server that should hold it. A config jail never records a server: it is applied on whichever
server holds the player, and does nothing there if that server lacks its world.

## IP bans

`/banip <ip>` bans that address. `/banip <player>` bans the last address the player connected from, and fails if
none is on record. IPv4 and IPv6 are both accepted and stored in canonical form. A ban matches one exact address;
there are no ranges or wildcards.

An IP ban kicks everyone online from that address except the staff member who issued it and exempt players, and
refuses later logins from it. The staff member is told how many other known accounts have used the address. IP bans
are not broadcast and take no `-s` flag.

Behind a proxy, the address is the player's own one forwarded by the proxy. Turn on player info forwarding, or
every player arrives from the proxy's address and nothing matches.

## Freeze

A frozen player cannot move to another block; they can still look around, chat and run commands. They are told why
when they try to walk, at most once every `freeze.notice-interval-seconds` (3 by default; 0 sends nothing).

A freeze is kept in memory on the server it was given on. It lasts until `/unfreeze` or until the server restarts
or the module reloads, it is not stored in the database, and it does not follow the player to another server. On a
network you can only freeze a player connected to your server.

## Lockdown

`/lockdown on` refuses every login except from holders of `uxmessentials.bypass.lockdown`, with a "server is locked
down" screen. Players already online stay. `/lockdown off` opens logins again, and `/lockdown` alone flips the state.
Holders of `uxmessentials.moderation.broadcast.receive` on this server are told of every change.

The flag is stored in the database and survives a restart. It is a single setting in that database, so on a network
that shares one database a lockdown covers every server.

## Templates

A template is a preset reason with an optional duration, applied with `/punish <player> <template>`:

```
templates {
  griefing { reason = "Griefing", duration = "7d" }
  cheating { reason = "Cheating" }
}
```

A template with a duration is a tempban, one without is a permanent ban. Exemption, the kick, the history, duration
caps and the broadcast work as with `/ban` and `/tempban`. `/punish` has no `-s` flag; it follows
`broadcast.silent-by-default`. A template with no reason or a bad duration is skipped with a warning in the log.

## Staff broadcast and silent sanctions

A ban, tempban, mute, warn or kick sends a one-line notice to every player on this server holding
`uxmessentials.moderation.broadcast.receive`, and to the console. Start the reason with `-s` to leave it out for
that one command; the flag is removed from the stored reason:

```
/ban Alex -s alt account
/tempmute Alex 1h -s
```

`broadcast.silent-by-default = true` makes every sanction silent. The staff member who ran the command and the
punished player are told either way. Jails, IP bans and lifts are never broadcast. The broadcast stays on the server
the command was run on.

With `discord-notify = true` each punishment also writes a readable line to the audit channel that the optional
Discord bridge forwards. Without the bridge the line only lands in the audit log.

## History and lookups

Every ban, tempban, IP ban, unban, mute, unmute, warning and kick is written to a history that is never edited. Jails
are not in it.

| Command | Permission | What it shows |
|---|---|---|
| `/history <player>` | `uxmessentials.moderation.history` | The player's whole record, newest first (up to 20 rows). |
| `/banhistory <player>` | `uxmessentials.moderation.ban` | Bans and unbans only, with IP bans given by player name. |
| `/mutehistory <player>` | `uxmessentials.moderation.mute` | Mutes and unmutes only. |
| `/sanction <player>` | `uxmessentials.moderation.sanction` | Current mute, jail and ban, and the number of warnings in effect. |
| `/checkban <player>`, `/checkmute <player>` | `uxmessentials.moderation.check` | Whether the player is banned or muted now. |
| `/banlist`, `/mutelist` | `uxmessentials.moderation.banlist`, `uxmessentials.moderation.mutelist` | Bans and mutes in effect (up to 50). |
| `/staffhistory <staff>` | `uxmessentials.moderation.staffhistory` | What one staff member has issued. |
| `/staffrollback <staff> [limit]` | `uxmessentials.moderation.staffrollback` | Lifts the bans and mutes that staff member issued that are still in effect, and clears the warnings they gave. Reads their last 100 actions unless a limit is given. A sanction since replaced by another staff member's is left alone. |
| `/modstats [staff] [days]` | `uxmessentials.moderation.stats` | The most active staff, or one staff member's breakdown. |
| `/seen <player>` | `uxmessentials.moderation.seen` | First join and last seen, or the server the player is on now. |
| `/seenip <player>`, `/alts <player>` | `uxmessentials.moderation.seen` | The last address and the accounts that share it; every account that shared any of the player's addresses. |
| `/commandspy` (`/cspy`) | `uxmessentials.moderation.commandspy` | Shows you the commands other players run. |
| `/sudo <player> <command>` | `uxmessentials.moderation.sudo` | Runs a command as another player. |

`censor-ip-addresses = true` masks the address `/seenip` prints (for example `203.*.*.*`). The stored history is
unchanged, and `/alts` lists accounts by name only.

`/mod` (`uxmessentials.moderation.gui`) opens a menu of the active punishments and each player's history. Run
without arguments, `/ban`, `/tempban`, `/mute`, `/tempmute`, `/warn` and `/banip` open a player picker and a
confirmation, `/jail` opens the jail menu, `/jails` the jail manager, `/jailedplayers` the release list, and
`/banlist` and `/banhistory` their menus.

## Kick screens and appeals

The plugin has no appeal system. The ban, IP ban, kick and lockdown screens are messages (`moderation.ban.kick`,
`moderation.tempban.kick`, `moderation.banip.kick`, `moderation.kick.kicked`, `moderation.lockdown.kick`) in
`messages/messages_<language>.conf`. Add your appeal link or Discord invite there.

## On a network

With `network.enabled = true` every server reads bans, mutes, warnings, jails, IP bans and the lockdown flag from the
shared database, so:

- A ban kicks the player from whichever server they are on, and every server refuses their login.
- An IP ban kicks everyone on that address on every server, exempt players aside.
- A mute is read from the database at every chat line and command, so it applies everywhere at once.
- A jail sends the player to the jail's server, as described in [Jails on a network](#jails-on-a-network).
- `/kick` and `/freeze` act only on players connected to your server.

The database holds bans (a permanent ban is stored as one that ends far in the future), mutes, warnings, jails and
the jails made with `/setjail`, IP bans, last-seen records and the address history, the sanction history and the
lockdown flag. Freezes, command spy and the staff broadcast are local to each server. See [network.md](network.md)
for setting up the network, and [import.md](import.md) for bringing bans, mutes, warnings and jails over from
EssentialsX and LiteBans.

## Settings

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Switches the module on or off. |
| `jails` | `[]` | The config jails, each placed in the `jail` block. |
| `jail-countdown` | `"online-only"` | How timed jails count down: `online-only` or `wall-clock`. |
| `muted-blocked-commands` | see [Mutes](#mutes) | Commands a muted player cannot run. |
| `warnings.actions` | `["3:tempmute:1h", "5:tempban:1d"]` | The escalation ladder. |
| `broadcast.silent-by-default` | `false` | Makes every sanction silent. |
| `freeze.notice-interval-seconds` | `3` | Seconds between "you are frozen" notices; 0 is none. |
| `address-strictness` | `"NORMAL"` | `STRICT` makes `/ban` IP-ban the player's known addresses. |
| `templates` | `{}` | The `/punish` templates. |
| `discord-notify` | `false` | Sends punishment lines to the Discord bridge. |
| `censor-ip-addresses` | `false` | Masks the address in `/seenip`. |
