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

The lookup commands are listed under [History and lookups](#history-and-lookups). A `<player>` may also be a uuid, with or without dashes. A uuid nobody has seen, and on an offline-mode server a Java name nobody has used, names the account it will join as, so a ban, mute, warning or jail waits for it; the staff member reads `moderation.target-never-joined`. A Bedrock name that never joined has no account yet: use the uuid Floodgate gives it.

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
`/kickall` and by an IP ban on their address, may still log in from a banned address, and is not stopped in chat or by
`muted-blocked-commands` while muted. It is read on the player's own thread, and for one on another server from that
server; an offline player, or one whose server does not answer, is read from every source as
[punishment exemption](punishment-exemption.md) explains. With no answer they count as exempt; staff are told why.

## Bans

`/ban` and `/tempban` kick the player at once and refuse every later login until the ban ends or `/unban` lifts it.
The player sees the reason, and for a timed ban how long is left, on the kick screen.

A ban never cuts short one that is already running on that player or address. A `/tempban`, a capped `/ban`, a
`/punish` template, a `/banip` or `/tempbanip`, or an API call whose ban would end before the running one is refused,
and the staff member is told which ban is in force and how long it still runs. A capped staff member is also told
their ban was capped. To shorten a ban, `/unban` or `/unbanip` first. A ban that ends later, or a second permanent
ban, replaces the old one. When two servers of a network ban the same player or address at the same moment, the
longer ban is the one kept.

A permanently banned player sees the `moderation.ban.kick` screen, with no time left on it, when the ban kicks them,
when they are kicked on another server of a network and every time they try to log in again. A timed ban, including a
`/ban` shortened by a duration cap, shows the `moderation.tempban.kick` screen with the time still left. Staff lookups
(`/checkban`, `/banlist`, `/sanction` and the active punishments menu) show a permanent ban's end as permanent. A
`/tempban` of 365000 days or more is refused: that is the span a permanent ban is stored with, so use `/ban`.

With `address-strictness = "STRICT"`, a `/ban` also IP-bans every address the player is known to have connected
from, so they cannot come back on a new account from the same connection. The default `NORMAL` bans only the
account. STRICT catches anyone else on those addresses too (a household, a school network), so leave it off unless
you want that. If the ban was shortened by a duration cap, the IP bans end with it. An address that is already
banned at least as long keeps its own ban, so a capped `/ban` never shortens a permanent `/banip`.

## Mutes

A muted player cannot chat, and cannot use this plugin's private messages, mail or helpop. The commands in
`muted-blocked-commands` are also refused while muted, which covers other plugins' and the game's own message
commands. Write each as the bare command word. It also stops the namespaced forms, such as `/minecraft:msg` and
`/essentials:msg`. An entry blocks whatever command owns that word, under all of its names, whichever plugin
registered it: if a ticket plugin's `/ticketreply` also answers to `/reply`, the entry `reply` blocks `/ticketreply`
too. The server does not list the game's own aliases (`/tell` and `/w` for `/msg`), so name them too, as the default
does.

`muted-allowed-commands` lists words a muted player may always run. It is checked first and matches only the word
written (with or without a namespace), never the command's other names, so it frees exactly that word:

```
muted-blocked-commands = ["me", "msg", "tell", "w", "whisper", "mail", "helpop", "r", "reply"]
muted-allowed-commands = ["ticketreply"]
```

With that, a muted player can run `/ticketreply` but not `/reply`. The listed commands' names are read again when a
plugin is enabled or disabled, the server finishes starting, `/minecraft:reload` or `/uxmess reload` runs; a command
added any other way (a script reload) waits for `/uxmess reload`.

A mute follows the ban rule. A `/mute` or `/tempmute` that ends later than the running mute, or a second permanent
mute, replaces it; one that would end sooner is refused, and the staff member is told how long the running mute still
runs. To shorten a mute, `/unmute` first. When two servers of a network mute the same player at the same moment, the
longer mute is the one kept. A timed mute ends on its own.

Each connected player's mute is kept in memory. Until it is read (just after joining, after another server changes it,
or while the database is down), what a mute stops is refused with a line to try again, though chat still uses a mute
already kept. Failed reads are logged once a minute (`event=mute_check_read_failed`, `online_mute_read_failed`).

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

A step never weakens what is already running. A mute or ban step leaves the player under whichever of its own sanction
and the running one of that kind ends later: a permanent one beats any timed one, and a tie keeps the running one. So
a `tempmute:10m` step extends a mute that has five minutes left, while a `tempban:1d` step on a permanently banned
player leaves the permanent ban in place. A step that is held back this way is written to the audit log as not applied
(`held_by_stronger`). A staff mute or ban that lands while a step is being written is kept by the same rule.

