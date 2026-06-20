# Changelog

All notable changes to uxmEssentials are documented in this file.

## [v2.5] - 2026-05-31

Playerstate and itemworld utility verbs.

- Playerstate: `/exp`, `/air`, `/burn`, `/getpos`, `/ping` and `/rest` round out the per-player utility surface, backed by a new `PlayerInfo` port for read-only lookups and `AirAmount`/`BurnDuration`/`ExperienceChange` value objects validated at the adapter boundary.
- Itemworld item-utils: `/unbreakable`, `/disenchant`, `/itemmodel` and `/editsign` extend the item-utils sub-feature group, each input-validated before it touches the live item.

## [v2.4] - 2026-05-31

PlaceholderAPI expansion.

- A `%uxmessentials_*%` PlaceholderAPI expansion exposing homes, economy, presence, kits, vaults and moderation placeholders, registered only when PlaceholderAPI is present on the server.
- An optional `%papi%` bridge in operator message content, so configured messages can interpolate PlaceholderAPI placeholders from other plugins.
- PlaceholderAPI is a soft-depend: the integration degrades cleanly and the plugin operates normally when PAPI is absent.

## [v2.3] - 2026-05-31

Communication module.

- Communication context: custom join/quit/death messages with a per-channel `DISABLE`/`DEFAULT`/`CUSTOM` mode, MiniMessage templates with `{player}`/`{displayname}`/`{count}`/`{world}` (and `{message}` for death) placeholders, and an optional one-off broadcast on a player's first-ever join.
- A rotating announcer on a timer with a `min-players` gate and a per-player opt-out via `/broadcasttoggle`, where `RANDOM` ordering never repeats the previous line back-to-back.
- Auto-registering server-info text commands (`/rules`, `/motd`, `/info`, …) sourced from config: each declared info page registers a permission-gated command of the same name.
- The whole module ships disabled by default, so every channel defers to the vanilla server message until an operator opts in.

## [v2.2] - 2026-05-31

Presence anti-AFK matrix and mute-command-block.

- Presence: an anti-machine activity filter so automated input no longer counts as activity, no item pickup while AFK, and the sleep/skip-night flow now ignores players who are AFK or vanished.
- Moderation: a configurable set of muted-blocked-commands that closes the `/me` mute-bypass, so muted players can no longer route chat through whitelisted command aliases.

## [v2.1] - 2026-05-31

Teleport + economy polish.

## [P12] - 2026-05-31

Discord bridge.

- An optional `uxmessentials-discord` jar that stays dormant until a bot token is configured.
- A JDA-backed `DiscordGateway` port that forwards audit events (mute, jail, ban, eco-admin) and economy notifications to mapped channels.
- The gateway is consumed via the `ServicesManager`, so callers depend on the port rather than JDA directly.
- JDA work runs off-thread, keeping the bridge off the server's main thread.
- An `origin=discord` loop sentinel prevents relayed messages from echoing back into Discord.

## [P11] - 2026-05-31

Velocity cross-server sync.

- A pure `NetworkMessage` codec shared by both adapters, so the bukkit bus and the Velocity proxy serialize and parse the same wire format.
- The Velocity proxy broker ships as an optional `uxmessentials-velocity` jar that relays plugin-messaging across backends.
- The bukkit bus client carries an origin-server loop guard so a relayed message is never re-broadcast back to the server that sent it.
- Opt-in cross-server cache-coherence invalidates homes, warps, economy and vaults caches across backends, with the database remaining the shared source of truth.
- Without a proxy the system degrades cleanly to local-only operation.

## [P10] - 2026-05-31

Multi-source data migration.

