# Changelog

All notable changes to uxmEssentials are documented in this file.

## [P0] - 2026-05-30

Initial project scaffolding and the feature-module framework.

- Gradle 6-module Kotlin-DSL build (api, core, bukkit-adapter, persistence-adapter, discord-adapter, velocity-adapter) with a shared `java-conventions` plugin and a version catalog.
- `FeatureModule` contract plus `ModuleRegistry` and the gated `PluginModule` wiring that loads modules behind explicit load conditions.
- `paper-plugin.yml` bootstrap/loader and the `/uxmess` Brigadier command root.
- ArchUnit core-purity fences and a module-registry drift guard to keep the architecture honest.