A step's sanction names the warning count that tripped it and that warning's reason
(`moderation.warn-escalation-reason`, or `-bare` when it had none), so `/history` shows what caused it. It is kept as
that line, so each reader reads it in their own language (screens, lookups, lists, menus, the staff broadcast,
placeholders; the API in the server default) and an edit of the line reaches steps already given. The audit log keeps
the punished player's text, cut to 256 characters, as do sanctions from before this was stored. The staff member then
reads what the sanction reports, such as the mute confirmation or why it failed, and a line on how the step landed.

## Kicks

`/kick` disconnects a player on this server with an optional reason. On a network, a player connected to another
server cannot be kicked from here; the command says which server they are on. A player who is not online anywhere
is not kicked, and the command says so. `/kickall` kicks everyone on this server but you and exempt players, and
writes a kick to the history of each player it kicked, the same row `/kick` writes. The kicks and the answer never
wait for the database: the history is written afterwards in one go, and if that fails the log says so.

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
by editing the file.

`/togglejail` without a jail name uses `default-jail`, which can name a config jail or one made with `/setjail`. With
`default-jail` empty (the default) it is refused, and the staff member is shown the jails `/jail` accepts, so nobody
is sent to a jail no one chose. A jailed player is released without naming a jail. A `default-jail` that names no jail
is refused the same way, saying the configured default does not exist, and the log warns about it at startup.

```
default-jail = "spawnjail"
```

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
there are no ranges or wildcards. `/unbanip` accepts any spelling of the address. A ban stored by an older version in
the spelling it was typed with (compressed or upper-case IPv6) is lifted by typing that same spelling.

An IP ban kicks everyone online from that address except the staff member who issued it and exempt players, and
refuses later logins from it. The staff member is told how many other known accounts have used the address. IP bans
are not broadcast and take no `-s` flag.

Behind a proxy, the address is the player's own one forwarded by the proxy. Turn on player info forwarding, or
every player arrives from the proxy's address and nothing matches.

## Freeze

A frozen player cannot move to another block; they can still look around, chat and run commands. They are told why
when they try to walk, at most once every `freeze.notice-interval-seconds` (3 by default; 0 sends nothing).

A freeze is kept in memory and is not stored in the database. It lasts until `/unfreeze`, or until the server
restarts or the module reloads. Logging out does not lift it: a frozen player who logs back in is still frozen. You
can only freeze a player connected to your server.

On a network the freeze is announced over the bus, and every server that hears it holds the player frozen too, so
switching server does not lift it. `/unfreeze` on any server lifts it everywhere. With the proxy plugin, a frozen
player cannot change server at all (`/server` and server selectors are refused with a line saying why), and one who
logs out frozen is sent back to the server that froze them when they return. A proxy restart forgets the freezes it
saw; the servers still hold theirs.

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
are not in it. `/kickall` writes one kick for each player it kicked. A STRICT `/ban` writes one IP ban for each address
it banned, naming the player; an address that kept its own longer ban gets no row.

| Command | Permission | What it shows |
|---|---|---|
| `/history <player>` | `uxmessentials.moderation.history` | The player's whole record, newest first (up to 20 rows). |
| `/banhistory <player>` | `uxmessentials.moderation.ban` | Bans and unbans only, with IP bans given by player name. |
| `/mutehistory <player>` | `uxmessentials.moderation.mute` | Mutes and unmutes only. |
| `/sanction <player>` | `uxmessentials.moderation.sanction` | Current mute, jail and ban, and the number of warnings in effect. |
| `/checkban <player>`, `/checkmute <player>` | `uxmessentials.moderation.check` | Whether the player is banned or muted now. |
| `/banlist`, `/mutelist` | `uxmessentials.moderation.banlist`, `uxmessentials.moderation.mutelist` | Bans and mutes in effect (up to 50). |
| `/staffhistory <staff>` | `uxmessentials.moderation.staffhistory` | What one staff member has issued. |
| `/staffrollback <staff> [limit]` | `uxmessentials.moderation.staffrollback` | Lifts the bans and mutes that staff member issued that are still in effect, and clears the warnings they gave. Reads their last 100 player bans, mutes and warnings unless a limit is given (kicks, lifts and IP bans are not read and do not count), at most 500; a larger limit is lowered to 500 and the command says so. A sanction since replaced by another staff member's is left alone. |
| `/modstats [staff] [days]` | `uxmessentials.moderation.stats` | The most active staff, or one staff member's breakdown, counted over the whole window. A `/banip` or `/tempbanip` counts as a ban, the addresses a STRICT `/ban` adds do not (the ban counts once), and a `/kickall` counts as one kick. |
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

