# Upgrading

What an operator edits when a release renames something an operator's own files or permission grants depend on, or
needs a setting it did not need before.

## Placeholders typed by players stay as typed

With PlaceholderAPI installed, a `%placeholder%` inside a value (a private message, mail, help request, staff chat
line, nickname, item or sign name, or a line relayed from another server) is no longer filled in for the reader; it
shows as typed. Placeholders you write into a message file, the `<prefix>`, an `@key` line's own arguments and a
`/broadcast` line are still filled, and the `<prefix>` now fills its placeholders too.

## Nicks are kept in the database

A `/nick` is kept in the new `player_nicks` table, so it survives a relog and follows the player to every server; it
was lost before. A nick that is another account's name in any case, or another player's nick, is refused; a player may
wear their own name, a Bedrock player's dotted one too. One nick has one holder across the database, so two servers
cannot hand it out at once, and a nick that copied a name before its account first joined is cleared when it joins.
A nick saved in a server's player data by an earlier version is taken over at the player's next join to that server,
under the same rules, when the database holds none; one that is taken is dropped. `/realname` also answers for a nick
whose player is offline or on another server. A server still on an older release does not show the shared nick, so
upgrade every server of the network in the same window.

## Lists name the players of every server

On a network, tab completion, `/list`, `/staff`, `/stafflist`, `staff_online`, the player pickers, `/realname`,
`/whois` and `/mail sendall` now take in the players of every server, and `/broadcast` reaches every server. Each server
announces how deep its players are vanished and whether they are staff. A server still on an older release announces
neither, so until it is upgraded its players are listed only to viewers who see every vanish level, and none of them
counts as staff; upgrade every server of the network in the same window.

## Staff actions reach every server

On a network, `/kick`, `/freeze`, `/security force` and `reset`, `/staffmode <player>` and `/clientinfo` now act on a
player connected to another server, and the security alt and client notices and the staff mode alerts reach staff on
every server. The kick, the freeze and the security change are checked and recorded on the server they were run on and
carried out by the server holding the player. A server still on an older release does not carry them out: a kick is
recorded and announced but the player stays, and `/clientinfo` reports no brand. A player who changes server while a
kick is on its way is not kicked either. Upgrade every server and the proxy plugin in the same window; a proxy upgraded
before its servers keeps a frozen player on the server that froze them, as before.

## Messaging switches follow the player

`/msgtoggle`, `/rtoggle`, `/broadcasttoggle`, `/socialspy` and the `/reply` partner are kept in three new tables
(`player_switches`, `messaging_spy_targets`, `messaging_reply_partners`) and follow the player to every server. Social
spy stays on across a relog, and shows nothing without its node. A server still on an older release keeps its own
copy and does not see the shared one, so upgrade every server of the network in the same window.

## Server switches show no join or quit line

On a network, a player moving between servers no longer shows a join or quit line (vanilla or custom) on either
server and is not sent the MOTD or the unread-mail line again; a quit line waits three seconds to tell a move from
leaving. The first-join
welcome and `first-join` kits come only on a player's first join to the network. To keep the earlier lines, set
`server-switch { hide = false }` in `modules/communication/join-quit.conf`; `count = NETWORK` makes `{count}` count
the whole network.

## Offline mode: names match as spelled

On an offline-mode server, or a network whose proxy runs in offline mode, `Steve` and `steve` are now two players: a
command, the console, a vote and the REST API find a name only as spelled when another account holds it in another
case, and answer with the spellings to choose from (see [network.md](network.md) for `lookup.name-case`). Another
plugin's name-based Vault calls follow the same rule: a deposit or a balance asked for a spelling no account has, when
another case of it names more than one account, now fails instead of reaching one of them. Plugins that pay by uuid
are not affected.

## Offline mode: sanctions follow other spellings of a name

