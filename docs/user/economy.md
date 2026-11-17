# Economy

The economy module keeps balances in the plugin's own database and gives players `/pay`, `/balance`, `/baltop`,
`/sell` and `/worth`. It can hold several currencies, publishes itself as a Vault economy so other plugins can pay
into it, and adds banknotes, shared banks, loans and exchange between currencies.

## Files

- `modules/economy/config.conf`: how the economy behaves.
- `modules/economy/currencies.conf`: one block per currency.
- `modules/economy/gui/`: the menus.

`/uxmess reload` reads them again.

## Currencies

Each block in `currencies.conf` is a currency, named by its id. The currency named in `wallet.default-currency`
(`coins` out of the box) is used whenever a command names none, and must be defined here.

| Key | Meaning |
|---|---|
| `symbol`, `plural` | How the currency is written. |
| `format` | A Java number pattern, for example `#,##0.00`. |
| `precision` | Decimal places. Amounts are rounded to it; 0 makes a whole number currency. |
| `starting` | What a new player starts with. |
| `min-balance`, `max-balance` | The range a balance stays in. A negative minimum allows debt. |
| `min-pay` | The smallest `/pay`. |
| `confirm-threshold` | Payments above it wait for `/payconfirm`; when left out, `pay.confirm-threshold` applies. |
| `icon-material` | The icon in the wallet, exchange and loan menus. |
| `transfer-allowed` | May be sent with `/pay` and `/payall`. |
| `exchange-allowed` | May be converted with `/exchange`. |
| `leaderboard-enabled` | Appears in `/baltop`. |
| `permission-required` | Using it needs `uxmessentials.economy.currency.<id>`. |
| `backend` | Where the balances live. Left out, they live in this plugin's database. |

A currency whose `backend` points at another plugin (`playerpoints`, `coinsengine:<id>`, `exp`, or a
`placeholder:<id>` bridge defined in `config.conf`) keeps its balances there. Such a currency has an empty `/baltop`,
and exchange, banks and loans switch off for it.

## Amounts players type

Amounts accept a suffix (`2.5k`, `1m`, `3b`) and either separator style: `1,000`, `1.000`, `1,000.50` and
`1.000,50` all work. A single separator followed by exactly three digits is a thousands separator, so `1.000` is one
thousand, unless the number starts with 0: `0.500` is half. An amount above the currency's maximum is lowered to the
maximum; zero and negative amounts are refused.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Switches the module on or off. |
| `amount-format` | `"full"` | `full` writes `1,234.56`; `compact` writes `1.23K`. |
| `wallet.default-currency` | `"coins"` | The currency commands use when none is named. |
| `wallet.gui-enabled` | `true` | Enables the `/wallet` menu. |
| `gui.timezone` | `"UTC"` | The time zone of transaction history times. |
| `banknotes.enabled` | `true` | Enables `/withdraw` and `/deposit`. |
| `bank.enabled` | `true` | Enables shared bank accounts. |
| `bank.interest` | off | Interest paid into banks every `interval-hours`, by `tiers` of `"minBalance:ratePercent"`. |
| `loans.enabled` | `true` | Enables `/loan`; the other `loans` keys set limits, rates and credit scores. |
| `pay.min-pay` | `"0.01"` | The smallest payment. |
| `pay.confirm-threshold` | `""` | Payments above this amount wait for `/payconfirm`. Empty turns it off. |
| `pay.confirm-timeout-ms` | `30000` | How long a payment waits for confirmation. |
| `pay.toggle-default` | `true` | Whether new players accept payments. |
| `pay.tax` | off | A cut of every payment: `percent` plus `flat`. `sink` is `void` or `account:<uuid>`. Holders of `bypass-permission` pay none. |
| `baltop.page-size` | `10` | Rows per page. |
| `baltop.cache-ttl-ms` | `60000` | How often the leaderboard is recalculated. |
| `baltop.exempt-permission` | | Holders of this node are left off the leaderboard. |
| `baltop.exclude-banned` | `false` | Leaves banned players off. |
| `provider.register` | `true` | Publishes the economy to Vault for other plugins. |
| `allow-nonatomic-recurring` | `false` | Lets timed charges, such as player warp rent, use a currency kept by another plugin. |
| `worth.items` | a starter list | Sell prices as `"MATERIAL:price"` or `"MATERIAL:price:currency"`. |

## Paying

`/pay <player> <amount> [currency]` checks, in order: the player is not paying themself, the currency may be
transferred, the amount is at least the minimum, the receiver accepts payments (`/paytoggle`), and the payer has
the money. A payment above the confirm threshold waits for `/payconfirm`, and is checked again when confirmed.

The receiver may be offline or on another server of the database: the money arrives at once, and the receiver is
told they were paid when they are next online, at once on the server they are on, or as they next join. The `notices`
block of `config.conf` bounds what waits for them: at most `kept-per-player` lines (50) are kept, the oldest dropped
first, a line waits `keep-days` (30) at most, and a returning player is shown the newest `shown-at-once` (10) with
one more line counting the rest.