- New `:migration` module built around a multi-source `Convert` port: each source registers a descriptor, and the EssentialsX importer ships first while other sources remain planned behind the same abstraction.
- `/uxmess import <source> --dry-run` plans an import without writing: it is idempotent, backup-first, and emits a per-record audit so a re-run never double-applies and every converted record is traceable.
- ACL mappers translate the imported data into the homes, warps, kits, economy and moderation contexts, so migrated state lands through the same boundaries as native writes.
- Golden-file tests pin the EssentialsX parse-and-map output, and a `SupportedMappings` drift guard fails the build when the declared mappings diverge from what the converter actually produces.
- A config-version migration ladder steps stored config forward across versions.

## [P9] - 2026-05-31

i18n and locale catalog.

- Per-player locale resolution: a player's client locale, overridable via `/lang`, propagated through the message pipeline with a `ScopedValue` so every rendered message resolves against the right catalog, with an `en` fallback chain when a key or locale is missing.
- Activated the i18n drift guards — `localeParityCheck`, `NoInlineUserMessage`, and `MessageKey`-parity checks — so locale catalogs can never drift from the `MessageKey` constants and no user-facing message is inlined outside the catalog.
- Shipped a complete Turkish (`tr`) locale carrying the full key set as the English authority.

## [P8] - 2026-05-31

Vaults.

- Vaults context: the twelfth bounded context — DB-persisted player vaults, stored with queryable owner/index/size columns alongside serialized contents, never in PDC.
- A GUI inventory-holder backs the player-facing vault, with numbered-node slot quotas governing per-vault size.
- The admin `/vault` surface is audit-logged.

## [P7] - 2026-05-31

Itemworld.

- Itemworld context: the full item/world command surface (~40 verbs) as the largest feature module, split into independently disableable sub-feature groups (item-utils, workstations, cleanup, powertool, mob-entity, time-weather, admin-fun) on top of the module-level enable switch, with a per-command disable on each verb.
- Overwhelmingly stateless, ACL-thin mutations with no persistence: every verb is input-validated at the adapter boundary before it touches the live item, entity or world, and the abusable verbs (give, spawnmob, kill/butcher/killall, lightning/fireball/kittycannon, time/weather) are audit-logged.
- `/repair`, `/repairall`, `/hat` and `/more` are now owned here — playerstate deferred them by name — so the two modules register them once and never double-register.

## [P6] - 2026-05-31

Moderation.

- Moderation context: mute, jail and tempban as the core escalation surface, plus kick, warn, banip (with alt-detection), freeze and seen, all backed by a DB-backed `ModerationProfile`.
- Every action is audit-logged and permission-gated, with ban-on-login enforcement so banned players are rejected at the door.
- Cross-wired into existing contexts: mute gates messaging delivery and jail gates teleport, so a punishment in the moderation context is enforced where the behaviour actually happens.

## [P5] - 2026-05-31

Messaging, mail and presence.

- Messaging context: private direct messages with a reply target carrying a TTL so `/r` expires gracefully, persistent text-only mail delivered to offline players and read on login, and the `/ignore`, `/socialspy`, and `/helpop` surface, all vanish-aware so hidden staff stay hidden in delivery and conversation routing.
- Presence context: automatic and manual AFK with vanish support, an in-memory `PlayerPresence` model, a sweep timer that ages idle players into AFK, and integration with the visibility layer so presence transitions respect who can see whom.
- Tests for the messaging domain/use cases and persistence, plus the presence model and sweep behaviour.

## [P4] - 2026-05-31

Kits and player-state contexts.

- Kits context: config-driven kit definitions with per-kit cooldown/one-time claim tracking persisted via PDC, permission gating plus an optional economy cost soft-coupled through the economy port, and the authoring command surface (`/createkit`, `/delkit`, `/kiteditor`, `/kitreset`, `/showkit`, `/kits`, `/kit`).
- Player-state context: an immutable per-player snapshot held in a `ConcurrentHashMap` and reconciled on the owning region thread, covering god/fly/heal/feed/gamemode/speed plus utility verbs (`/extinguish`, `/suicide`, `/near`, `/nightvision`, `/ptime`, `/pweather`).
- Tests for the kit domain/use cases and claim store, plus the player-state snapshot, value objects, use cases, and region-thread reconciliation.

