# Changelog

## [0.12.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.11.0...v0.12.0) (2026-11-04)


### ⚠ BREAKING CHANGES

* **core:** the `regions` module, `/regions` and its `uxmessentials.regions.*` nodes are removed. Region placeholders keep their `regions_*` names and are answered by guard; `regions_available` now says whether guard is on.

### Features

* **command:** refuse with the catalog line, never Brigadier's text ([#65](https://github.com/UXPLIMA/uxm-essentials/issues/65)) ([40d13cb](https://github.com/UXPLIMA/uxm-essentials/commit/40d13cb139fde7ac64cd720298359d609ad21aa9))
* **config:** apply command-control edits on reload ([#56](https://github.com/UXPLIMA/uxm-essentials/issues/56)) ([b180d89](https://github.com/UXPLIMA/uxm-essentials/commit/b180d896ed5917e1c29a638b762793836e98ddf3))
* **config:** cover a whole plugin with one command-control entry ([#58](https://github.com/UXPLIMA/uxm-essentials/issues/58)) ([4a15105](https://github.com/UXPLIMA/uxm-essentials/commit/4a151050cbad1e053ede7fbefe71d2096aa3d95c))
* **config:** read command-control entries down to their arguments ([#57](https://github.com/UXPLIMA/uxm-essentials/issues/57)) ([a49ccea](https://github.com/UXPLIMA/uxm-essentials/commit/a49ccea18170d11b8c29562ee643b36f0c44c953))
* **core:** add the guard module and remove regions ([#62](https://github.com/UXPLIMA/uxm-essentials/issues/62)) ([98d7f41](https://github.com/UXPLIMA/uxm-essentials/commit/98d7f4189de78ed7412282bbc0096bb9b603d3ae))
* **core:** hold the server's operators to an allowed list ([#59](https://github.com/UXPLIMA/uxm-essentials/issues/59)) ([b90a389](https://github.com/UXPLIMA/uxm-essentials/commit/b90a389ce2b83c285d3cfd699eac77afc90324a9))


### Bug Fixes

* **command:** let command words use the letters of every language ([#60](https://github.com/UXPLIMA/uxm-essentials/issues/60)) ([57cb020](https://github.com/UXPLIMA/uxm-essentials/commit/57cb02030d845f29a1f55ec132b77ef4842cc3ea))
* **integration:** import a passthrough deny global region as protected ([#63](https://github.com/UXPLIMA/uxm-essentials/issues/63)) ([dda7687](https://github.com/UXPLIMA/uxm-essentials/commit/dda7687700d8af86dc3808368ead4262dd4faed3))
* **menu:** write the example menu title without colour ([#55](https://github.com/UXPLIMA/uxm-essentials/issues/55)) ([18cb506](https://github.com/UXPLIMA/uxm-essentials/commit/18cb506cc4231cba8ba317aa52b1d0f7a23aa674))
* **message:** describe /setspawn as the current world's spawn ([#61](https://github.com/UXPLIMA/uxm-essentials/issues/61)) ([3eb993c](https://github.com/UXPLIMA/uxm-essentials/commit/3eb993cb435f8a84cfa511b68ccdaf551229a050))
* **message:** say how long a kit cooldown has left ([#64](https://github.com/UXPLIMA/uxm-essentials/issues/64)) ([b745bf0](https://github.com/UXPLIMA/uxm-essentials/commit/b745bf010fd2da0df124d6c1d9bb1e4788aa2c4d))
* **message:** write the Latin-script languages in small capitals ([#53](https://github.com/UXPLIMA/uxm-essentials/issues/53)) ([7b9219a](https://github.com/UXPLIMA/uxm-essentials/commit/7b9219ab41fefaf5230e592c2bbd7353dbab9ef5))

## [0.11.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.10.3...v0.11.0)

### Added

- Correct and import playtime.
- Show each language's readers their own command words.
- Answer the shared leaderboard placeholders.
- Rank money earned and spent.
- Rank playtime, trades and prestige.
- Rank votes on the shared boards.
- Step back from chat features uxmChat runs.

### Changed

- The warp picker's effect and sound presets are named in every language's messages file.
- The network block names this backend with `server-name`; a file that still says `server-id` keeps working.
- The shipped `config.conf` lists `storage` right after `language`, and `menu` last.
- The SQLite database lives under data/ and moves there by itself on the first start. The config copy is in backups/.

### Fixed

- Point the plugin website at its uxplima.com product page.
- Join hologram redraws and parse shared words once.
- Send nametags only to clients that track the wearer.
- Mark vanished players for other plugins.
- Put navigation where the conventions say.
- Describe the new playtime subcommands in help.

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
