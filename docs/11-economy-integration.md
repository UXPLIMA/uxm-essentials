# 11. Economy integration

Thirty six references in this repository's javadoc point here. This document is written from
the code that cites it: the `economy` context in `:core`, its ports, the provider adapters in
`:bukkit-adapter`, and the eight per context economy seams.

## 11.1 The three layers

An amount of money crosses three boundaries in this plugin, and each one exists to stop a
dependency the layer above would otherwise have.

| Layer | What it is | Who names it |
|---|---|---|
| A context seam | `HomeEconomy`, `VaultEconomy`, `WarpEconomy`, `KitEconomy`, `RankEconomy`, `TradeEconomy`, `PlayerWarpEconomy`, `ClickActionEconomy` | The context that charges |
| `EconomyProvider` | The plugin's own economy surface: balance, withdraw, deposit, transfer | The economy context |
| `CurrencyBackend` | One place money actually lives | The backend adapter |

A context never imports an economy type. Homes owns `HomeEconomy` and vaults owns
`VaultEconomy`, each one expressed in that context's own words and each one holding only the
calls that context makes: a balance check, the charge, and the refund. The economy context
supplies the adapter that bridges the seam to `EconomyProvider`. That is why a server that
runs no economy still runs homes: the seam is injected as an `Optional`, and an absent
provider means a configured cost is ignored rather than a command that fails.

## 11.2 The charge is the gate

A seam's `withdraw` is itself the guarded debit. It answers false when the funds were not
there, so there is no affordability check before it and no window between the check and the
charge in which the balance can change. A context that reads a balance first and charges
second has written the oldest race in economy code: the check passes, another thread spends
it, and the charge takes the player negative.

## 11.3 Several currencies, one provider

`RoutingEconomyProvider` is what a caller holds. It reads the currency registry, picks the
`CurrencyBackend` that owns the named currency, and routes the call. A currency the registry
does not know is an error and never a silent fall back onto the default one: money moved in
the wrong currency is money lost.

`SerialisingCurrencyBackend` wraps a backend that cannot take concurrent writes, so the
backend author never has to think about threads. `NativeCurrencyBackend` is the balance this
plugin keeps itself.

## 11.4 Another plugin's economy

Three directions, and they are not the same job.

- **We read theirs.** `ForeignEconomyProviders` reaches an economy plugin behind a soft
  dependency seam. The plugin is asked for by name before any class of it is loaded, so a
  server without it carries none of its code.
- **They read ours.** `VaultEconomyPublisher` registers this plugin as a Vault economy, so
  every plugin that speaks Vault sees our balances. `EconomyProviderRegistrar` does the same
  for the service registry, and `TreasuryCurrencies` for Treasury.
- **We price something of theirs.** `ProviderKitEconomy`, `ProviderRankEconomy`,
  `ProviderWarpEconomy` and `ProviderTaxSink` are the adapters that put one context's seam on
  top of whichever provider is live.

## 11.5 What the economy context owns beyond a balance

`BankService` and `BankInterestPolicy`, `LoanService` and `LoanPolicy`, `TaxPolicy` and a tax
sink, `ExchangeService` between currencies, `BalTop` and its exemptions, `Worth` and the
`WorthTable` behind `SellItem` and `SellAll`, `Pay` and `PayToggle`, banknotes, an audit and a
history. Each is a use case in `economy/application`, each reads a port, and none of them
knows which backend the money is in.

## 11.6 The rules that cost something to learn

- **A cost of zero is still a charge.** A context that skips the call when the amount is zero
  skips the audit row with it, and an operator then cannot see that the action happened.
- **A refund is a deposit through the same seam**, never a negative withdraw. A backend that
  refuses overdrafts refuses the negative one too, and the refund silently does not happen.
- **An absent provider is not an error.** It is a server that has no economy, which is a
  supported configuration: the cost is ignored, the action goes through, and nothing is said
  to the player about money they were never asked for.
