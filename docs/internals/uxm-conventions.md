# UXM plugin conventions

How every UXPLIMA Minecraft plugin is built and how it behaves for an operator and a player. The canonical copy lives in `UXPLIMA/.github` and is synced into each plugin as `docs/internals/uxm-conventions.md`. A plugin changes this page only by changing the canonical copy.

Contents: [Platform](#platform) · [Architecture](#architecture) · [Threading](#threading) · [Configuration](#configuration) · [Messages and style](#messages-and-style) · [Commands](#commands) · [Permissions](#permissions) · [Placeholders](#placeholders) · [Menus](#menus) · [Storage](#storage) · [Rewards and effects](#rewards-and-effects) · [Items a plugin hands out](#items-a-plugin-hands-out) · [Events](#events) · [Logging](#logging) · [Packaging](#packaging) · [Code style](#code-style)

## Principles

1. **Standalone.** A plugin works when it is the only plugin of ours on the server. It never depends on another UXM plugin. Two plugins that want the same behavior each implement it, or one publishes a service the other reads only if it is present.
2. **uxmLib is mechanism, not game.** Schedulers, menus, storage, commands, text and items belong in uxmLib. Prices, curves, caps and rules belong in the plugin that plays them.
3. **Nothing hardcoded.** A value an operator could want to change is configuration. A word a player reads is in the message catalog.
4. **Every shipped example works on a bare server.** The first boot writes content that does what it says with nothing else installed.
5. **Bedrock is the floor.** Every menu a Java player can open, a Bedrock player gets as a native form.
6. **A replacement is a superset.** A plugin that takes over a feature from another plugin of ours keeps everything an operator used there (language keys, placeholders and their relational forms, effects) and imports that plugin's files and data, language lines included. A gap found later is fixed in the plugin and written into this page.

## Platform

- Paper 26.2 and later, `compileOnly`. No support for older versions, Spigot or forks below Paper.
- Java 25 toolchain, Java 21 bytecode for uxmLib modules that never touch a server, Java 25 bytecode for plugins.
- `paper-plugin.yml`, never `plugin.yml`. `folia-supported: true`.
- Velocity plugins and Velocity sides of a plugin follow the same rules where they apply; the Paper-only sections (menus, items) do not.
- Bedrock players reach the server through Geyser or Floodgate. Plugins use `uxmlib-bedrock` and never name a Geyser or Floodgate class directly.

## Architecture

Hexagonal with domain-driven design, at every size.

```
com.uxplima.<slug>/
  domain/              entities, value objects, domain services, domain events. Plain Java only.
  application/         use cases. One class per use case, named as a verb phrase.
  application/port/    interfaces the outside implements (repositories, clocks, gateways).
  adapter/inbound/     commands, listeners, menus, placeholder providers. Translate input into a use case call.
  adapter/outbound/    implementations of ports: storage, the server, other plugins.
  bootstrap/           the plugin class, the loader and the module that wires the graph by hand.
```

- `domain` imports no `org.bukkit`, `io.papermc`, `net.kyori`, `org.slf4j` or uxmLib type. `application` imports no adapter. One ArchUnit test enforces the direction and the forbidden APIs listed under [Threading](#threading) and [Messages and style](#messages-and-style).
- A larger plugin splits by bounded context first (`com.uxplima.crates.key`, `com.uxplima.crates.reward`), each with the same layers inside.
- Value objects are records that validate in their compact constructor. An invalid value never exists.
- An aggregate is changed only through its root. Repositories load and save whole aggregates.
- A closed set of cases is a `sealed interface` with records. A use case returns a sealed `Outcome`, never throws for a business rule.
- Constructor injection only. No singletons, no static state, no `JavaPlugin.getPlugin`, no service locator.
- A port exists when there is a real platform boundary, a second implementation or a test seam. Its javadoc names which.
- Names: a use case is a verb phrase (`ClaimDailyReward`), a port says what (`RewardRepository`), an adapter says the technology first (`SqlRewardRepository`).

## Threading

The plugin runs on Paper and on Folia, so there is no main thread to rely on.

- Everything schedules through uxmLib's `Scheduler` port. `BukkitScheduler`, `BukkitRunnable` and `Bukkit.getScheduler()` are forbidden.
- Entity state is touched on that entity's scheduler, block and world state on the region scheduler of that location, global state (world time, broadcasts) on the global scheduler.
- Database, file and network I/O run on the async scheduler, never on a region or entity thread.
- An async task never calls the Bukkit API. It hops back with `scheduler.entity(...)` or `scheduler.region(...)` before it touches the world or a player.
- Shared mutable state is an immutable snapshot behind a `volatile` field, swapped whole, or a concurrent collection. No `synchronized` around Bukkit calls.
- A cache that another server can change is refreshed through `DataSynchronizers` (see [Storage](#storage)).

## Configuration

### Files

| File | Holds |
|---|---|
| `config.conf` | behavior: switches, numbers, slots, materials, sounds, the `storage` and `language` blocks |
| `commands.conf` | name, aliases, localized aliases and enabled state of every command and subcommand |
| `messages/messages_<tag>.conf` | every word a player reads; `en` and `tr` ship complete |
| `menus/<name>.conf` | one file per menu: layout, items, actions |
| content files (`crates.conf`, `jobs/<id>.conf`) | operator content: the things the plugin is about |
| `plugins/uxmTheme/theme.conf` | the shared palette and roles; the only file with a hex code |

A word in `config.conf` is a defect, and a slot number in `messages/` is a defect. Words an operator authors (a warp label, a crate name) stay with their content, and every such field also takes a catalog line: a value that starts with `@` (`display = "@custom.tag.vip"`) names a line of the messages files and is drawn in each reader's language through `Messages.expandLine`. A field that shows text to players and cannot take `@` is a defect.

### Format

- HOCON (`.conf`) through Configurate, two space indent, kebab-case keys.
- Order inside `config.conf`: general values, `language`, `storage`, feature blocks, `menu`, content references.
- Every block opens with a comment that says what it does and what a wrong value causes. Comments give ranges and consequences (`# 1 to 6. Higher values cost more memory.`), never types.
- Every default works: a fresh server starts without an edit. A value only the operator can fill (a webhook URL) ships empty with a comment.
- No secret, no absolute path, no value duplicated across files.

### Code

- One `PluginSettings` class reads every file into immutable records. Nothing else reads a path by string.
- The snapshot is read on enable and on reload, then swapped whole. A parse error on reload keeps the previous snapshot and logs the file, line and reason.
- On every start `PluginSettings.bringUpToDate` merges keys a release added into the operator's file, keeps every value the operator wrote, and writes `config.conf.bak` first. There is no `config-version` key.
- A map whose keys the operator chooses (groups, ranks, currencies, worlds, materials, levels, macros) is named in `PluginSettings.OPERATOR_LISTS` and passed to `mergeDefaults`. Once the operator's file has such a map, it is theirs whole: a shipped example they removed is never put back. A map whose keys the plugin defines (moments, token kinds, claim providers) is not on the list, so a key a release adds still arrives; it is turned off with `enabled = false` or `false`, not by removing it. A shipped example that comes back after an operator removed it is a defect, and a test removes one and checks it stays gone.
- Language files are kept current with `LanguageFileUpdates.bringUpToDate`: an untouched shipped line takes the new wording, an edited line stays the operator's.
- `/<root> reload` reloads every file in one step and reports what changed or failed.

## Messages and style

### Text

- All text is Adventure components from MiniMessage through uxmLib `Text`. `ChatColor`, `&` and `§` codes are forbidden.
- Text a player typed is never parsed as MiniMessage. Use `Text.paint`.
- Every message has a key in a `Keys` enum with an English default, so a deleted line still reads.
- Keys are dotted lower case, grouped by feature: `crate.open.no-key`, `command.reload.done`.
- Placeholders inside a message are named tags (`<player>`, `<amount>`), never positional.
- A message is sent through the uxmLib message channel; whether it goes to chat, action bar, title or boss bar is the operator's choice in `config.conf`, not a second message.

### Language

- `language { default, follow-client, force }` in `config.conf`. Order: forced language, the player's choice, the client language while `follow-client` is on, the default.
- The player's choice is shared across all UXM plugins through uxmLib `SharedLanguage` (`uxmlib:language`). Nothing else reads `player.locale()`.
- Player-facing wording is English or Turkish written for the player, short sentences, active voice.
- Text is drawn in the language of the player who reads it, not of the player it is about. A line many players read that holds language text (a tag, a channel name) is drawn once per reader language, not once for everybody, and a line sent to another server carries one drawing per language.

### Look

- Colors come from roles in `theme.conf` (`<accent>`, `<value>`, `<good>`, `<bad>`, `<money>`), never hex codes in a plugin.
- Body text is white. A state color marks a state, never emphasis: green succeeded, red failed, gold is money or attention.
- A chat line is `<tag:'FEATURE'> <body>sentence with a <value>value</value>.`: one bold feature word, the `▶` separator, white body, at most three colored spans, no result glyph.
- Lines that are embedded elsewhere (list rows, help lines, formats, placeholder output) carry no prefix.
- The interface is written in small capitals through `Typography`, for the languages `theme.conf` names. Values a player wrote are never converted.

## Commands

- Built with uxmLib's annotation DSL on Paper's Brigadier lifecycle. Each handler is `@FromConfig`, never also `@Command`.
- One root per plugin, named after the plugin (`/uxmcrates`), plus a short alias in the shipped `commands.conf` (`/crates`). Every command and subcommand is renameable, aliasable, disableable and takes localized aliases from `commands.conf`.
- Subcommand names are one lower-case word, a verb where the branch acts (`give`, `reset`) and a noun where it lists or opens (`list`, `menu`).
- The root always runs: it opens the menu, shows the list, or sends one line that points to help. Every intermediate word runs too. A player never sees Brigadier's own error text.
- Standard admin branches in every plugin: `reload`, `lang set-player <player> <tag>`, `lang reset-player <player>`, and `help`.
- Every command works from the console unless it needs a player, and says so with `@PlayerOnly`.
- Arguments complete: player names, ids of the plugin's own content, numbers with their range.
- Work that touches storage runs async; the reply comes back on the sender's scheduler.
- Every refusal (no permission, bad argument, cooldown) is a catalog line in the player's language.

## Permissions

- Format: `<plugin>.<feature>.<action>`, all lower case, the plugin id without separators: `uxmcrates.key.give`.
- Player features: `<plugin>.<feature>` or `<plugin>.<feature>.<action>`, default `true` when every player should have it.
- Acting on someone else: `.others` suffix: `uxmhomes.home.teleport.others`.
- Administration: `<plugin>.admin.<feature>`, default `op`. `reload` is `<plugin>.admin.reload`.
- Exemptions: `<plugin>.bypass.<rule>`, default `false`: `uxmchat.bypass.cooldown`.
- Numeric limits: `<plugin>.limit.<thing>.<n>`, the highest granted `n` wins, the configured default applies without one: `uxmhomes.limit.homes.5`.
- Every node is declared in `paper-plugin.yml` with a description and a default, and every node the code checks is declared. `<plugin>.admin` and `<plugin>.*` parents group them.
- Permission checks go through one `Permissions` class of constants; no node string appears elsewhere.

## Placeholders

- Registered through uxmLib's PlaceholderAPI integration, only when PlaceholderAPI is present.
- Format: `%uxm_<plugin>_<path>%`, lower case, `_` between words: `%uxm_crates_keys_<crate>%`, `%uxm_jobs_level_<job>%`.
- A provider answers for an `OfflinePlayer`. A value that needs an online player returns the configured empty value for an offline one.
- A number is returned raw; `_formatted` returns it formatted for display: `%uxm_jobs_points%`, `%uxm_jobs_points_formatted%`.
- Every placeholder whose value is text a player reads (a tag, a title, a name with a language key) also answers as a relational placeholder, `%rel_<plugin>_<path>%`, in the reader's language. Other plugins show it in chat, the tab list and nametags, which are drawn for each reader.
- A placeholder never returns null and never throws. An unknown path returns empty.
- A placeholder never does I/O. It reads cached state; a cache miss returns the empty value and loads in the background.
- Leaderboards: `%uxm_<plugin>_top_<board>_<rank>_name%` and `_value`.
- Every placeholder is listed in `docs/user/` with an example value.

## Menus

- A menu is a file under `menus/`, run by `uxmlib-menu`. Code registers what menus may say and do through `MenuBindings`; it does not build windows by hand.
- Window title: centered with `MenuTitles.centre`, no color, no bold, no decoration.
- A tile's display name is a single space; its title is the first lore line. Lore order: title, category, blank, description, blank, details, blank, click hint. Built with `Tiles` and `LoreWrap`.
- Navigation: `← previous page` and `→ next page` on arrows, `« back` on a feather, page counter muted. No close button. Glyphs are part of the catalog line.
- Navigation sits in the middle of the bottom row: `« back` in the centre slot, `← previous page` directly left of it, `→ next page` directly right of it (slots 48, 49 and 50 in six rows, 21, 22 and 23 in three). The arrows are drawn on every page, and one with no page to go to does nothing. A menu with nothing to go back to leaves the centre to the frame, and no other button takes these three slots.
- Materials carry meaning (money a gold ingot, danger TNT). No decorative heads, no glow except as a selected state. Empty slots take a gray pane with a hidden tooltip and no sound.
- A destructive action asks once, in one confirm window, on Java and Bedrock alike.
- Sounds: open, click, page and denied come from `menu.sounds` in `config.conf` through `MenuSoundsFile`; volume 0.5 to 0.7. A button with its own sound plays only that.
- Every menu has a Bedrock form from the same file, through `uxmlib-menu`.
- In-game editors exist only where operators build objects while the server runs (crates, jobs, shops). Catalog-style content stays in files.

## Storage

- One `storage` block in `config.conf`, read by `StorageSettings.read`: `type` (`sqlite`, `mysql`, `mariadb`, `postgresql`), `file`, `host`, `port`, `database`, `username`, `password`, `table-prefix`, `pool-size`. An unknown type stops the plugin.
- SQLite is the default and works with no edit. MariaDB and PostgreSQL drivers are fetched by the plugin loader from `StorageDrivers.coordinates()`; no plugin shades a JDBC driver.
- Schema changes are SQL migrations in `src/main/resources/db/migration/V<n>__<name>.sql`, applied by `storage.migrate`. A migration is never edited after release; a change is a new migration. A plugin whose migrations are not found refuses to start.
- Every table and index name is `{prefix}name`. The history table is `<prefix>schema_history`.
- SQL every backend accepts: `DOUBLE PRECISION`, `SMALLINT`, no `IF NOT EXISTS` on indexes, no default on `TEXT`. Any order a player sees comes from an `ORDER BY`.
- All queries use bound parameters (`Sql`, `SelectBuilder`). No string-built SQL.
- All storage calls run async. Player data loads on join (async pre-login where possible) and saves on change or on a timer, and on quit.
- A plugin whose rows another server can write uses `DataSynchronizers.forStore` with `storage.sync-seconds`.
- Every repository adapter is tested with `@EveryBackend` on all four backends.

## Rewards and effects

- Anything a plugin gives is a list: items, commands, currencies. A record never holds a single reward field.
- Requirements and rewards are declarative and run through `uxmlib-condition`: conditions with a failure policy, actions in `[verb] payload` form, parsed once at load.
- Every interaction (refusal, win, purchase, level up) fires a configurable list of effects: message, title, action bar, boss bar, sound, particle, command.
- Every change to a player's own choice fires such a list too: `set`, `cleared`, and `lapsed` when the plugin takes the choice back because the player lost it (checked on join). The shipped list holds the message as `[message] @key`, so an operator can add a sound or write `[]` for silence. A fixed message in code instead of a list is a defect.
- Payments go through the wallet (`[give-money]`, `[take-money]`), never a console command against another plugin. Shipped payments use `exp`, `levels` or the plugin's own balance; `vault` appears in the comment as the alternative.
- Shipped commands in content are vanilla commands only.
- Every seam an action needs (command sink, broadcast audience, boss bar timer) is wired; an unwired seam throws at load with the method to call.

## Items a plugin hands out

- Placed with `HeldItems.place` and removed with `HeldItems.takeBack`: never overwrite or move another item, one copy per player.
- Locked with `HeldItemGuard`: clicks, number keys, off hand, drag, drop, place and death drops. One switch turns the lock off.
- Recognized by a persistent data key, never by material or name.

## Events

- Listeners are inbound adapters that call a use case; no business logic in a listener.
- `ignoreCancelled = true` unless the listener must see cancelled events, with the reason in a comment.
- Priority: `LOWEST` to cancel early, `NORMAL` by default, `MONITOR` only to observe and never to change the event.
- Paper events (`AsyncChatEvent`, `PlayerJoinEvent` with components) are preferred over legacy Bukkit ones.

## Logging

- The plugin's component logger. One INFO line on enable with the version, storage backend and counts of loaded content. No banners.
- WARN for an operator mistake with the file, line and fix; ERROR for a fault with its exception.
- No log line per player action at INFO. Debug output sits behind `debug = false` in `config.conf`.

## Packaging

- Shadow builds the jar. uxmLib and runtime libraries are relocated under `com.uxplima.<slug>.libs`. Adventure, slf4j and the annotation libraries are never shaded; `org.sqlite` is never relocated; `mergeServiceFiles()` stays on.
- The build verifies the jar and fails on a forbidden or missing package.
- uxmLib is taken as individual modules, never `uxmlib-all`.
- Jar name: `uxm<Name>-<version>.jar`.

## Code style

- Formatting is Palantir Java Format through Spotless; the build fails on unformatted code. Kotlin build scripts use ktlint.
- Every package has `package-info.java` with `@NullMarked`. NullAway and Error Prone run as errors with `-Werror`.
- Public methods validate arguments at the boundary; value objects validate in the constructor.
- No empty catch, no `printStackTrace`. A caught exception is named for what it is, not `e`.
- Prefer records, sealed types, pattern matching and `switch` expressions. No Lombok.
- Comments say why, never what. No dates, names, history, issue numbers or decision ids in comments; git and the pull request hold those. A disabled check or an accepted cost carries its reason in one line.
- Javadoc is required on uxmLib's public API and on ports; elsewhere only where intent is not obvious.
- English everywhere in code, configuration and documentation. No em dash or en dash.
