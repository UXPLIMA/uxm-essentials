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

With a tax, the receiver is credited the payment and the tax is then taken from them: paying 50 with a 10 percent
and 1 flat tax sends 50, takes 6, and the receiver keeps 44. The tax is never more than the payment.

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
| `/loan` | `uxmessentials.economy.loan` | Loans and credit score. |
| `/exchange` | `uxmessentials.economy.exchange` | Converts between currencies. |
| `/eco give\|take\|set <player> <amount> [currency]` | `uxmessentials.economy.admin.give`, `.take`, `.set` | Changes a balance. |
| `/eco reset <player> [currency]` | `uxmessentials.economy.admin.set` | Resets a balance. |
| `/eco giveall\|resetall` | `uxmessentials.economy.admin.bulk` | Changes every balance; `resetall` needs `--confirm`. |
| `/eco give-random` | `uxmessentials.economy.admin.give` | Gives a random amount between a minimum and a maximum. |
| `/eco note give <player> <amount> [currency]` | `uxmessentials.economy.admin.note` | Gives a banknote without taking money from anyone. |

Every node is listed in [permissions-gameplay.md](permissions-gameplay.md#economy).

## Placeholders

| Placeholder | Value |
|---|---|
| `%uxmessentials_balance%` | The balance in the default currency, as a plain number. |
| `%uxmessentials_balance_formatted%` | The same with the symbol and grouping. |
| `%uxmessentials_economy_balance_compact%` | Shortened, such as `1.2K`. |
| `%uxmessentials_baltop_position%` | The player's place on the leaderboard. |
| `%uxmessentials_economy_currency_symbol%` | The symbol of the default currency. |

## Examples

**A premium currency nobody can trade.** Add `gems { precision = 0, transfer-allowed = false,
exchange-allowed = false, leaderboard-enabled = false }` to `currencies.conf` and give gems with
`/eco give <player> 10 gems`.

**Ask before large payments.** Set `pay.confirm-threshold = "10000"`. A player who pays more sees a prompt and
finishes with `/payconfirm` within 30 seconds.

**A tax on payments.** Set `pay.tax { enabled = true, percent = "5" }`. Staff with
`uxmessentials.economy.tax.bypass` pay none.
