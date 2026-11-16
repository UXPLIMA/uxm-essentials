# Trade

`/trade <player>` asks another player to trade; they accept or deny within `request-expiry-seconds`. Both get a window
with their own offer on one side and the other player's offer on the other. Changing either offer clears both
confirmations, and the trade runs only when both confirm with nothing changed since. Money and experience can be
staked too when an economy is installed. The settings are in `modules/trade/config.conf`; the node is
`uxmessentials.trade.use` ([permissions](permissions-gameplay.md#trade)). A trade with a player on another server
goes through the shared escrow, see [network.md](network.md).

## Where the items go

An offered stack leaves the player's inventory while it sits in the window. Every way a trade ends hands each stack
over exactly once:

| The trade | The stacks |
|---|---|
| Both confirmed and the money and experience moved | Each player gets the other's stacks. |
| The money or experience could not move | Each player gets their own stacks back. |
| A player closed the window, left, or changed worlds | Each player gets their own stacks back. |
| The trade module stops or reloads | Each player gets their own stacks back. |

A stack that does not fit an online player's inventory is dropped at their feet, where they stand when the trade
ends.

## Items kept for the next join

A player who is gone by the time their stacks are handed over does not lose them: they left while the trade
settled, or the server stopped with the trade open. The stacks are kept in the database (`trade_item_returns`) and
handed over the next time that player joins, on any server that shares the database. They keep everything about
them, enchantments, names and a shulker's contents included. The player is told how many items came back.

On that join, what does not fit the inventory stays kept for the join after, instead of being dropped: a join point
is often a crowded spawn, where a dropped stack can be picked up by someone else or despawn. The player is told how
many items wait.

Each kept batch goes to exactly one server: it is removed from the database before it is handed over, so a player who
joins two servers in quick succession gets it once. A batch whose hand-over does not land (the player left again, or
the inventory refused it) is kept again at once. A batch kept while the player is already online waits for their
next join.

If the database cannot be written at that moment, the log has `event=trade_return_keep_failed` with the player and
the items, so an operator can give them back by hand.
