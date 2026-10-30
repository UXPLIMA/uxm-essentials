# Playtime

The playerstate module keeps its own record of how long each player plays. Every `sample-seconds` (60 out of the
box) it credits each online player that interval as active or AFK time, one row per player and day, in the
plugin's database. `/playtime` shows the record for today, the last 7 and 30 days and all time.

The record starts on the day the plugin was installed. Time played before that lives only in the server's vanilla
statistic, which the record does not read. Two tools close that gap: an import from the vanilla statistic, and a
correction by hand.

## Commands

| Command | Node | What it does |
|---|---|---|
| `/playtime [player]` | `uxmessentials.playtime.use` | Show the breakdown. |
| `/playtime set <player> <time>` | `uxmessentials.playtime.set` | Make the all-time total exactly `<time>`. |
| `/playtime add <player> <time>` | `uxmessentials.playtime.set` | Add `<time>` to the all-time total. |
| `/playtime take <player> <time>` | `uxmessentials.playtime.set` | Take `<time>` from the all-time total, stopping at zero. |
| `/playtime import <player>` | `uxmessentials.playtime.import` | Raise the total to the vanilla statistic when it is lower. |
| `/playtime import all` | `uxmessentials.playtime.import` | The same for every player the server has a profile for. |
| `/playtime reset [player]` | `uxmessentials.playtime.reset` | Clear the record and any correction. |
| `/playtime resetall` | `uxmessentials.playtime.reset` | The same for every player. |

`<time>` is written like `30m`, `2d5h` or `1w` (units `s`, `m`, `h`, `d`, `w`); `set` also takes `0`. Naming a
player other than yourself also needs `uxmessentials.playtime.others`, as every playerstate command does. Each
subcommand sits under `/playtime`, so it needs `uxmessentials.playtime.use` too.

## Corrections

`set`, `add`, `take` and an import do not rewrite the daily rows. They store one correction per player, which is
added to the all-time totals. The today, week and month figures stay what the sampler counted. A correction counts
as active time, so it also counts toward the `playtime-active` rank requirement.

## Importing

An import compares the tracked all-time total with the vanilla statistic and adds the difference when the vanilla
figure is higher. It never lowers a total, so running it twice changes nothing. `import all` reads the players one
after the other and reports how many got time added when it is done.

With `playtime.import-on-join = true` in `modules/playerstate/config.conf`, each player is imported once, the first
time they join with the setting on. The playerstate module does not reload live, so the setting takes a restart. A player imported before, by the command or on join, is skipped, so a player
whose playtime was reset (for example by a prestige action) does not get the old time back on the next join.

## Rank requirements

The ranks module reads this record:

| Requirement | Passes when |
|---|---|
| `playtime <seconds>` | the all-time total, active and AFK, is at least `<seconds>` |
| `playtime-active <seconds>` | the all-time active total, AFK left out, is at least `<seconds>` |

To make players earn their playtime again after a prestige, reset it in the prestige actions:

```hocon
actions = [
  "console playtime reset {player}"
]
```

## The playtime board

`%uxmessentials_top_playtime_<period>_<n>_name%` and its siblings rank active time, AFK left out. The all-time board
reads this record, corrections included, so it covers every day since the install; the monthly, weekly and daily
boards count from the version that added them. The reference lists every board key.
