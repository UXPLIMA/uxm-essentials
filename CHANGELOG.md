# Changelog

All notable changes to uxmEssentials are documented in this file.

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
