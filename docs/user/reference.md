# Reference

The command, permission and placeholder pages this one points to are generated from the plugin's own catalogues, so they list what the build registers.

Contents: [Commands](#commands) · [Permissions](#permissions) · [Placeholders](#placeholders) · [Boards](#boards) · [REST add-on](#rest-add-on) · [Files](#files)

## Commands

Every command with its aliases, its module and the node that gates it is in [commands.md](commands.md). `/uxmess` is the administration root; `/help` lists the commands a player may use, in their language.

## Permissions

Listed by module in [permissions-gameplay.md](permissions-gameplay.md), [permissions-server.md](permissions-server.md) and [permissions-items.md](permissions-items.md).

## Placeholders

Registered when PlaceholderAPI is installed, as `%uxm_essentials_<key>%`. The keys each module answers are in [placeholders.md](placeholders.md), and the facts about a player, the item in their hand and the server are in [placeholders-facts.md](placeholders-facts.md); each key there comes with an example of what it renders. The leaderboard keys every UXM plugin shares are below.

### Boards

The boards answer the grammar every UXM plugin shares, so a hologram written for another plugin's board works here.

| `<board>` | Module | Ranks | Periods |
|---|---|---|---|
| `playtime` | playerstate | Active time, AFK left out. All time reads the playtime record, corrections included; the other periods count from the board's first start. | all four |
| `trades` | trade | Trades finished; both sides count one. | all four |
| `prestige` | ranks | The prestige level. | `alltime` only |
| `earned` | economy | Money that came into a wallet in the default currency: sales, salaries, rewards, money another plugin pays through Vault. An admin give, a `/pay` from another player, a cashed banknote and a refund are left out. Counted from the board's first start. | all four |
| `spent` | economy | Money that left a wallet in the default currency: purchases, fees, money another plugin takes through Vault. An admin take, a `/pay` to another player and a written banknote are left out. Counted from the board's first start. | all four |
| `balance` | economy | The default currency's balance, from `/baltop`. | `alltime` only |
| `votes` | vote | Votes, online or offline. All time reads the vote totals, so a `/vote` reset shows; the other periods count each vote, starting from the totals of the windows open when the board first starts. | all four |

The periods are `alltime`, `monthly`, `weekly` and `daily`. `playtime`, `trades`, `prestige`, `earned`, `spent` and `votes` run on uxmLib's shared board module and follow the `leaderboard` block of `config.conf` (size, refresh, periods, time zone, first day of the week); `balance` answers from `/baltop`.

| Key | Value |
|---|---|
| `top_<board>_<period>_<n>_name` | The name at place n, or the word for nobody. |
| `top_<board>_<period>_<n>_value` | The value at place n as the board shows it (a time as `1d 2h 3m`, a balance with its symbol, a count), or the word for none. |
| `top_<board>_<period>_<n>_head` | The player at place n for a `<head:...>` tag: their name, or their uuid when an account cannot have that name; the `leaderboard.empty-head` for a place nobody holds. |
| `rank_<board>_<period>` | The reader's own place, or the word for none. |
| `value_<board>_<period>` | The reader's own value. |
| `resets_<period>` | The time until a period starts again, in the reader's words (`4d 1h`, `1h 30m`, `12m`), or the word for none for `alltime`. |

The words come from the `board.*` lines of the messages files. The `economy_baltop_*`, `baltop_position`, `votes_top_*` and `votes_position_*` keys in [placeholders.md](placeholders.md) answer as well.

## REST add-on

Every player route of the REST add-on takes a uuid. A web store or a bot that only holds a typed name finds the uuid
with `GET /api/v1/players/by-name/{name}` (a token with the read scope). It resolves the name the way a command does:
players online here or on another server sharing the database, players who are offline, and Bedrock players under
their prefixed name. Send a `#` prefix as `%23`.

| Answer | When |
|---|---|
| `200`, `match` is `found` | One account plays under the name. `player` holds its `player-id` and `player-name`, and `candidates` holds the same account. |
| `200`, `match` is `ambiguous` | On an offline-mode server, `Steve` and `steve` are two accounts. A name that matches several only in case, and none exactly, picks none: `player` is null and `candidates` lists every account it could mean. |
| `404` | Nobody has played under the name. |
| `400` | The name is blank. |

A plugin asks the same question with `UxmEssentialsApi.players().byName(name)`.

## Files

Everything sits under `plugins/uxmEssentials/`. A file is written on first start and keeps every edit made to it; an update brings the lines nobody changed to the new wording and adds new settings at the end. `/uxmess reload` reads the files again; turning a module on or off with `enabled` takes a restart.

- `config.conf`: the plugin-wide blocks, such as storage, language, network, leaderboards and claims.
- `modules/<module>/config.conf`: one per module, with the module's `enabled` switch; its menus are under `modules/<module>/gui/`. Beside it: `teleport/rtp.conf`, `economy/currencies.conf`, `ranks/ranks.conf`, and for communication `chat.conf`, `announcer.conf`, `join-quit.conf`, `advancements.conf` and `info-pages.conf`.
- `commands.conf`: the name, aliases and switch of every command, written from the commands the plugin registers.
- `messages/messages_<tag>.conf`: every line a player reads, one file per shipped language.
- `menus/` holds custom menus and `commands/custom/` custom commands, one file each, both starting with an `example.conf`.
- `text-input.conf`: how each menu prompt takes a typed line: anvil, chat, sign or dialog.
- `data/uxmessentials.db`: the database while `storage.type` is `sqlite`.
