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
| A player left in the moment both confirmed, before their window was read | Nothing moves; each player gets their own stacks back. |
| The trade module reloads | Each player gets their own stacks back. |
| The server or the plugin stops | Each player's own stacks are kept for their next join. |

A stack that does not fit an online player's inventory is dropped at their feet, where they stand when the trade
ends.

## Items kept for the next join

A player who is gone by the time their stacks are handed over does not lose them: they left while the trade
settled, or the server stopped with the trade open. The stacks are kept in the database (`trade_item_returns`) and
handed over the next time that player joins, on any server that shares the database and has the trade module on.
They keep everything about them, enchantments, names and a shulker's contents included. The player is told how many
items came back. A player who left in the moment both sides confirmed gets the offer the trade last recorded for
them; a stack placed in that same tick is not in it.

On that join, what does not fit the inventory stays kept for the join after, instead of being dropped: a join point
is often a crowded spawn, where a dropped stack can be picked up by someone else or despawn. The player is told how
many items wait.

Each kept batch is claimed by one server before it is handed over, and removed only once the items are in the
inventory, so a player who joins two servers in quick succession gets it once. A batch whose hand-over does not land
(the player left again, or the inventory refused it) is released and waits for the next join. A batch kept while the
player is already online waits for their next join too.

A claim that is never finished, because the server crashed between the claim and the hand-over, counts as abandoned
after 10 minutes and the batch can be claimed again. That choice trades a duplicate for a loss: a hand-over that
really took longer than 10 minutes would give the batch twice, while a claim that never expired would strand the items
for good after a crash. A hand-over takes well under a second, so the duplicate does not happen in practice; if it
ever does, the log has `event=trade_return_claim_lost`.

A kept row whose data cannot be read is skipped, logged as `event=trade_return_row_unreadable` and left in the table;
the player's other batches are still handed over. If the database cannot be written when items are kept, the log has
`event=trade_return_keep_failed` with the player and the items, so an operator can give them back by hand.

On MySQL and MariaDB a kept batch is a `TEXT` column, as in the vault tables, which holds up to 64 KB: a full trade
offer of ordinary items fits, but a batch of many filled shulker boxes can exceed it and fail to be kept, with the
same log line.

On Folia the plugin only stops when the server does. The stopping thread owns no region, so it does not empty the
open trade windows before it keeps their stacks; the windows are never saved and go away with the server.