With a tax, the receiver is credited the payment and the tax is then taken from them: paying 50 with a 10 percent
and 1 flat tax sends 50, takes 6, and the receiver keeps 44. The tax is never more than the payment.

## Changing every balance

`/eco giveall <amount> [currency]` and `/eco resetall --confirm [currency]` change the balance of every player who
has joined a server of this database: players who are offline, players on other servers of a network, and Bedrock
players who join through Geyser and Floodgate (by the id and the prefixed name Floodgate gives them). Before it
starts, the server adds the players it knows from its own player list. `resetall` puts each balance back to the
currency's `starting` figure and asks for `--confirm` first.

Other plugins can open accounts through Vault for things that are not players, such as a town's bank or a tax holding
account. On this plugin's ledger these are left alone unless `--all-accounts` ends the command:
`/eco giveall 100 --all-accounts`, `/eco resetall --confirm coins --all-accounts`. The bulk screen of the `/eco` menu
reaches players only.

`/eco giveall <amount> [currency] --online` gives the amount only to the players online on the server that runs it,
one by one, the way a scheduled reward for being online wants, whatever kind of currency it is. It replies with the
number of players and fires the usual credit event for each of them. For an item currency, or experience, it is a
delivery per player handed over at once, so what does not fit an inventory waits for the next join, as below; the
reply then also says how many players had no room for all of it.

How the change reaches the players depends on where the currency's money is kept:

- **This plugin's ledger** (the usual case): a few hundred accounts at a time, each step one short transaction that
  changes the balances and writes their history lines together.
- **A balance that changes only while its player is online**: an item currency, or a backend such as `exp` that
  cannot reach an offline player. Each step queues one delivery per player. A player online on any server gets it at
  once, the others when they next join any server; each delivery is handed over once, however many servers try. What
  does not fit in the inventory is not dropped: it waits for the next join. Gives queued while a player is away add
  up in one delivery, so the waiting list stays one line per player and currency however many runs there are.
  A `resetall` sets the balance (for an item currency, the coins a player carries in their inventory) to the
  starting figure when they are next online, and drops the gives and the offline credits of that currency still
  waiting for them. It is applied before anything given after it, and while it waits, credits of that currency
  wait with it. Coins in chests and ender chests are not part of the balance and are left alone.
- **Another plugin keeps the money** (a Vault or Treasury economy provides the server's economy, or the currency's
  `backend` is another plugin): each step hands up to fifty players to that plugin, one call per player. A Vault
  economy is called on the server thread, ten players a tick; Treasury and the other backends off it. `resetall`
  takes or adds the difference to the starting figure. These calls cannot be undone together, so they are made at
  most once: a player whose call was under way when the server stopped, or whose call failed, is counted as
  uncertain, named in the console (`event=economy_sweep_uncertain`) and not paid again; check those players by
  hand. A balance the plugin did not return is left as it was and counted as left out. `--all-accounts` adds
  nothing here, since the other plugin's accounts cannot be listed.

- The change runs in the background and the reply comes once it is done, with the number of accounts changed.
  Each step locks only its own accounts, for a moment, so payments and other plugins' calls carry on while it runs.
  `giveall` leaves out an account the amount would take past `max-balance`, or one the other plugin refuses, and
  says how many it left out.
- If the server stops or the database fails part way, the change pauses after the last finished step and tells you
  how far it got. It carries on by itself every minute, or when any server of the database starts again, and no
  account is changed twice. A player who joins for the first time while it runs is reached only if they sort after
  the players already done, so they may or may not get the change.
- A delivery handed over while the database cannot record it is remembered by that server and recorded once the
  database is back. If that server restarts before then, the delivery is handed over again after ten minutes, so the
  player gets it twice; the console says so (`event=swept_items_finish_failed`).
