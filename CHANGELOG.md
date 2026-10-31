# Changelog

## [0.11.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.10.3...v0.11.0) (2026-10-31)


### Features

* **command:** correct and import playtime ([#37](https://github.com/UXPLIMA/uxm-essentials/issues/37)) ([19e1c47](https://github.com/UXPLIMA/uxm-essentials/commit/19e1c47c11506f222e6c0aa13dfec287933deb87)), closes [#36](https://github.com/UXPLIMA/uxm-essentials/issues/36)
* **config:** show each language's readers their own command words ([#47](https://github.com/UXPLIMA/uxm-essentials/issues/47)) ([ae9aa12](https://github.com/UXPLIMA/uxm-essentials/commit/ae9aa12e6ed45cdc71c5170658edd5ec3bce6a94)), closes [#46](https://github.com/UXPLIMA/uxm-essentials/issues/46)
* **integration:** answer the shared leaderboard placeholders ([#35](https://github.com/UXPLIMA/uxm-essentials/issues/35)) ([889bb01](https://github.com/UXPLIMA/uxm-essentials/commit/889bb01c848ffe866cec883d38901a054cbe9608)), closes [#34](https://github.com/UXPLIMA/uxm-essentials/issues/34)
* **integration:** rank money earned and spent ([#43](https://github.com/UXPLIMA/uxm-essentials/issues/43)) ([a124974](https://github.com/UXPLIMA/uxm-essentials/commit/a1249741d53ffd21f95c13382f21ef6e5c154ae0)), closes [#42](https://github.com/UXPLIMA/uxm-essentials/issues/42)
* **integration:** rank playtime, trades and prestige ([#41](https://github.com/UXPLIMA/uxm-essentials/issues/41)) ([9c45e88](https://github.com/UXPLIMA/uxm-essentials/commit/9c45e88a29855b3dbe17bf3dd4e54c1217dccd20)), closes [#40](https://github.com/UXPLIMA/uxm-essentials/issues/40)
* **integration:** rank votes on the shared boards ([#45](https://github.com/UXPLIMA/uxm-essentials/issues/45)) ([7a5a3c1](https://github.com/UXPLIMA/uxm-essentials/commit/7a5a3c1f41f2fac989ebc2791c881d4836651614)), closes [#44](https://github.com/UXPLIMA/uxm-essentials/issues/44)
* **integration:** step back from chat features uxmChat runs ([#26](https://github.com/UXPLIMA/uxm-essentials/issues/26)) ([d954361](https://github.com/UXPLIMA/uxm-essentials/commit/d95436147a4b63a1fa44691b8e39cd9c064a1475)), closes [#25](https://github.com/UXPLIMA/uxm-essentials/issues/25)


### Bug Fixes

* **build:** point the plugin website at its uxplima.com product page ([#24](https://github.com/UXPLIMA/uxm-essentials/issues/24)) ([5164c9d](https://github.com/UXPLIMA/uxm-essentials/commit/5164c9d7703583802e27095d8fa8a6473fab6da5))
* **core:** join hologram redraws and parse shared words once ([#20](https://github.com/UXPLIMA/uxm-essentials/issues/20)) ([18d17e2](https://github.com/UXPLIMA/uxm-essentials/commit/18d17e23a5bd1f04dd33652b8c0d47fcb3d00a89)), closes [#19](https://github.com/UXPLIMA/uxm-essentials/issues/19)
* **core:** send nametags only to clients that track the wearer ([#23](https://github.com/UXPLIMA/uxm-essentials/issues/23)) ([36eb5f5](https://github.com/UXPLIMA/uxm-essentials/commit/36eb5f5295b62a1c40d15b184336000793dce9a4)), closes [#22](https://github.com/UXPLIMA/uxm-essentials/issues/22)
* **integration:** mark vanished players for other plugins ([#28](https://github.com/UXPLIMA/uxm-essentials/issues/28)) ([01d6cdb](https://github.com/UXPLIMA/uxm-essentials/commit/01d6cdbbceefa22a9d64bf03bae23a886a192364)), closes [#27](https://github.com/UXPLIMA/uxm-essentials/issues/27)
* **menu:** put navigation where the conventions say ([#33](https://github.com/UXPLIMA/uxm-essentials/issues/33)) ([6005e79](https://github.com/UXPLIMA/uxm-essentials/commit/6005e79b7b6fecfdf7b1f5c41f7490c50c80c779)), closes [#32](https://github.com/UXPLIMA/uxm-essentials/issues/32)
* **message:** describe the new playtime subcommands in help ([#39](https://github.com/UXPLIMA/uxm-essentials/issues/39)) ([8c1a6e6](https://github.com/UXPLIMA/uxm-essentials/commit/8c1a6e6959ec1e79fdc7d4703e371f787624446a)), closes [#38](https://github.com/UXPLIMA/uxm-essentials/issues/38)

## [0.10.3](https://github.com/UXPLIMA/uxm-essentials/compare/v0.10.2...v0.10.3) (2026-10-28)

### Fixed

- An amount with a leading zero, such as `0.500` or `0.001`, is read as a decimal. Before this, `/pay 0.500` paid 500.
- The permission list describes the kit cooldown and home permissions the way the plugin checks them.

## [0.10.2](https://github.com/UXPLIMA/uxm-essentials/compare/v0.10.1...v0.10.2) (2026-10-27)

### Added

- The bossbar module: bars that stay up, timed bars and announced bars. Bars reach placeholders, the API and REST, a rotating frame is read in each player's language, and a visibility event is published.
- The pause-menu link labels are read in each player's language, and the links are sent again when a player chooses another language.

### Changed

- Every skin name opens unless the operator restricts it, and the console can dress another player.
- Menus play the sounds `config.conf` names, and every shipped window is filled with the house filler.
- Releases are tagged `vX.Y.Z` again, and the jars and a `SHA256SUMS` file are attached to every release. Commands, permissions, configuration files and stored data stay as they were.

### Fixed

- The worlds module reaches every dimension of Paper 26.2. A world can be named by its key (`minecraft:the_nether`, `mypack:mining`), by the bare path of the nether or end (`the_nether`), or by the name the server gives it (`world_nether`, `mypack_mining`). A datapack dimension is found under its own namespace and loads from there on every start.
- `/worlds import` reads the environment from the dimension when none is given, instead of answering "Unknown or incomplete command".
- A player's HUD keeps drawing after the server drops one of their renders.

## 0.0.1 (2026-10-27)

### Changed

- Versions restart at 0.0.1 under the UXPLIMA repository standard. Commands, permissions, configuration files and stored data stay as they were.
- The jar and a `SHA256SUMS` file are attached to every release.
