# 10. Feature modules

Fifty three references in this repository's javadoc point here. This document is written from
the code that cites it: `shared/application/module/` in `:core`, the thirty four module
implementations in `:bukkit-adapter`, and the shipped `modules/<id>/config.conf` beside each
one.

## 10.1 What a module is

A feature of uxmEssentials is a bounded context: its own package, its own config file, its own
commands, listeners and migrations, and its own switch. Homes is one, economy is another,
thirty four in all today:

`commandcontrol`, `communication`, `customcommands`, `custommenus`, `discordlink`, `economy`,
`holograms`, `homes`, `invrollback`, `itemworld`, `kits`, `messaging`, `moderation`, `npc`,
`playerstate`, `playerwarps`, `poses`, `presence`, `ranks`, `regions`, `scoreboard`,
`security`, `servertweaks`, `skin`, `staff`, `survival`, `teleport`, `trade`, `vanish`,
`vaults`, `villagers`, `vote`, `warps`, `worlds`.

`shared` is not a module. It is what the modules stand on.

## 10.2 The contract

`FeatureModule` in `:core` is eight methods and nothing else:

| Method | What it answers |
|---|---|
| `id()` | The stable identifier, equal to the context's package name |
| `configRoot()` | The HOCON path this module owns, `modules.<id>` |
| `commands()` | The Brigadier specs, registered only when the module is enabled |
| `listeners()` | The listener factories, registered only when the module is enabled |
| `migrations()` | The Flyway locations, run only when the module is enabled |
| `enabled(ConfigStore)` | The operator's switch, default true |
| `loadCondition()` | The capability gate beyond the switch, default "always loadable" |
| `start(ModuleContext)` / `stop()` | Acquire, and release in reverse |

Two of those carry the whole design.

**`enabled` is the operator's answer and `loadCondition` is the server's.** A module an
operator switched off is not started and its commands never appear in the tree. A module the
operator wants but the environment cannot satisfy, because a soft dependency is absent or the
platform is the wrong flavour, answers `loadCondition` with a human readable reason and the
console says that sentence rather than a stack trace.

**`stop()` returns only once quiescent.** It drains its own in flight async work within a
bounded wait and cancels every handle it opened. This is why the `Scheduler` port is fire and
forget with no cancellation handle: a module that needs to bound its outstanding work tracks
it itself, and a clean stop then leaves zero orphaned tasks and zero in memory state. Persisted
rows are never touched by a stop.

## 10.3 What a module owns on disk

```
modules/<id>/config.conf     the switch and every knob, with a comment per line
modules/<id>/gui/*.conf      the windows this module draws, when it has any
```

`enabled = true` is the first line of every one of them. Everything below it is a value an
operator may change without a rebuild, and the comment beside it says what changes.

## 10.4 Adding one

1. A package under `com/uxplima/uxmessentials/<id>/`, with `domain`, `application` and
   `adapter` inside it exactly as the plugin's own architecture asks.
2. A `FeatureModule` implementation that names the id and the config root.
3. `modules/<id>/config.conf`, shipped, with `enabled` first.
4. Registration with the `ModuleRegistry`, which is the one list that knows every module.
5. The guards do the rest: `IntegrationCatalogDriftTest` holds the manifest and the catalogue
   in bijection, and `SoftDependSeamDriftTest` refuses a soft dependency touched outside its
   seam.

## 10.5 What a module may not do

- Reach another module's classes. Two contexts that need the same fact share it through a port
  in `shared`, never by importing each other.
- Name a server type inside `domain` or `application`. That is what the ports are for.
- Register a command or a listener outside `commands()` and `listeners()`. A module that is
  switched off must leave no trace on the server, and both lists are read only when it is on.
- Leave work running after `stop()`.