- A reset that cannot be applied (the player's balance does not answer) holds that currency's gives and offline
  credits for the player and is named once in the console (`event=swept_items_reset_stuck`); it is tried again on
  every pass and join.
- If the database refuses a step for good, for example a balance too large for its column, the change stops there
  and says how many accounts it changed; those keep the change. So does a change whose currency is removed from the
  configuration before it finishes.
- Every changed account gets its own line in the transaction history (an item delivery when it is handed over); the
  audit log gets one line for the command.
- The other servers of a network are told, drop the balances they held in memory, and hand item deliveries to their
  players online.
- A currency has one unfinished change at a time across every server of the database. Running the command again
  while one is running or paused is refused and names it, so a repeated command cannot pay everyone twice.
- `/eco sweeps` lists the unfinished changes with their id, progress and last error; `/eco sweeps cancel <id>` stops
  one, and the accounts it already changed keep the change. The first eight characters of the id are enough.

When it is done, plugins hear `UxmBalanceSweepEvent` once, with what was done, the amount and the number of accounts.
On the ledger and for items there is no credit or debit event per account; through another plugin each call fires
the usual one. The REST add-on streams it as `economy.balance-sweep`.

`/eco giverandom <amount> [currency]` gives the amount to one player online on this server.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/balance [player] [currency]` | `uxmessentials.economy.balance`, `.others` for another player | Shows a balance. |
| `/wallet [currency]` | `uxmessentials.economy.wallet` | Shows every balance. |
| `/pay <player> <amount> [currency]` | `uxmessentials.economy.pay` | Sends money. |
| `/payconfirm` | `uxmessentials.economy.pay` | Confirms a large payment. |
| `/paytoggle` | `uxmessentials.economy.pay.toggle` | Stops or allows payments to you. |
| `/payall <amount> [currency]` | `uxmessentials.economy.payall` | Pays every online player. |
| `/baltop [currency] [page]` | `uxmessentials.economy.baltop` | Shows the richest players. |
| `/worth [item]` | `uxmessentials.economy.worth` | Shows what the held item sells for. |
| `/sell [amount]`, `/sellall` | `uxmessentials.economy.sell` | Sells the held item, or everything sellable. |
| `/setworth [item] <price> [currency]` | `uxmessentials.economy.setworth` | Sets a sell price; `clear` removes it. |
| `/withdraw`, `/deposit` | `uxmessentials.economy.withdraw`, `.deposit` | Turns money into a banknote and back. |
| `/bank` | `uxmessentials.economy.bank` | Shared bank accounts. |
| `/loan` | `uxmessentials.economy.loan` | Loans and credit score. In the dashboard a left click on a loan pays an installment and a right click pays it off. A shift click, or the gold custom-repayment button and then a loan, pays an amount you type. A `modules/economy/gui/economy-loan.conf` from an older release lacks the button: copy the shipped `custom` block into it. |
| `/exchange` | `uxmessentials.economy.exchange` | Converts between currencies. |
| `/eco give\|take\|set <player> <amount> [currency]` | `uxmessentials.economy.admin.give`, `.take`, `.set` | Changes a balance. |
| `/eco reset <player> [currency]` | `uxmessentials.economy.admin.set` | Resets a balance. |
| `/eco giveall\|resetall` | `uxmessentials.economy.admin.bulk` | Changes every player's balance, online or not; `resetall` needs `--confirm`, `--all-accounts` adds other plugins' accounts, `giveall --online` only the players online here. See [Changing every balance](#changing-every-balance). |
| `/eco giverandom <amount> [currency]` | `uxmessentials.economy.admin.bulk` | Gives the amount to one random player online here. |
| `/eco sweeps [cancel <id>]` | `uxmessentials.economy.admin.sweeps` | Lists the unfinished changes to every balance, or stops one. |
| `/eco give-random` | `uxmessentials.economy.admin.give` | Gives a random amount between a minimum and a maximum. |
| `/eco note give <player> <amount> [currency]` | `uxmessentials.economy.admin.note` | Gives a banknote without taking money from anyone. |
| `/eco history [player]` | `uxmessentials.economy.admin.history` | Opens a player's transaction history, your own when none is named. In game only. |
| `/eco logs` | `uxmessentials.economy.admin.logs` | Opens the transaction log of the whole server. In game only. |
| `/eco backup` | `uxmessentials.economy.admin.backup` | Writes every wallet balance to `backups/economy/backup_<time>.json` in the plugin folder. |
| `/eco export` | `uxmessentials.economy.admin.backup` | Writes every wallet balance to `backups/economy/export_<time>.csv` as `owner,currency,balance`. |
| `/eco restore <player> <backup>` | `uxmessentials.economy.admin.restore` | Puts one player's wallet balances back from a backup; tab completion lists them. Currencies no longer configured are skipped. |

Every `/eco` subcommand also needs `uxmessentials.economy.admin`, which opens `/eco` itself. Every node is listed in [permissions-gameplay.md](permissions-gameplay.md#economy).

## Placeholders

| Placeholder | Value |
|---|---|
| `%uxm_essentials_balance%` | The balance in the default currency, as a plain number. |
| `%uxm_essentials_balance_formatted%` | The same with the symbol and grouping. |
| `%uxm_essentials_economy_balance_compact%` | Shortened, such as `1.2K`. |
| `%uxm_essentials_baltop_position%` | The player's place on the leaderboard. |
| `%uxm_essentials_economy_currency_symbol%` | The symbol of the default currency. |

## Examples

**A premium currency nobody can trade.** Add `gems { precision = 0, transfer-allowed = false,
exchange-allowed = false, leaderboard-enabled = false }` to `currencies.conf` and give gems with
`/eco give <player> 10 gems`.

**Ask before large payments.** Set `pay.confirm-threshold = "10000"`. A player who pays more sees a prompt and
finishes with `/payconfirm` within 30 seconds.

**A tax on payments.** Set `pay.tax { enabled = true, percent = "5" }`. Staff with
`uxmessentials.bypass.tax` pay none.