## [P3] - 2026-05-30

Economy context and provider abstraction.

- Multi-currency economy domain (`Wallet`, `Money`, `Currency`, `Transaction`): DB-backed and double-spend-guarded, with debounced writes through a cached wallet repository so balance mutations stay consistent under load without thrashing the database.
- `EconomyProvider` port with a native ledger implementation plus Treasury and Vault adapters wired through the Bukkit `ServicesManager` using a register-or-defer strategy, so uxmEssentials can serve as the economy provider or yield to an existing one.
- Player economy commands (`/balance`, `/pay`, `/baltop`) and the `eco` admin surface, backed by the `EconomyProviderContractTest` that pins adapter behaviour to the port contract.

## [P2] - 2026-05-30

Persistence layer, homes and warps.

- Activated the `:persistence-adapter` module: jOOQ code generation (the generator parses the Flyway `V1__init.sql` baseline through the DDLDatabase so the typed classes can never drift from the runtime schema), Flyway migrations, and a HikariCP connection pool. SQLite is the default single-node backend, with MySQL/MariaDB and PostgreSQL options activated via configuration, and a Caffeine read-through cache in front of the repositories.
- Homes context: quota-gated home CRUD (`/sethome`, `/delhome`, `/movehome`, `/renamehome`, `/homes`, `/home`, plus the admin surface) where the per-player limit is resolved from permission-backed quotas, with teleport delegated to the teleport context.
- Warps context: operator-curated warps (`/setwarp`, `/delwarp`, `/movewarp`, `/warps`, `/warp`, `/warpinfo`) with an optional per-warp required permission and cost soft-coupled to the economy port so warps degrade gracefully when no economy is present.
- Tests for the home/warp domain value objects and `HomeSet`, the command-path guards, and the jOOQ repository integration tests (embedded SQLite plus Testcontainers MySQL/PostgreSQL).

## [P1] - 2026-05-30

Shared kernel and the teleport context.

- Shared value objects (`PlayerRef`, `WorldRef`, `Position`, `Result`, `Unit`, `DomainEvent`) and the outbound ports for `Scheduler`, `Permissions`, `Cooldowns`, `Warmups`, `Messages`, `ConfigStore`/`ScopedConfigStore`, `LocaleCatalog`, `MessageSink`, `Logger`, player/world lookups, and the domain-event publisher.
- Folia-aware adapter implementations: `FoliaScheduler`, `BukkitPermissions` (with LuckPerms meta source and quota-node reducer), `PdcCooldowns`, `SchedulerWarmups`, the catalog/HOCON `Messages`, and the Configurate-backed config store, all wired through `KernelPorts`/`KernelWiring`.
- Teleport context: the cooldown/warmup engine with a configurable cooldown start phase and move-cancels-warmup toggles, the TPA request/accept/deny/cancel flow with expiry sweep, `/back` capture and restore, `/rtp` backed by a pre-warmed safe-location queue and safe-search validator, `/spawn` plus respawn resolution and set-spawn directory, and the full teleport command surface including the admin tp commands.
- Tests for the teleport engine, requests, and pending teleports, plus the quota-node reducer, teleport command-path, and module-registry drift guards.

## [P0] - 2026-05-30

Initial project scaffolding and the feature-module framework.

- Gradle 6-module Kotlin-DSL build (api, core, bukkit-adapter, persistence-adapter, discord-adapter, velocity-adapter) with a shared `java-conventions` plugin and a version catalog.
- `FeatureModule` contract plus `ModuleRegistry` and the gated `PluginModule` wiring that loads modules behind explicit load conditions.
- `paper-plugin.yml` bootstrap/loader and the `/uxmess` Brigadier command root.
- ArchUnit core-purity fences and a module-registry drift guard to keep the architecture honest.