## How a login is checked

The bans, the IP ban on the player's address and the lockdown flag are read from the database while the player
connects, before the server thread is involved. The decision follows a moment later, once the player's permissions
are known, so a login never makes the server wait on the database. A login is never let in unchecked: when the read
fails, or the decision finds no read for that connection (or one more than 30 seconds old), the player is refused with
`moderation.login.unchecked`, which asks them to join again, and a line is written to the log.
On an offline-mode server (no online-mode proxy in front) each spelling of a name is its own account, so `Steve` and `STEVE` log in with two uuids. A ban, a mute or a jail on one follows the player into another spelling: the login is refused with the banned account's ban screen, and a mute or a jail is copied onto the account once its login is allowed, so every server holds it and it runs out when the original does. A copied mute reads "inherited from" the original holder in its reason and in `/mutehistory`. Lifting the original (`/unmute`, `/unjail`, a served sentence, the menu or the API) lifts the copies it made, and a longer mute reaches them; lifting a copy lifts that copy alone, for good. A copy is never copied on, so it cannot come back to the account it came from. Bans are not copied: the login reads the original ban, so `/unban` frees every spelling. By default (`case-variant-sanctions = "newer-or-address"`) a sanction reaches an account with no trace from before it was issued (a completed join, the name list, the server's own player file) and any account that ever connected from an address the sanctioned one used, however old; `"newer"` leaves out the address, `"all"` reaches every spelling. Only an address that tells people apart counts: never loopback (Geyser without Floodgate), private, link-local or carrier-grade NAT addresses, nor one more than `case-variant-address.busy-after` (3) accounts used, nor one listed in `case-variant-address.ignore`, where your proxy's public address belongs. An account already playing under its own spelling from elsewhere is somebody else. A refused login creates no seen row. When a ban lands, the spellings it reaches that are connected at that moment are kicked too, on every server. With `refuse-name-case-variants = true` a new account whose name only differs in case from one the server already knows is refused too, sanction or not, with `moderation.login.name-variant`, which names the spelling to use; accounts that already joined keep joining. With authenticated logins a name has one owner and none of this applies.

## Kick screens and appeals

The plugin has no appeal system. The ban, IP ban, kick and lockdown screens are messages (`moderation.ban.kick`,
`moderation.tempban.kick`, `moderation.banip.kick`, `moderation.kick.kicked`, `moderation.lockdown.kick`) in
`messages/messages_<language>.conf`. Add your appeal link or Discord invite there, in both `moderation.ban.kick` (permanent bans) and `moderation.tempban.kick` (timed bans).

## On a network

With `network.enabled = true` every server reads bans, mutes, warnings, jails, IP bans and the lockdown flag from the
shared database, so a sanction holds on every server: a ban or an IP ban kicks the player wherever they are and every
server refuses their login, a mute applies at once, and a jail sends the player to the jail's server (see
[Jails on a network](#jails-on-a-network)). `/kick` and `/freeze` act only on players connected to your server; a
freeze then holds on every server. Freezes are held in memory; command spy and the staff broadcast stay on one
server. [network.md](network.md#moderation) has the details and the setup, and [import.md](import.md) brings bans,
mutes, warnings and jails over from EssentialsX and LiteBans.

## Settings

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Switches the module on or off. |
| `jails` | `[]` | The config jails, each placed in the `jail` block. |
| `default-jail` | `""` | The jail `/togglejail` uses when no jail is named; empty refuses and lists the jails. |
| `jail-countdown` | `"online-only"` | How timed jails count down: `online-only` or `wall-clock`. |
| `muted-blocked-commands` | see [Mutes](#mutes) | Commands a muted player cannot run. |
| `muted-allowed-commands` | `[]` | Words a muted player may always run, checked first. |
| `warnings.actions` | `["3:tempmute:1h", "5:tempban:1d"]` | The escalation ladder. |
| `broadcast.silent-by-default` | `false` | Makes every sanction silent. |
| `freeze.notice-interval-seconds` | `3` | Seconds between "you are frozen" notices; 0 is none. |
| `address-strictness` | `"NORMAL"` | `STRICT` makes `/ban` IP-ban the player's known addresses. |
| `templates` | `{}` | The `/punish` templates. |
| `discord-notify` | `false` | Sends punishment lines to the Discord bridge. |
| `censor-ip-addresses` | `false` | Masks the address in `/seenip`. |
| `refuse-name-case-variants` | `false` | Offline mode: refuses a new account named as a case variant of a known one. |
| `case-variant-sanctions` | `"newer-or-address"` | Offline mode: which other spellings a sanction reaches: `newer`, `newer-or-address` or `all`. |