On an offline-mode server a ban, a mute and a jail now follow a player into another spelling of their name, by default
into an account with no trace from before the sanction and one that shared a telling address with the sanctioned
account (`case-variant-sanctions`, `case-variant-address`). The update adds one table,
`moderation_case_copies`, which records the mutes and jails copied that way; the first server to start on the new
release creates it, and nothing needs editing. Upgrade every server that shares the database in the same window: a
server on the previous release does not copy, and does not lift a copy when it lifts the original. See
[moderation.md](moderation.md#how-a-login-is-checked).

## A PIN is ASCII digits only

A PIN is now made of the ten digits 0 to 9 and nothing else, because that is all the keypad can enter. An older
release also took digits from other scripts (fullwidth or Arabic-Indic, for example), so a PIN set that way can no
longer be entered by anyone. Run `/security reset <player> pin` for such an account; the player then sets a new one.

## Money is paid only in a currency the server knows

A player warp rating reward (`ratings.rewards.*` in the player warps config) whose currency id this server does not
know is no longer paid in the default currency: the log has `event=pwarp_rate_reward_credit_failed` and the player
it was for gets nothing. `default` still pays the default currency. Check the ids in those rewards against your
currencies.

On a network, a paid hop is now refunded, and a player warp owner paid, in exactly the currency it was taken in.
Define the same currency ids on every backend; a backend that does not know one logs the refund instead of paying it
in its own default. See [network.md](network.md).

## /eco giveall and resetall reach every player

`/eco giveall` and `/eco resetall` used to change only the players online on the server that ran them. They now change
every player who has joined a server of the database, offline and on other servers too, and with `--all-accounts` also
the accounts other plugins opened, for every kind of currency: the ledger, item currencies (delivered when each player
is next online) and economies another plugin keeps (paid one player at a time through it). The update adds two tables,
`economy_sweeps`, where the progress of each run is kept, and `economy_item_deliveries`, the item deliveries waiting for
their players. See [economy.md](economy.md#changing-every-balance).

- **Scheduled commands.** A console script or scheduler that runs `eco giveall 100` as a reward for the players
  online would now pay every player who ever joined, each time it runs. Add `--online` to keep the old meaning:
  `eco giveall 100 --online`. `/payall` is no substitute: only a player can run it, and it takes the money from that
  player.
- **Item currencies and experience.** `giveall` no longer drops coins at the feet of a player whose inventory is
  full, with or without `--online`: what does not fit waits for their next join. Experience is now reached for
  offline players too, when they next join.
- **Another plugin's economy.** Payments through a Vault or Treasury economy are made at most once; a player whose
  payment was under way when the server stopped is named in the console as uncertain rather than paid twice.
- **Events.** A plugin that listened to `UxmWalletCreditEvent` for each account `giveall` paid on the ledger now
  hears one `UxmBalanceSweepEvent` when the run finishes. Through another plugin's economy, with `giveall --online`,
  and for item currencies and experience each time a delivery is handed over, every payment still fires the credit
  event, and a reset that takes fires the debit event.
- **New permission.** `/eco sweeps` and `/eco sweeps cancel <id>` need `uxmessentials.economy.admin.sweeps`.

## Transaction history ids come from the database

The update adds one table, `id_allocator`, and starts the transaction history ids after the highest one already
written. Each server used to count those ids itself from the highest one it saw at start, so two servers sharing one
database wrote the same ids, and the history of one of them stopped being saved. Nothing needs editing.

Upgrade every server that shares the database in the same window. A server still on the previous release, or one
rolled back to it, keeps counting for itself from the highest id it saw at start. When a batch of history meets an id
such a server took, the upgraded server moves its counter past the highest id written and stores the batch again, so
the history is kept on both. A row the database refuses even then (a figure too large for the column, say) is
dropped alone and logged as `event=ledger_rows_dropped` with the rows it concerns; the rest of its batch is stored.
Balances are never affected, only the history lines.

## Ladder reasons follow each reader's language

The update adds two nullable columns, `reason_key` and `reason_args`, to `moderation_mutes`, `moderation_tempbans`,
`moderation_ip_bans` and `moderation_sanction_history`. The first server to start on the new release adds them to the
shared database; nothing needs editing. A sanction the warn ladder gives from then on keeps its reason as the catalog
line, so staff read it in their own language. Sanctions already stored keep the text they were written with. A server
still on the previous release reads the same rows and shows that text. See
[moderation.md](moderation.md#warnings-and-the-escalation-ladder).

## Item columns hold more on MySQL and MariaDB

On MySQL and MariaDB the columns that hold serialized items are widened from `TEXT`, which stops at 64 KB, to
`LONGTEXT`: vaults, the items kept from a deleted vault or a trade, the cross-server trade escrow, inventory
rollback snapshots, the staff mode loadout and NPC equipment. A full vault, an inventory snapshot with an ender chest,
or a kept batch of filled shulker boxes could be larger than 64 KB, and was then refused, or cut short on a server
with strict mode off. SQLite and PostgreSQL had no such limit and are not changed.

Nothing needs editing. Back up the database first: the first server to start on the new release alters those tables,
and MySQL and MariaDB rebuild a table to change a column, so the start takes longer while a large `inv_snapshots`
table is copied. A row that was cut short before the upgrade stays cut short; the update cannot restore it.

## Cross-server trades need the same release on both servers

A cross-server trade opens the window of the player who accepted only after the inviter's server says its own
window is open. A server on an older release never says so. While backends run different releases, as in a rolling
upgrade, a player on an upgraded server who accepts an invite from a player on an older one waits a few seconds and
is told the trade was cancelled; each player keeps their items. Upgrade every backend in the same window, as
[upgrading a network](network.md#upgrading-a-network) asks.

## Trades keep items for a player who left

The update adds one table, `trade_item_returns`. The first server to start on the new release creates it in the
shared database; nothing needs editing. A same-server trade that cannot hand a player their stacks because they left
mid-trade, or because the server stopped with the trade open, now keeps them there and gives them back on that
player's next join, where it used to lose them. See [trade.md](trade.md#items-kept-for-the-next-join).

## Rank actions wait for a player who is not here

The update adds the `rank_pending_actions` table; nothing needs editing. Rank actions now take `{uuid}`: hand a
permission plugin `{uuid}` rather than `{player}`, as `ranks.conf` shows.

## Votes for an unknown name wait for that player

The update adds the `vote_parked` table; nothing needs editing. A vote whose name no account answers to used to be
credited to an id made up from the name, which on a Bedrock bridge, or on an online-mode server for a name Mojang
gives someone else, was the wrong player, so the rewards were never paid. Such a vote now waits under the name and
is credited, with the time it came, when a player with that name first joins any server of the database; votes
nobody claims within 30 days are forgotten. A Bedrock voter who types their gamertag without the bridge's prefix is
found under the prefixed name. On an offline-mode server, a name that only differs in case from several accounts
waits for that exact spelling.

## Inventory restores wait for a player who is not here

The update adds the `inv_pending_restores` table; nothing needs editing. A restore staff choose in `/invrestore` for
a player who is offline, or on another server, is now kept and put on the player as they next join, or at once by
the server they are on, with the same safety snapshot first. It used to answer that the player was not online.
Upgrade every server that shares the database in the same window: an older server neither applies a kept restore nor
announces one.

## /pay reaches players who are not online

The update adds the `player_notices` table; nothing needs editing. `/pay` now pays a player who is offline or on
another server, where it used to answer that the player was unknown. The receiver's own `/paytoggle`, the minimum
and the confirm threshold apply as before. The line telling them they were paid waits in that table until they are
next online.

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

## Database kit claims apply on a single server

On a single server (`network.enabled = false`), the one-time kit claims the database holds for a player are now
copied into the player's data when they join. Such rows come from an EssentialsX import, or are left from a time the
server ran with the network on: a one-time kit claimed then now stays claimed after the network is turned off,
where it could be claimed again before. On a single server `/kit reset` now also deletes those rows, so a reset claim
does not come back on the next join. See [kits.md](kits.md#on-a-network).

## Staff teleports stop at /tptoggle

`/tp`, `/tphere`, `/goto` and `/bring` no longer reach a player who refuses teleports with `/tptoggle`. The staff
member is told to use `/tpo` or `/tpohere`, which still go through. Grant `uxmessentials.tp.others` to the staff
who must keep moving such players. The position forms of `/tp`, and players on another server, are not stopped. See
[teleport.md](teleport.md#staff-teleports).

The console and automation are stopped too: a script or a plugin that runs `/tp <player> <destination>` must run
`/tpo <player> <destination>` to keep reaching a player with `/tptoggle` off.

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

Kits, a teleport command with its own `cooldowns.<command>` value and a warp with its own cooldown are not affected: one
node covers all of them, so there it still only lowers each value ([teleport](teleport.md#cooldowns), [kits](kits.md)).

An example with LuckPerms: the `default` group holds `uxmessentials.tp.cooldown.10` and `default-cooldown` was lowered
to 5. Before the upgrade everyone waited 5 seconds; after it every rank that inherits `default` waits 10.

What to check, for each group (`lp group <group> permission info`, starting with `default`) and for single players:
look for `cooldown.<seconds>` nodes longer than the config value of their feature. Lower the node or remove it, and
let the config value apply again.

## RTP radius tiers stay inside max-radius

A `uxmessentials.rtp.radius.<blocks>` node used to replace `max-radius` of `rtp.conf` for `/rtp biome`, and the
config value took part in the "largest wins" fold, so a node could only widen the search, up to beyond `max-radius`.
A node now only narrows it: the largest node held is the outer edge when it is smaller than `max-radius`, and a node
at or above `max-radius` changes nothing ([teleport](teleport.md#random-teleport)).

To keep a reach a node gave beyond `max-radius`, raise `max-radius` to that reach and give everyone who should search
less a lower node instead: for example `max-radius = 20000`, `uxmessentials.rtp.radius.20000` for the rank that had
it, and `uxmessentials.rtp.radius.5000` for the `default` group. Plain `/rtp` serves from the shared ready spots,
which follow `max-radius` for everyone, so a larger `max-radius` widens it for every player.

## Home world limits no longer lift the overall limit

`uxmessentials.home.limit.<world>.<n>` used to be folded into the home limit when a home was set in that world, so a
large world node raised the limit there and a small one did not hold the world back. It now counts only the owner's
homes in that world, and the overall limit (`default-limit` or `uxmessentials.home.limit.<n>`) still caps all of them
and sizes the `/home` grid ([homes](homes.md#how-many-homes-a-player-gets)). A player who relied on a world node for
more homes needs an overall node as large; homes already set stay, and a new home past either limit, or a home moved
into a full world, is refused.

## Player warp world limits no longer lift the overall limit

`uxmessentials.pwarp.limit.<world>.<n>` used to be folded into the overall limit when a warp was set in that world and
compared with the owner's warps in every world, so a large world node raised the overall cap there. It now counts only
the owner's warps in that world, and the overall limit (`default-limit` or `uxmessentials.pwarp.limit.<n>`) still
caps all of them ([warps](warps.md#limits-and-categories)). A player who relied on a world node to own more warps in
total needs a larger `uxmessentials.pwarp.limit.<n>` as well. Warps set before the upgrade are kept; only a new warp,
or a warp moved into the world, is refused.

## Exemptions and placeholders follow the shared names

The release that renamed every exemption node and placeholder has its own page: [upgrading-renames.md](upgrading-renames.md) lists what to edit, the node map and the LuckPerms commands that move the grants.
