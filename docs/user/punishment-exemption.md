# Punishment exemption

A player holding `uxmessentials.bypass.punishment` cannot be punished (see
[moderation](moderation.md#who-cannot-be-punished)). This page says where that node is read for a target who is not
online on the server a staff member acts from, which on a [network](network.md) is the usual case.

Whether a target holds `uxmessentials.bypass.punishment` is read where the answer is:

- On this server, live. On another server, that server is asked over the bus and reads it live; its answer wins.
- When that server does not answer within three seconds, or the player is offline, every source here is read, and a
  grant from any one exempts: LuckPerms (any context), the Vault permission plugin, this server's operator list, and
  what each server recorded when the player was last there. Each server records the live answer when the player
  joins, leaves, and, with LuckPerms, whenever their permissions change, in `moderation_exempt_seen`.
- For a player whose server did not answer, only a grant counts: anything else refuses the sanction, since that
  server's own operator list and permission plugin are not read here.
- LuckPerms cannot tell for a player its storage never saw (storage not shared with the server they played on), nor
  Vault for a player who never joined this server, since Vault asks by name. Then the record decides: a player some
  server recorded, with no recorded grant, is not exempt. One no server recorded cannot be sanctioned from here; do it
  from a server they played on.

The record is only as fresh as the player's last visit. An exemption granted while they are offline, in a permission
store this server does not share, is not seen here until they join the server that has it; a grant removed while they
are away still counts until then. When a server is gone for good, delete its rows:
`DELETE FROM moderation_exempt_seen WHERE server = 'name'` (with your table prefix).
