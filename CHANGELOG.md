# Changelog

## [0.13.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.12.0...v0.13.0) (2026-11-18)


### ⚠ BREAKING CHANGES

* **core:** `/eco giveall` and `/eco resetall` now change the balance of every player who has joined a server of the database, online or not, instead of the players online on the server that runs them. A scheduled `eco giveall <amount>` keeps its old meaning only with `--online`. For item currencies and experience the change is delivered when each player is next online; through another plugin's economy players are paid one by one at most once. A finished run on the ledger fires one `UxmBalanceSweepEvent` instead of a `UxmWalletCreditEvent` per account; for an item currency or experience each delivery fires the credit (or, for a reset that takes, the debit) event when it is handed over.
* **proxy:** with `network.transport = velocity` or `both`, the plugin channel stays closed until every backend sets the same `network.bus.secret` (at least 32 characters). Set it on every backend before upgrading; see docs/user/upgrading.md.
* **core:** every exemption node moved under `uxmessentials.bypass.` (full table and LuckPerms commands in `docs/user/upgrading.md`), and placeholders are `%uxm_essentials_<key>%`. Operators move their grants; the old `%uxmessentials_<key>%` placeholders keep answering, with a warning per key, until a later release removes them.
* **core:** keep managed worlds per server and teleport across them ([#155](https://github.com/UXPLIMA/uxm-essentials/issues/155))

### Features

* **api:** look players up by name in REST and the Java API ([#375](https://github.com/UXPLIMA/uxm-essentials/issues/375)) ([a7286b8](https://github.com/UXPLIMA/uxm-essentials/commit/a7286b8ae4178447e03fb45be352883332517894)), closes [#336](https://github.com/UXPLIMA/uxm-essentials/issues/336)
* **api:** move player text out of java into the catalogs ([#176](https://github.com/UXPLIMA/uxm-essentials/issues/176)) ([58910b0](https://github.com/UXPLIMA/uxm-essentials/commit/58910b02999792bb0753f7db613f038352b7b57d)), closes [#94](https://github.com/UXPLIMA/uxm-essentials/issues/94)
* **config:** choose whether paid custom commands run without a backend ([#131](https://github.com/UXPLIMA/uxm-essentials/issues/131)) ([3865339](https://github.com/UXPLIMA/uxm-essentials/commit/386533999bcd73e043c8486644f90e3c9cd2ec44)), closes [#99](https://github.com/UXPLIMA/uxm-essentials/issues/99)
* **config:** hard-delete player warps left archived for unpaid rent ([#141](https://github.com/UXPLIMA/uxm-essentials/issues/141)) ([48e20fe](https://github.com/UXPLIMA/uxm-essentials/commit/48e20fe974a3071f6adf37969ac0d289778d937a)), closes [#111](https://github.com/UXPLIMA/uxm-essentials/issues/111)
* **core:** cache rank pointers and save rankups off the tick ([#119](https://github.com/UXPLIMA/uxm-essentials/issues/119)) ([585657c](https://github.com/UXPLIMA/uxm-essentials/commit/585657c9e807d45e0d5fcb373316d7d1cfa7220e)), closes [#69](https://github.com/UXPLIMA/uxm-essentials/issues/69)
* **core:** carry msg, helpop and staff chat across the network ([#148](https://github.com/UXPLIMA/uxm-essentials/issues/148)) ([d9ac9fe](https://github.com/UXPLIMA/uxm-essentials/commit/d9ac9fe4104861ebdb7b8932d67f68763eb1bfed)), closes [#83](https://github.com/UXPLIMA/uxm-essentials/issues/83)
* **core:** handle bedrock players who share a geyser address ([#380](https://github.com/UXPLIMA/uxm-essentials/issues/380)) ([4308ab0](https://github.com/UXPLIMA/uxm-essentials/commit/4308ab074258679903ae63d5d07155fdc4a73784)), closes [#338](https://github.com/UXPLIMA/uxm-essentials/issues/338)
* **core:** jail players across the servers of a network ([#152](https://github.com/UXPLIMA/uxm-essentials/issues/152)) ([3fee39d](https://github.com/UXPLIMA/uxm-essentials/commit/3fee39d02c21d7ebd2254b61d028bc5e010074dd)), closes [#81](https://github.com/UXPLIMA/uxm-essentials/issues/81)
* **core:** keep managed worlds per server and teleport across them ([#155](https://github.com/UXPLIMA/uxm-essentials/issues/155)) ([054f6ba](https://github.com/UXPLIMA/uxm-essentials/commit/054f6ba95e0bf9c84ae9cc7b8a0752d65ab0fe80)), closes [#80](https://github.com/UXPLIMA/uxm-essentials/issues/80)
* **core:** know which server each online player is on ([#128](https://github.com/UXPLIMA/uxm-essentials/issues/128)) ([6c0ed2f](https://github.com/UXPLIMA/uxm-essentials/commit/6c0ed2f5654a02a7905cb5bb9b3033627ba5c5ef)), closes [#77](https://github.com/UXPLIMA/uxm-essentials/issues/77)
* **core:** let moderation name accounts that never joined ([#381](https://github.com/UXPLIMA/uxm-essentials/issues/381)) ([3d7b3ef](https://github.com/UXPLIMA/uxm-essentials/commit/3d7b3ef367b87d8df01010cd95eeac776dc06081)), closes [#347](https://github.com/UXPLIMA/uxm-essentials/issues/347)
* **core:** list and suggest players from the whole network ([#385](https://github.com/UXPLIMA/uxm-essentials/issues/385)) ([f7e01fa](https://github.com/UXPLIMA/uxm-essentials/commit/f7e01fa65912b3af4ef9351699f2e2c2a38053f8)), closes [#344](https://github.com/UXPLIMA/uxm-essentials/issues/344)
* **core:** open another player's vault while they are offline ([#376](https://github.com/UXPLIMA/uxm-essentials/issues/376)) ([ce74982](https://github.com/UXPLIMA/uxm-essentials/commit/ce749825dbff2d346e42fa469687c02e8b87a082)), closes [#319](https://github.com/UXPLIMA/uxm-essentials/issues/319)
* **core:** pay a vote at once when the voter is on another server ([#387](https://github.com/UXPLIMA/uxm-essentials/issues/387)) ([a159e09](https://github.com/UXPLIMA/uxm-essentials/commit/a159e096fad179cf2fd3d0a122595d3efbc84ae3)), closes [#328](https://github.com/UXPLIMA/uxm-essentials/issues/328)
* **core:** pay offline players and players on other servers ([#396](https://github.com/UXPLIMA/uxm-essentials/issues/396)) ([1ff3b28](https://github.com/UXPLIMA/uxm-essentials/commit/1ff3b2843748d0c46c17cdbd2fdad3a1cb8e6a98)), closes [#321](https://github.com/UXPLIMA/uxm-essentials/issues/321)
* **core:** rename bypass nodes and placeholders to shared names ([#181](https://github.com/UXPLIMA/uxm-essentials/issues/181)) ([7561398](https://github.com/UXPLIMA/uxm-essentials/commit/7561398ca89162d305a9dc0a61449b5798b7516e)), closes [#92](https://github.com/UXPLIMA/uxm-essentials/issues/92)
* **core:** reset kits for players who are offline ([#373](https://github.com/UXPLIMA/uxm-essentials/issues/373)) ([ac12267](https://github.com/UXPLIMA/uxm-essentials/commit/ac122676e0a6811bafc0c4775cf60a429de5aa17)), closes [#318](https://github.com/UXPLIMA/uxm-essentials/issues/318)
* **core:** restore an inventory snapshot to an offline player ([#379](https://github.com/UXPLIMA/uxm-essentials/issues/379)) ([8e20894](https://github.com/UXPLIMA/uxm-essentials/commit/8e20894a8bc8689d0f64c8f51b1d66e3b625f9c8)), closes [#348](https://github.com/UXPLIMA/uxm-essentials/issues/348)
* **core:** scope holograms, npcs and main spawns to servers ([#150](https://github.com/UXPLIMA/uxm-essentials/issues/150)) ([f44e5b4](https://github.com/UXPLIMA/uxm-essentials/commit/f44e5b49b65db7621edc7b4b7774598a0f03b11b)), closes [#86](https://github.com/UXPLIMA/uxm-essentials/issues/86)
* **core:** send players to homes on other servers ([#151](https://github.com/UXPLIMA/uxm-essentials/issues/151)) ([336811f](https://github.com/UXPLIMA/uxm-essentials/commit/336811f5571952a56f4faec0f1be1feb6bd9fae1)), closes [#79](https://github.com/UXPLIMA/uxm-essentials/issues/79)
* **core:** send players to warps on other servers ([#147](https://github.com/UXPLIMA/uxm-essentials/issues/147)) ([2549403](https://github.com/UXPLIMA/uxm-essentials/commit/25494037acdacbfbc5d9d50c1373f2e6244cf2bd)), closes [#139](https://github.com/UXPLIMA/uxm-essentials/issues/139) [#78](https://github.com/UXPLIMA/uxm-essentials/issues/78)
* **core:** send tpa and staff tp to players on other servers ([#154](https://github.com/UXPLIMA/uxm-essentials/issues/154)) ([9824d63](https://github.com/UXPLIMA/uxm-essentials/commit/9824d63db61e8e7e585ee4c3b36e579126f7481d)), closes [#82](https://github.com/UXPLIMA/uxm-essentials/issues/82)
* **core:** share the cross-server hop across teleport kinds ([#135](https://github.com/UXPLIMA/uxm-essentials/issues/135)) ([79a3ce9](https://github.com/UXPLIMA/uxm-essentials/commit/79a3ce9d6668e8fafd1a5a57cef91b967017ffea)), closes [#76](https://github.com/UXPLIMA/uxm-essentials/issues/76) [#130](https://github.com/UXPLIMA/uxm-essentials/issues/130)
* **integration:** draw stored skins on board heads ([#423](https://github.com/UXPLIMA/uxm-essentials/issues/423)) ([0d5f4e6](https://github.com/UXPLIMA/uxm-essentials/commit/0d5f4e6eeffcaf6dd70e4a0bb3bbd2684e932839))
* **menu:** give every module panel a tile on the management hub ([#270](https://github.com/UXPLIMA/uxm-essentials/issues/270)) ([27cf604](https://github.com/UXPLIMA/uxm-essentials/commit/27cf604273129e8379e908cb577f076a636db06e)), closes [#109](https://github.com/UXPLIMA/uxm-essentials/issues/109)
* **proxy:** bedrock forms and xuid through geyser on the proxy ([#422](https://github.com/UXPLIMA/uxm-essentials/issues/422)) ([9b47349](https://github.com/UXPLIMA/uxm-essentials/commit/9b473490ddef896a63302e280b2b2afe908d732b)), closes [#416](https://github.com/UXPLIMA/uxm-essentials/issues/416)


### Bug Fixes

* **command:** give back items a failed or abandoned sale took ([#114](https://github.com/UXPLIMA/uxm-essentials/issues/114)) ([e74e18d](https://github.com/UXPLIMA/uxm-essentials/commit/e74e18d42e95c77212c72dd33810cc8207d7cc15)), closes [#70](https://github.com/UXPLIMA/uxm-essentials/issues/70)
* **command:** make offline and override teleports match the catalog ([#209](https://github.com/UXPLIMA/uxm-essentials/issues/209)) ([ecd690f](https://github.com/UXPLIMA/uxm-essentials/commit/ecd690f315357c423f796702d9430d75da1e3654)), closes [#189](https://github.com/UXPLIMA/uxm-essentials/issues/189)
* **command:** report a window as opened only once it is showing ([#275](https://github.com/UXPLIMA/uxm-essentials/issues/275)) ([6bdf674](https://github.com/UXPLIMA/uxm-essentials/commit/6bdf6742c1d3aa8d029874bb2c1ffabcb34c08f7)), closes [#245](https://github.com/UXPLIMA/uxm-essentials/issues/245)
* **command:** validate custom command arguments used in command lines ([#409](https://github.com/UXPLIMA/uxm-essentials/issues/409)) ([0691e0c](https://github.com/UXPLIMA/uxm-essentials/commit/0691e0c4cf731514d3ea13cd0548002611026dad)), closes [#406](https://github.com/UXPLIMA/uxm-essentials/issues/406)
* **config:** comment every block and move player words to the catalog ([#183](https://github.com/UXPLIMA/uxm-essentials/issues/183)) ([7da629e](https://github.com/UXPLIMA/uxm-essentials/commit/7da629e2b1398d6259bc72f2c575518cc2c047e6)), closes [#98](https://github.com/UXPLIMA/uxm-essentials/issues/98)
* **config:** keep the redis password out of the network config text ([#246](https://github.com/UXPLIMA/uxm-essentials/issues/246)) ([514350a](https://github.com/UXPLIMA/uxm-essentials/commit/514350ad04f78f2a174b8db2197fe576b672ad12)), closes [#216](https://github.com/UXPLIMA/uxm-essentials/issues/216)
* **config:** let each module's own flag gate its console and op actions ([#413](https://github.com/UXPLIMA/uxm-essentials/issues/413)) ([655b591](https://github.com/UXPLIMA/uxm-essentials/commit/655b59125af356a18df96bb8b5356af1c4ff7fc0)), closes [#410](https://github.com/UXPLIMA/uxm-essentials/issues/410)
* **config:** read warp sign and effect settings from the module root ([#117](https://github.com/UXPLIMA/uxm-essentials/issues/117)) ([83dbf98](https://github.com/UXPLIMA/uxm-essentials/commit/83dbf98838558c6316798538533e5611af7ed333)), closes [#72](https://github.com/UXPLIMA/uxm-essentials/issues/72)
* **config:** ship import policy keys and drop the unused ladder ([#134](https://github.com/UXPLIMA/uxm-essentials/issues/134)) ([25b7a4b](https://github.com/UXPLIMA/uxm-essentials/commit/25b7a4b2ea626a0994a1de5f3edcfa4c57abbbff)), closes [#91](https://github.com/UXPLIMA/uxm-essentials/issues/91)
* **core:** accept Floodgate names as cross-server teleport targets (350) ([8974f2f](https://github.com/UXPLIMA/uxm-essentials/commit/8974f2fb0396816da72725bb64a10a756a8f5170)), closes [#315](https://github.com/UXPLIMA/uxm-essentials/issues/315)
* **core:** act on players on other servers in kick, freeze and security ([#391](https://github.com/UXPLIMA/uxm-essentials/issues/391)) ([8289890](https://github.com/UXPLIMA/uxm-essentials/commit/8289890faf4a7a9b9884e4ea5196456e777a49af)), closes [#329](https://github.com/UXPLIMA/uxm-essentials/issues/329)
* **core:** announce mail at join and across servers ([#368](https://github.com/UXPLIMA/uxm-essentials/issues/368)) ([6a04e03](https://github.com/UXPLIMA/uxm-essentials/commit/6a04e03461e612ce0b92c87f9941c4a315e23cef)), closes [#345](https://github.com/UXPLIMA/uxm-essentials/issues/345)
* **core:** answer online checks from a join and quit index ([#269](https://github.com/UXPLIMA/uxm-essentials/issues/269)) ([5cead84](https://github.com/UXPLIMA/uxm-essentials/commit/5cead8495d4c5672281ef05055facc6520ceedc4))
* **core:** apply the ignore list to /msg sent to offline players ([#353](https://github.com/UXPLIMA/uxm-essentials/issues/353)) ([f1ac524](https://github.com/UXPLIMA/uxm-essentials/commit/f1ac52495f63d94fa6afa466d199143117d1471d)), closes [#339](https://github.com/UXPLIMA/uxm-essentials/issues/339)
* **core:** apply warp world-blacklist to moves and imports ([#239](https://github.com/UXPLIMA/uxm-essentials/issues/239)) ([15e9254](https://github.com/UXPLIMA/uxm-essentials/commit/15e925427f22884f430b3f3a9ce4e2d7d1d39835)), closes [#235](https://github.com/UXPLIMA/uxm-essentials/issues/235)
* **core:** ask before opening a cross-server trade and report replies ([#137](https://github.com/UXPLIMA/uxm-essentials/issues/137)) ([c2bf893](https://github.com/UXPLIMA/uxm-essentials/commit/c2bf89378dd1ed03795fd58bee3f070e02575e50)), closes [#88](https://github.com/UXPLIMA/uxm-essentials/issues/88)
* **core:** ask the online index where presence is checked off the tick ([#292](https://github.com/UXPLIMA/uxm-essentials/issues/292)) ([02474d9](https://github.com/UXPLIMA/uxm-essentials/commit/02474d904b21cf268d42538be7390b1ffe1c2aa0)), closes [#279](https://github.com/UXPLIMA/uxm-essentials/issues/279)
* **core:** back off jail transfer retries that are turned away at once ([#252](https://github.com/UXPLIMA/uxm-essentials/issues/252)) ([d1431c4](https://github.com/UXPLIMA/uxm-essentials/commit/d1431c41e0948d447bff33f4264940be2a9a49a2)), closes [#219](https://github.com/UXPLIMA/uxm-essentials/issues/219)
* **core:** bind a teleport warmup to the session that began it ([#247](https://github.com/UXPLIMA/uxm-essentials/issues/247)) ([083b77f](https://github.com/UXPLIMA/uxm-essentials/commit/083b77fb8a7b268582415aa89cb30f175e631c27)), closes [#230](https://github.com/UXPLIMA/uxm-essentials/issues/230)
* **core:** cancel a warmup on quit and say why each warmup stops ([#225](https://github.com/UXPLIMA/uxm-essentials/issues/225)) ([588f15c](https://github.com/UXPLIMA/uxm-essentials/commit/588f15c5465c1c849d56e634bf40b867547998a2)), closes [#172](https://github.com/UXPLIMA/uxm-essentials/issues/172)
* **core:** cancel the earlier warmup when a new one starts ([#170](https://github.com/UXPLIMA/uxm-essentials/issues/170)) ([e53e670](https://github.com/UXPLIMA/uxm-essentials/commit/e53e670f7e4dc0c77c2430067ce64d7631755517)), closes [#166](https://github.com/UXPLIMA/uxm-essentials/issues/166)
* **core:** cap the vault refund at what the vault cost ([#169](https://github.com/UXPLIMA/uxm-essentials/issues/169)) ([ed40392](https://github.com/UXPLIMA/uxm-essentials/commit/ed403928da158e01579ae8f30b14db0205a5bc33)), closes [#164](https://github.com/UXPLIMA/uxm-essentials/issues/164)
* **core:** charge the rtp fee before the hop and refund a missed one ([#113](https://github.com/UXPLIMA/uxm-essentials/issues/113)) ([bdd702a](https://github.com/UXPLIMA/uxm-essentials/commit/bdd702a3a5275c3c6b8bacdf1fa9a7f4b95dc559)), closes [#71](https://github.com/UXPLIMA/uxm-essentials/issues/71)
* **core:** check a cross-server player warp's safety where it lands ([#202](https://github.com/UXPLIMA/uxm-essentials/issues/202)) ([674cfdf](https://github.com/UXPLIMA/uxm-essentials/commit/674cfdf2fe8a6ebb106ea02eaf706c5dccc54fae)), closes [#191](https://github.com/UXPLIMA/uxm-essentials/issues/191)
* **core:** check bans at async pre-login instead of on the tick ([#268](https://github.com/UXPLIMA/uxm-essentials/issues/268)) ([3f60fae](https://github.com/UXPLIMA/uxm-essentials/commit/3f60faecb32a15973cd694d4d668f4e674574c18)), closes [#237](https://github.com/UXPLIMA/uxm-essentials/issues/237)
* **core:** close a viewer's older vault window before reopening it ([#286](https://github.com/UXPLIMA/uxm-essentials/issues/286)) ([b6f9f2b](https://github.com/UXPLIMA/uxm-essentials/commit/b6f9f2b2c320165ca4738d9666fcc52ef00fe72b)), closes [#284](https://github.com/UXPLIMA/uxm-essentials/issues/284)
* **core:** close the loose ends of the per-server world claim ([#218](https://github.com/UXPLIMA/uxm-essentials/issues/218)) ([3e78b23](https://github.com/UXPLIMA/uxm-essentials/commit/3e78b231747677100932b9e8f2470795d3a2d6ce)), closes [#173](https://github.com/UXPLIMA/uxm-essentials/issues/173)
* **core:** confirm a remembered name against the store off the tick ([#399](https://github.com/UXPLIMA/uxm-essentials/issues/399)) ([4d83982](https://github.com/UXPLIMA/uxm-essentials/commit/4d83982fef3c252a0249b3658ae515a34a3acdc8)), closes [#395](https://github.com/UXPLIMA/uxm-essentials/issues/395)
* **core:** credit votes to the right Bedrock or offline-mode account ([#383](https://github.com/UXPLIMA/uxm-essentials/issues/383)) ([f0fe5d1](https://github.com/UXPLIMA/uxm-essentials/commit/f0fe5d14fd7e385ab6a747ead56d108578c605aa)), closes [#327](https://github.com/UXPLIMA/uxm-essentials/issues/327)
* **core:** declare seven checked permission nodes in the catalog ([#125](https://github.com/UXPLIMA/uxm-essentials/issues/125)) ([c94df33](https://github.com/UXPLIMA/uxm-essentials/commit/c94df33ef95e587c0e35cc80d877b3d4c7156731)), closes [#108](https://github.com/UXPLIMA/uxm-essentials/issues/108)
* **core:** draw player heads from stored textures, not names ([#363](https://github.com/UXPLIMA/uxm-essentials/issues/363)) ([7b66f6e](https://github.com/UXPLIMA/uxm-essentials/commit/7b66f6eedcaee043a6a0683dded5457789f04bba)), closes [#334](https://github.com/UXPLIMA/uxm-essentials/issues/334)
* **core:** dress bedrock players in their own skin first ([#361](https://github.com/UXPLIMA/uxm-essentials/issues/361)) ([09b35d7](https://github.com/UXPLIMA/uxm-essentials/commit/09b35d7559143c605889f37f5ed9893037457820)), closes [#333](https://github.com/UXPLIMA/uxm-essentials/issues/333)
* **core:** drop a late both-carrier vote party copy after a restart ([#264](https://github.com/UXPLIMA/uxm-essentials/issues/264)) ([ffa0fb9](https://github.com/UXPLIMA/uxm-essentials/commit/ffa0fb91c286de6301c7378e4ef2000705d94179)), closes [#201](https://github.com/UXPLIMA/uxm-essentials/issues/201)
* **core:** drop late copies of a remote vote announcement ([#397](https://github.com/UXPLIMA/uxm-essentials/issues/397)) ([40fb29f](https://github.com/UXPLIMA/uxm-essentials/commit/40fb29f38e594fdbac8bd7e7653ec7f30afae7eb)), closes [#392](https://github.com/UXPLIMA/uxm-essentials/issues/392)
* **core:** drop timed-out physical credits and sell only priced stacks ([#262](https://github.com/UXPLIMA/uxm-essentials/issues/262)) ([7ebb85b](https://github.com/UXPLIMA/uxm-essentials/commit/7ebb85b904dff8e79a516bee61ba90059325cb71)), closes [#124](https://github.com/UXPLIMA/uxm-essentials/issues/124)
* **core:** expire worlds delete confirms and apply create defaults ([#122](https://github.com/UXPLIMA/uxm-essentials/issues/122)) ([d3b91fd](https://github.com/UXPLIMA/uxm-essentials/commit/d3b91fdf7989845750e53de3cad0a26a6cda5d90)), closes [#73](https://github.com/UXPLIMA/uxm-essentials/issues/73)
* **core:** fail closed when a mute cannot be read ([#305](https://github.com/UXPLIMA/uxm-essentials/issues/305)) ([1719cad](https://github.com/UXPLIMA/uxm-essentials/commit/1719cada2a6acb7bb7bf07a3f14f0ebcd215669a)), closes [#303](https://github.com/UXPLIMA/uxm-essentials/issues/303)
* **core:** give Bedrock players a way to every click-only action ([#360](https://github.com/UXPLIMA/uxm-essentials/issues/360)) ([d4dc5f1](https://github.com/UXPLIMA/uxm-essentials/commit/d4dc5f15f2593a7f54c8c6c90aa9265ff9bca587)), closes [#331](https://github.com/UXPLIMA/uxm-essentials/issues/331)
* **core:** give homes and server warps the player warp limit rules ([#250](https://github.com/UXPLIMA/uxm-essentials/issues/250)) ([5b672dd](https://github.com/UXPLIMA/uxm-essentials/commit/5b672dd6c84f5a29e9dd68bbaa4a2d9aa9391bca)), closes [#228](https://github.com/UXPLIMA/uxm-essentials/issues/228)
* **core:** give unset family nodes their catalog default ([#199](https://github.com/UXPLIMA/uxm-essentials/issues/199)) ([b95b9ba](https://github.com/UXPLIMA/uxm-essentials/commit/b95b9ba04e1e8034516de007e70e80b6c8f873a0)), closes [#195](https://github.com/UXPLIMA/uxm-essentials/issues/195)
* **core:** guard mutes and ip bans like player bans ([#220](https://github.com/UXPLIMA/uxm-essentials/issues/220)) ([477eebd](https://github.com/UXPLIMA/uxm-essentials/commit/477eebdabdb3dd88ee84099140aa4bad0b8b41d4)), closes [#206](https://github.com/UXPLIMA/uxm-essentials/issues/206)
* **core:** hold a freeze on every server of the network ([#249](https://github.com/UXPLIMA/uxm-essentials/issues/249)) ([efb7b35](https://github.com/UXPLIMA/uxm-essentials/commit/efb7b35a5688a0f757b5cb26b5cb939e347f968d)), closes [#213](https://github.com/UXPLIMA/uxm-essentials/issues/213)
* **core:** honour the punishment exempt node for remote targets ([#354](https://github.com/UXPLIMA/uxm-essentials/issues/354)) ([e4e10a7](https://github.com/UXPLIMA/uxm-essentials/commit/e4e10a7c6bff430d0ca44d0861e2ed9675f24bb2)), closes [#341](https://github.com/UXPLIMA/uxm-essentials/issues/341)
* **core:** ignore vanish frames older than the newest one applied ([#254](https://github.com/UXPLIMA/uxm-essentials/issues/254)) ([a7c83ae](https://github.com/UXPLIMA/uxm-essentials/commit/a7c83ae2ec6636e4200f6e8dcefdedb375507026)), closes [#215](https://github.com/UXPLIMA/uxm-essentials/issues/215)
* **core:** keep a closing vault's items until its save lands ([#180](https://github.com/UXPLIMA/uxm-essentials/issues/180)) ([844ab95](https://github.com/UXPLIMA/uxm-essentials/commit/844ab95e82ba47a21f067c87826c498402c3aeea)), closes [#179](https://github.com/UXPLIMA/uxm-essentials/issues/179)
* **core:** keep a vault open fee only for a window that showed ([#283](https://github.com/UXPLIMA/uxm-essentials/issues/283)) ([0e10f75](https://github.com/UXPLIMA/uxm-essentials/commit/0e10f75d0ff6dc3e22e271088e2036e6000c0b6d)), closes [#277](https://github.com/UXPLIMA/uxm-essentials/issues/277)
* **core:** keep nicks across relogs and servers, refuse impersonation ([#394](https://github.com/UXPLIMA/uxm-essentials/issues/394)) ([5f4780f](https://github.com/UXPLIMA/uxm-essentials/commit/5f4780f1d455adc21b8b1b6a850b2974061de1cf)), closes [#340](https://github.com/UXPLIMA/uxm-essentials/issues/340)
* **core:** keep trade items for a player who left until they rejoin ([#265](https://github.com/UXPLIMA/uxm-essentials/issues/265)) ([99b24b2](https://github.com/UXPLIMA/uxm-essentials/commit/99b24b2d84581ab93ad3304bf73057ce59aae316)), closes [#115](https://github.com/UXPLIMA/uxm-essentials/issues/115)
* **core:** keep vanish durable and drop it from the network on quit ([#138](https://github.com/UXPLIMA/uxm-essentials/issues/138)) ([1fe7813](https://github.com/UXPLIMA/uxm-essentials/commit/1fe7813a085839ad27008dc2de91c8841dfc18d0)), closes [#85](https://github.com/UXPLIMA/uxm-essentials/issues/85)
* **core:** kick ip-banned players and pay vote parties network-wide ([#123](https://github.com/UXPLIMA/uxm-essentials/issues/123)) ([7ece186](https://github.com/UXPLIMA/uxm-essentials/commit/7ece1864824f8654925423fc61220efa04e44462)), closes [#75](https://github.com/UXPLIMA/uxm-essentials/issues/75)
* **core:** launch world entries and read borders on owning threads ([#272](https://github.com/UXPLIMA/uxm-essentials/issues/272)) ([c68feaa](https://github.com/UXPLIMA/uxm-essentials/commit/c68feaa18eb04489f1c410611e72c42711f6b40d))
* **core:** let /home admin tp and /tpblock reach offline players ([#378](https://github.com/UXPLIMA/uxm-essentials/issues/378)) ([47d655f](https://github.com/UXPLIMA/uxm-essentials/commit/47d655fdf29ea45865abf0c72b5c96c3b65d6219)), closes [#320](https://github.com/UXPLIMA/uxm-essentials/issues/320)
* **core:** let /setrank reach offline players, run rank actions by uuid ([#365](https://github.com/UXPLIMA/uxm-essentials/issues/365)) ([4f78b9f](https://github.com/UXPLIMA/uxm-essentials/commit/4f78b9f363661b76fbb461bd6a5ea4d2d20f389a)), closes [#317](https://github.com/UXPLIMA/uxm-essentials/issues/317)
* **core:** let a longer mute replace a running one ([#243](https://github.com/UXPLIMA/uxm-essentials/issues/243)) ([8780619](https://github.com/UXPLIMA/uxm-essentials/commit/8780619ebaaf24062d4002ff760c8128a0126710))
* **core:** let a new custom command warmup replace the earlier one ([#224](https://github.com/UXPLIMA/uxm-essentials/issues/224)) ([cbff6fd](https://github.com/UXPLIMA/uxm-essentials/commit/cbff6fd398119c2641d5251570fedb6944aaafa6)), closes [#171](https://github.com/UXPLIMA/uxm-essentials/issues/171)
* **core:** let a warn escalation extend a running mute or ban ([#185](https://github.com/UXPLIMA/uxm-essentials/issues/185)) ([a98a4d5](https://github.com/UXPLIMA/uxm-essentials/commit/a98a4d55be7cb1e150dd8968cc276662caaeba3d)), closes [#162](https://github.com/UXPLIMA/uxm-essentials/issues/162)
* **core:** let cooldown tiers set the wait ([#207](https://github.com/UXPLIMA/uxm-essentials/issues/207)) ([db3ab4a](https://github.com/UXPLIMA/uxm-essentials/commit/db3ab4a465d5d762d9c3efe3aa44c50e03932ded)), closes [#193](https://github.com/UXPLIMA/uxm-essentials/issues/193)
* **core:** lift old IP ban spellings and store imported bans canonical ([#244](https://github.com/UXPLIMA/uxm-essentials/issues/244)) ([bb890b9](https://github.com/UXPLIMA/uxm-essentials/commit/bb890b953b3f7b89d54a98e7fb255470fe93ad72)), closes [#132](https://github.com/UXPLIMA/uxm-essentials/issues/132)
* **core:** list every permission family the plugin checks ([#184](https://github.com/UXPLIMA/uxm-essentials/issues/184)) ([393f40d](https://github.com/UXPLIMA/uxm-essentials/commit/393f40d9ac33982ee68230edd03ef7be87c9fdb8)), closes [#175](https://github.com/UXPLIMA/uxm-essentials/issues/175)
* **core:** make eco giveall and resetall reach every account ([#306](https://github.com/UXPLIMA/uxm-essentials/issues/306)) ([4cbd146](https://github.com/UXPLIMA/uxm-essentials/commit/4cbd146e4f4fdefc5e627429917267dd15763600)), closes [#301](https://github.com/UXPLIMA/uxm-essentials/issues/301)
* **core:** make moderation commands do what staff expect ([#229](https://github.com/UXPLIMA/uxm-essentials/issues/229)) ([c0002b9](https://github.com/UXPLIMA/uxm-essentials/commit/c0002b985954eeff89f50298e4fa9763b4d84dda)), closes [#196](https://github.com/UXPLIMA/uxm-essentials/issues/196)
* **core:** make warp cooldowns, limits and password tries hold ([#227](https://github.com/UXPLIMA/uxm-essentials/issues/227)) ([6092c45](https://github.com/UXPLIMA/uxm-essentials/commit/6092c45c477bbd47af0450fce94538826b9cc4e6)), closes [#197](https://github.com/UXPLIMA/uxm-essentials/issues/197)
* **core:** match allowed operators by exact name on offline mode ([#357](https://github.com/UXPLIMA/uxm-essentials/issues/357)) ([699ba48](https://github.com/UXPLIMA/uxm-essentials/commit/699ba48e788a057dc97d831bb37ecec78f95886e)), closes [#311](https://github.com/UXPLIMA/uxm-essentials/issues/311)
* **core:** move moderation join and quit storage off the tick ([#240](https://github.com/UXPLIMA/uxm-essentials/issues/240)) ([a9e0ffa](https://github.com/UXPLIMA/uxm-essentials/commit/a9e0ffaac3a61a6c06d50230a50bcd7d212349db)), closes [#238](https://github.com/UXPLIMA/uxm-essentials/issues/238)
* **core:** never let a staff ban cut short the ban already running ([#204](https://github.com/UXPLIMA/uxm-essentials/issues/204)) ([eb6ea26](https://github.com/UXPLIMA/uxm-essentials/commit/eb6ea2691893e4ec77db93b45b85436ad623c2c2)), closes [#186](https://github.com/UXPLIMA/uxm-essentials/issues/186)
* **core:** pay a vote party once when it arrives over both transports ([#200](https://github.com/UXPLIMA/uxm-essentials/issues/200)) ([9849acd](https://github.com/UXPLIMA/uxm-essentials/commit/9849acde323a07e8fc81f214199e05803ddab776)), closes [#188](https://github.com/UXPLIMA/uxm-essentials/issues/188)
* **core:** pay player warp hops and owners in the charged currency ([#258](https://github.com/UXPLIMA/uxm-essentials/issues/258)) ([c03676c](https://github.com/UXPLIMA/uxm-essentials/commit/c03676cf63502453546c665f73044b302e8f02a5)), closes [#255](https://github.com/UXPLIMA/uxm-essentials/issues/255) [#157](https://github.com/UXPLIMA/uxm-essentials/issues/157)
* **core:** pay queued credits off the tick and requeue what fails ([#263](https://github.com/UXPLIMA/uxm-essentials/issues/263)) ([323371d](https://github.com/UXPLIMA/uxm-essentials/commit/323371d27fa22f95c84d382adb5a5698eb5ad24b))
* **core:** read a sanction target's immunity on the target's own thread ([#295](https://github.com/UXPLIMA/uxm-essentials/issues/295)) ([c295326](https://github.com/UXPLIMA/uxm-essentials/commit/c29532640966182586187d5bfcf644ec309b356c)), closes [#294](https://github.com/UXPLIMA/uxm-essentials/issues/294)
* **core:** read permissions and combat tags on the player's thread ([#271](https://github.com/UXPLIMA/uxm-essentials/issues/271)) ([77e4499](https://github.com/UXPLIMA/uxm-essentials/commit/77e44995304b2f99d21d26bc180ca212b22486f2))
* **core:** read player names from the database when the index misses ([#364](https://github.com/UXPLIMA/uxm-essentials/issues/364)) ([85be3ad](https://github.com/UXPLIMA/uxm-essentials/commit/85be3ad0406243ab9a5e11dff7dd9c76683653c3)), closes [#313](https://github.com/UXPLIMA/uxm-essentials/issues/313)
* **core:** read warn ladder reasons in each reader's language ([#274](https://github.com/UXPLIMA/uxm-essentials/issues/274)) ([8b1f79d](https://github.com/UXPLIMA/uxm-essentials/commit/8b1f79d6a82466aaefea4eb45a1d17a13bf0bdc5)), closes [#222](https://github.com/UXPLIMA/uxm-essentials/issues/222)
* **core:** read worlds and holograms on tick threads, refund cut warmups ([#291](https://github.com/UXPLIMA/uxm-essentials/issues/291)) ([f3c421c](https://github.com/UXPLIMA/uxm-essentials/commit/f3c421c4479694ef22987ca13782e34bfe78137e))
* **core:** record no server off the network and refund a racing hop ([#156](https://github.com/UXPLIMA/uxm-essentials/issues/156)) ([9d9866f](https://github.com/UXPLIMA/uxm-essentials/commit/9d9866f46e1439ad284c65173ffe7ad5b22132e6)), closes [#153](https://github.com/UXPLIMA/uxm-essentials/issues/153)
* **core:** refund a claimed hop the stopping server never moved ([#251](https://github.com/UXPLIMA/uxm-essentials/issues/251)) ([6d41d87](https://github.com/UXPLIMA/uxm-essentials/commit/6d41d8795ebcd5f8cb464fc41cfc41ea7a7cbcc5)), closes [#149](https://github.com/UXPLIMA/uxm-essentials/issues/149)
* **core:** refund a paid teleport that never lands ([#182](https://github.com/UXPLIMA/uxm-essentials/issues/182)) ([3e4c90e](https://github.com/UXPLIMA/uxm-essentials/commit/3e4c90eb38efd10e462416136cddb9d776947e67)), closes [#160](https://github.com/UXPLIMA/uxm-essentials/issues/160)
* **core:** refund an escrow an abort overtook and tell the inviter ([#276](https://github.com/UXPLIMA/uxm-essentials/issues/276)) ([2e6426f](https://github.com/UXPLIMA/uxm-essentials/commit/2e6426f15beee8fc8ad299964a7ed258bbaa0590)), closes [#144](https://github.com/UXPLIMA/uxm-essentials/issues/144)
* **core:** refund cross-server warps that never move the player ([#116](https://github.com/UXPLIMA/uxm-essentials/issues/116)) ([8e73e19](https://github.com/UXPLIMA/uxm-essentials/commit/8e73e19e9ead11f58fdda4a2a4c6529ea52f9bac)), closes [#68](https://github.com/UXPLIMA/uxm-essentials/issues/68)
* **core:** refund warp and home charges in the currency taken ([#253](https://github.com/UXPLIMA/uxm-essentials/issues/253)) ([abd8820](https://github.com/UXPLIMA/uxm-essentials/commit/abd8820eb8ae455147775cf0a5bd241d703af8d5)), closes [#157](https://github.com/UXPLIMA/uxm-essentials/issues/157)
* **core:** reload shared caches when a backend fills again ([#142](https://github.com/UXPLIMA/uxm-essentials/issues/142)) ([5ed2b88](https://github.com/UXPLIMA/uxm-essentials/commit/5ed2b88236c6600670db02e693b8f29931d9b15a)), closes [#87](https://github.com/UXPLIMA/uxm-essentials/issues/87)
* **core:** reload warps and hologram viewer lists off the tick on resync ([#257](https://github.com/UXPLIMA/uxm-essentials/issues/257)) ([f9220aa](https://github.com/UXPLIMA/uxm-essentials/commit/f9220aad9d7ffc638d208d34cca91bdd3b383cc1)), closes [#146](https://github.com/UXPLIMA/uxm-essentials/issues/146)
* **core:** reload warps and hologram viewer lists off the tick on resync ([#257](https://github.com/UXPLIMA/uxm-essentials/issues/257)) ([821a3df](https://github.com/UXPLIMA/uxm-essentials/commit/821a3df22f4e34a8d2770a4f2a9fb0204d021d5d)), closes [#146](https://github.com/UXPLIMA/uxm-essentials/issues/146)
* **core:** report a server name two backends share ([#121](https://github.com/UXPLIMA/uxm-essentials/issues/121)) ([6e10b25](https://github.com/UXPLIMA/uxm-essentials/commit/6e10b2507d487f6da4b9173fcba8af082e92cc5b)), closes [#74](https://github.com/UXPLIMA/uxm-essentials/issues/74)
* **core:** report and rank bus frames dropped from a full queue ([#256](https://github.com/UXPLIMA/uxm-essentials/issues/256)) ([743bf99](https://github.com/UXPLIMA/uxm-essentials/commit/743bf99e636f9fe351bbf75f48822b1544a7772c)), closes [#145](https://github.com/UXPLIMA/uxm-essentials/issues/145)
* **core:** reshuffle the random player warp order every round ([#304](https://github.com/UXPLIMA/uxm-essentials/issues/304)) ([7458525](https://github.com/UXPLIMA/uxm-essentials/commit/74585251ac525dce718f38da836258a17a5640c0)), closes [#300](https://github.com/UXPLIMA/uxm-essentials/issues/300)
* **core:** resolve guard members through PlayerLookup ([#386](https://github.com/UXPLIMA/uxm-essentials/issues/386)) ([0e0f06e](https://github.com/UXPLIMA/uxm-essentials/commit/0e0f06e98eccb2ee4751c24d7a637ac3298708e3)), closes [#325](https://github.com/UXPLIMA/uxm-essentials/issues/325)
* **core:** resolve offline players in playerstate and itemworld ([#382](https://github.com/UXPLIMA/uxm-essentials/issues/382)) ([16771ac](https://github.com/UXPLIMA/uxm-essentials/commit/16771acac07b1090f49bc6e998ee2776e506d530)), closes [#323](https://github.com/UXPLIMA/uxm-essentials/issues/323)
* **core:** resolve player arguments through PlayerLookup ([#371](https://github.com/UXPLIMA/uxm-essentials/issues/371)) ([813be52](https://github.com/UXPLIMA/uxm-essentials/commit/813be528d641705a7767554e0feea24f35f3900b)), closes [#316](https://github.com/UXPLIMA/uxm-essentials/issues/316)
* **core:** resolve remote players in holograms and bossbars ([#384](https://github.com/UXPLIMA/uxm-essentials/issues/384)) ([5d86b84](https://github.com/UXPLIMA/uxm-essentials/commit/5d86b84b05f261ec5953c89803bde351765acb88))
* **core:** route /eco and Vault name lookups through PlayerLookup ([#393](https://github.com/UXPLIMA/uxm-essentials/issues/393)) ([5a52e63](https://github.com/UXPLIMA/uxm-essentials/commit/5a52e6393f8b9b79bd8d68c1fa67c9b9cecf1687)), closes [#322](https://github.com/UXPLIMA/uxm-essentials/issues/322)
* **core:** run moderation storage work off the server thread ([#236](https://github.com/UXPLIMA/uxm-essentials/issues/236)) ([43a1b38](https://github.com/UXPLIMA/uxm-essentials/commit/43a1b380660e95c7e637988896448f9cdd3c5dea)), closes [#208](https://github.com/UXPLIMA/uxm-essentials/issues/208)
* **core:** save prestige off the tick and refund it when the save fails ([#261](https://github.com/UXPLIMA/uxm-essentials/issues/261)) ([f550c8e](https://github.com/UXPLIMA/uxm-essentials/commit/f550c8eb967ab0c9a85c85f8b5bffbda4239ff88)), closes [#120](https://github.com/UXPLIMA/uxm-essentials/issues/120)
* **core:** say a move is under way when a hop loses a race ([#217](https://github.com/UXPLIMA/uxm-essentials/issues/217)) ([075f78a](https://github.com/UXPLIMA/uxm-essentials/commit/075f78ada3050148bfc03de6b7a782aef3396fac)), closes [#159](https://github.com/UXPLIMA/uxm-essentials/issues/159)
* **core:** see players on other servers in tpoffline and warps ([#388](https://github.com/UXPLIMA/uxm-essentials/issues/388)) ([7b9fd38](https://github.com/UXPLIMA/uxm-essentials/commit/7b9fd383b1183c3ffbfdf20040cf58eab63f5dde)), closes [#326](https://github.com/UXPLIMA/uxm-essentials/issues/326)
* **core:** seed player names and accounts from imported plugins ([#372](https://github.com/UXPLIMA/uxm-essentials/issues/372)) ([81cba8c](https://github.com/UXPLIMA/uxm-essentials/commit/81cba8cff467ef66dd2e0121326d3356ff95754b)), closes [#335](https://github.com/UXPLIMA/uxm-essentials/issues/335)
* **core:** share pin and 2fa attempt limits across the network ([#358](https://github.com/UXPLIMA/uxm-essentials/issues/358)) ([a583dc2](https://github.com/UXPLIMA/uxm-essentials/commit/a583dc2de1100a7f1ba6601452a72f9c72ad64c9)), closes [#342](https://github.com/UXPLIMA/uxm-essentials/issues/342)
* **core:** share social spy, message toggles and reply partner ([#362](https://github.com/UXPLIMA/uxm-essentials/issues/362)) ([e044537](https://github.com/UXPLIMA/uxm-essentials/commit/e044537ce609605b3e514b1f5421ff0d66a30ddf)), closes [#343](https://github.com/UXPLIMA/uxm-essentials/issues/343)
* **core:** show the permanent ban screen for a permanent ban ([#212](https://github.com/UXPLIMA/uxm-essentials/issues/212)) ([7eab2bf](https://github.com/UXPLIMA/uxm-essentials/commit/7eab2bf0eae536399d075a7e2bbf6202d884019b)), closes [#190](https://github.com/UXPLIMA/uxm-essentials/issues/190)
* **core:** skip cancelled events in listeners or say why they must not ([#297](https://github.com/UXPLIMA/uxm-essentials/issues/297)) ([92622c0](https://github.com/UXPLIMA/uxm-essentials/commit/92622c0eb96f46b1aaa08b831815c4753ac05b63))
* **core:** start teleport cooldowns at every configured phase ([#231](https://github.com/UXPLIMA/uxm-essentials/issues/231)) ([53eb3a2](https://github.com/UXPLIMA/uxm-essentials/commit/53eb3a20d34e19d465a0d9b7174ecd238ccd4094)), closes [#161](https://github.com/UXPLIMA/uxm-essentials/issues/161)
* **core:** stop /banip reading Floodgate names as addresses (351) ([a0106ef](https://github.com/UXPLIMA/uxm-essentials/commit/a0106ef02f01e66cbe1c4d1c811f9826cd6dbe1a)), closes [#337](https://github.com/UXPLIMA/uxm-essentials/issues/337)
* **core:** stop a banned offline-mode player rejoining in another case ([#356](https://github.com/UXPLIMA/uxm-essentials/issues/356)) ([5dd2abd](https://github.com/UXPLIMA/uxm-essentials/commit/5dd2abd4971016605947bf0816db31285e0ecb06)), closes [#310](https://github.com/UXPLIMA/uxm-essentials/issues/310)
* **core:** stop a pending custom command warmup holding chain depth ([#248](https://github.com/UXPLIMA/uxm-essentials/issues/248)) ([ab60320](https://github.com/UXPLIMA/uxm-essentials/commit/ab60320e526a37a5372a178c61f333f81a1b34c0)), closes [#226](https://github.com/UXPLIMA/uxm-essentials/issues/226)
* **core:** stop join, quit and first-join firing on every server switch ([#374](https://github.com/UXPLIMA/uxm-essentials/issues/374)) ([d841732](https://github.com/UXPLIMA/uxm-essentials/commit/d841732519a9827d263c688b525acda7da1be7b3)), closes [#346](https://github.com/UXPLIMA/uxm-essentials/issues/346)
* **core:** sync skin changes across the network ([#359](https://github.com/UXPLIMA/uxm-essentials/issues/359)) ([36e6cd7](https://github.com/UXPLIMA/uxm-essentials/commit/36e6cd70738b41b834b6dfc7cc8a6412e37363ce)), closes [#332](https://github.com/UXPLIMA/uxm-essentials/issues/332)
* **core:** tell apart offline-mode accounts whose names differ in case ([#355](https://github.com/UXPLIMA/uxm-essentials/issues/355)) ([50a5c78](https://github.com/UXPLIMA/uxm-essentials/commit/50a5c789ee17e7495566c2bdee4a47d22c066c3c)), closes [#309](https://github.com/UXPLIMA/uxm-essentials/issues/309)
* **core:** tell the player when a money command cannot look a name up ([#412](https://github.com/UXPLIMA/uxm-essentials/issues/412)) ([5ca9ef3](https://github.com/UXPLIMA/uxm-essentials/commit/5ca9ef331770a687b730ba46ef125dad02d3ec23)), closes [#411](https://github.com/UXPLIMA/uxm-essentials/issues/411)
* **core:** tie online entries to their session, skip web name lookups ([#278](https://github.com/UXPLIMA/uxm-essentials/issues/278)) ([1f2ffb1](https://github.com/UXPLIMA/uxm-essentials/commit/1f2ffb150980c2df875d8abfe53c01069e8a7350))
* **core:** write warp categories off the tick, read staff targets safely ([#289](https://github.com/UXPLIMA/uxm-essentials/issues/289)) ([a141981](https://github.com/UXPLIMA/uxm-essentials/commit/a141981b6c5b4d6dd83ca2cfdfe4e5bcb9bb5e59))
* **docs:** correct documentation claims that disagree with the code ([#133](https://github.com/UXPLIMA/uxm-essentials/issues/133)) ([4e6f669](https://github.com/UXPLIMA/uxm-essentials/commit/4e6f6697d00c3c4c70731a3058d7899caa030ab4)), closes [#107](https://github.com/UXPLIMA/uxm-essentials/issues/107)
* **docs:** make catalog, config and readme lines match the code ([#234](https://github.com/UXPLIMA/uxm-essentials/issues/234)) ([d674edb](https://github.com/UXPLIMA/uxm-essentials/commit/d674edb41d1fe245848673fa6eefdf4765e989dd)), closes [#192](https://github.com/UXPLIMA/uxm-essentials/issues/192)
* **integration:** carry essentialsx one-time kit claims over ([#232](https://github.com/UXPLIMA/uxm-essentials/issues/232)) ([48bfa88](https://github.com/UXPLIMA/uxm-essentials/commit/48bfa880b9594d1abda7c528406578cf1ac69c95)), closes [#167](https://github.com/UXPLIMA/uxm-essentials/issues/167)
* **integration:** import through the running modules' repositories ([#143](https://github.com/UXPLIMA/uxm-essentials/issues/143)) ([2753eed](https://github.com/UXPLIMA/uxm-essentials/commit/2753eeda1a8bec2b01ca160b6eea4dd6e5a3f709)), closes [#90](https://github.com/UXPLIMA/uxm-essentials/issues/90)
* **integration:** keep imported essentialsx kits behind their node ([#214](https://github.com/UXPLIMA/uxm-essentials/issues/214)) ([86b9f58](https://github.com/UXPLIMA/uxm-essentials/commit/86b9f58e2098467fcd93137c717af463ecf0bdf7)), closes [#168](https://github.com/UXPLIMA/uxm-essentials/issues/168)
* **integration:** keep player values from our placeholders out of markup ([#415](https://github.com/UXPLIMA/uxm-essentials/issues/415)) ([28e80ca](https://github.com/UXPLIMA/uxm-essentials/commit/28e80ca5148154a80453fe4d5e68587c854d473a)), closes [#405](https://github.com/UXPLIMA/uxm-essentials/issues/405)
* **integration:** write imported essentialsx mail and real outcomes ([#126](https://github.com/UXPLIMA/uxm-essentials/issues/126)) ([f66c4fa](https://github.com/UXPLIMA/uxm-essentials/commit/f66c4faa7cc67d4aacc84c966051100a7ff9b23a)), closes [#89](https://github.com/UXPLIMA/uxm-essentials/issues/89)
* **menu:** escape values bound into menu lines once ([#417](https://github.com/UXPLIMA/uxm-essentials/issues/417)) ([ecd0755](https://github.com/UXPLIMA/uxm-essentials/commit/ecd0755290981c29f4ac19f74ee4581932290974)), closes [#404](https://github.com/UXPLIMA/uxm-essentials/issues/404)
* **menu:** keep typed values out of JSON and legacy menu broadcasts ([#421](https://github.com/UXPLIMA/uxm-essentials/issues/421)) ([949a501](https://github.com/UXPLIMA/uxm-essentials/commit/949a501a8d338a96a628dc077e2096876d966601)), closes [#418](https://github.com/UXPLIMA/uxm-essentials/issues/418)
* **menu:** let bedrock players set a custom loan payment ([#366](https://github.com/UXPLIMA/uxm-essentials/issues/366)) ([53bbe39](https://github.com/UXPLIMA/uxm-essentials/commit/53bbe398ccfcd48b84deb8f2d372b70793c8fc69)), closes [#331](https://github.com/UXPLIMA/uxm-essentials/issues/331)
* **menu:** let bedrock players type their pin instead of the keypad ([#352](https://github.com/UXPLIMA/uxm-essentials/issues/352)) ([b9ade5a](https://github.com/UXPLIMA/uxm-essentials/commit/b9ade5a21ee466a10eb217eb792b5a0fc3a29522)), closes [#330](https://github.com/UXPLIMA/uxm-essentials/issues/330)
* **menu:** queue warp category saves and report failed editor saves ([#281](https://github.com/UXPLIMA/uxm-essentials/issues/281)) ([402ea67](https://github.com/UXPLIMA/uxm-essentials/commit/402ea6721d261048f9208ec98942b1ed6ea67ef8)), closes [#178](https://github.com/UXPLIMA/uxm-essentials/issues/178)
* **menu:** run the player warp go-to button off the tick ([#260](https://github.com/UXPLIMA/uxm-essentials/issues/260)) ([77d7826](https://github.com/UXPLIMA/uxm-essentials/commit/77d7826f7531ff52bca3b779ce0e1d8126126a98)), closes [#140](https://github.com/UXPLIMA/uxm-essentials/issues/140)
* **menu:** save warp editor changes off the tick ([#273](https://github.com/UXPLIMA/uxm-essentials/issues/273)) ([5601d2b](https://github.com/UXPLIMA/uxm-essentials/commit/5601d2b3829deaa51b8dffdce494738a52f5189c)), closes [#178](https://github.com/UXPLIMA/uxm-essentials/issues/178)
* **menu:** send menu whispers to players on other servers ([#390](https://github.com/UXPLIMA/uxm-essentials/issues/390)) ([12a2f63](https://github.com/UXPLIMA/uxm-essentials/commit/12a2f636a70fb58aa789357b2a02f33122b77b65)), closes [#324](https://github.com/UXPLIMA/uxm-essentials/issues/324)
* **message:** escape player values in join, quit and death messages ([#403](https://github.com/UXPLIMA/uxm-essentials/issues/403)) ([224da60](https://github.com/UXPLIMA/uxm-essentials/commit/224da60e8677da6da0eb7cca62f7eae229ea797f)), closes [#401](https://github.com/UXPLIMA/uxm-essentials/issues/401)
* **message:** escape values filled into catalog lines by default ([#414](https://github.com/UXPLIMA/uxm-essentials/issues/414)) ([7ae586a](https://github.com/UXPLIMA/uxm-essentials/commit/7ae586ae0c6cd8085775e5e357c2ca641e73b414))
* **message:** keep placeholders typed by players from being expanded ([#400](https://github.com/UXPLIMA/uxm-essentials/issues/400)) ([e82a2b6](https://github.com/UXPLIMA/uxm-essentials/commit/e82a2b6b878d4fb73f8388caab122eac640dbff2)), closes [#398](https://github.com/UXPLIMA/uxm-essentials/issues/398)
* **message:** read the default for clients with no catalogue ([#290](https://github.com/UXPLIMA/uxm-essentials/issues/290)) ([11d8452](https://github.com/UXPLIMA/uxm-essentials/commit/11d84526b1bfce488b75355643382ec356c88bfd)), closes [#287](https://github.com/UXPLIMA/uxm-essentials/issues/287)
* **message:** render a player's lines in their own language ([#242](https://github.com/UXPLIMA/uxm-essentials/issues/242)) ([cf99d79](https://github.com/UXPLIMA/uxm-essentials/commit/cf99d79e614f01edcabb9ad37a9ccb4abc060338)), closes [#221](https://github.com/UXPLIMA/uxm-essentials/issues/221)
* **proxy:** doctor calls the bus connected only when the proxy answers ([#203](https://github.com/UXPLIMA/uxm-essentials/issues/203)) ([be338df](https://github.com/UXPLIMA/uxm-essentials/commit/be338df5d9718df32612d1b9cfa2f2bb31233e04)), closes [#198](https://github.com/UXPLIMA/uxm-essentials/issues/198)
* **proxy:** sign plugin channel bus frames with a shared secret ([#211](https://github.com/UXPLIMA/uxm-essentials/issues/211)) ([d222a31](https://github.com/UXPLIMA/uxm-essentials/commit/d222a3138ff431023abc6350a17dee79c2a43c94)), closes [#205](https://github.com/UXPLIMA/uxm-essentials/issues/205)
* **storage:** allocate economy ledger ids safely across servers ([#308](https://github.com/UXPLIMA/uxm-essentials/issues/308)) ([c4ea177](https://github.com/UXPLIMA/uxm-essentials/commit/c4ea17709190532486507f493bf4fac223f3ab2d)), closes [#307](https://github.com/UXPLIMA/uxm-essentials/issues/307)
* **storage:** save offline invsee edits safely under a per-player lock ([#223](https://github.com/UXPLIMA/uxm-essentials/issues/223)) ([5806fe2](https://github.com/UXPLIMA/uxm-essentials/commit/5806fe2acbc1dc96205302d09aef69b251418427)), closes [#210](https://github.com/UXPLIMA/uxm-essentials/issues/210)
* **storage:** share kit claims, cooldowns and daily reward on a network ([#136](https://github.com/UXPLIMA/uxm-essentials/issues/136)) ([584cd71](https://github.com/UXPLIMA/uxm-essentials/commit/584cd716354960c70b7edd9f317397aa1f9e73f6)), closes [#84](https://github.com/UXPLIMA/uxm-essentials/issues/84)
* **storage:** store item columns as LONGTEXT on MySQL and MariaDB ([#267](https://github.com/UXPLIMA/uxm-essentials/issues/267)) ([52afc7f](https://github.com/UXPLIMA/uxm-essentials/commit/52afc7f940da58032d8dcbe5bc416b8d03b72d81)), closes [#266](https://github.com/UXPLIMA/uxm-essentials/issues/266)
* **storage:** take a fresh mail id when another writer took it first ([#259](https://github.com/UXPLIMA/uxm-essentials/issues/259)) ([e9c7283](https://github.com/UXPLIMA/uxm-essentials/commit/e9c7283cafcacccec781fad36d2deba9d321ef16)), closes [#127](https://github.com/UXPLIMA/uxm-essentials/issues/127)
* **storage:** widen player name columns for floodgate and long names ([#389](https://github.com/UXPLIMA/uxm-essentials/issues/389)) ([a9f0f7f](https://github.com/UXPLIMA/uxm-essentials/commit/a9f0f7f5ab2c537dd29e12100d9dafe80fb03c7c)), closes [#312](https://github.com/UXPLIMA/uxm-essentials/issues/312)


### Performance Improvements

* **core:** keep offline name lookups off disk on the server thread ([#367](https://github.com/UXPLIMA/uxm-essentials/issues/367)) ([7d9848b](https://github.com/UXPLIMA/uxm-essentials/commit/7d9848b11807cda15af8bfaabfcb70a9ed8b9c46)), closes [#314](https://github.com/UXPLIMA/uxm-essentials/issues/314)
* **storage:** index sanction history by time for modstats ([#241](https://github.com/UXPLIMA/uxm-essentials/issues/241)) ([90db6ae](https://github.com/UXPLIMA/uxm-essentials/commit/90db6aee3edf8650431cee5f46b35ca733dee4de)), closes [#233](https://github.com/UXPLIMA/uxm-essentials/issues/233)

## [0.12.0](https://github.com/UXPLIMA/uxm-essentials/compare/v0.11.0...v0.12.0)

Upgrading: the `regions` module is gone and the new `guard` module protects regions itself. A server that used
`/regions` with WorldGuard imports its regions with `/guard import worldguard dry-run`, then `run`, and can remove
WorldGuard once they match. Grants of `uxmessentials.regions.*` move to the `uxmessentials.guard.*` nodes. The
`regions_*` placeholders keep their names.

### Added

- The guard module: region protection for Paper and Folia, on one server or a network. Regions of any shape with
  rules per action and role, behaviours, templates, menus, history and undo, inspect, backups, alerts and a
  WorldGuard importer. `/guard` (`/bolge`), `modules/guard/config.conf`.
- `security.allowed-operators`: only the listed players may be operators.
- One command-control entry can cover a whole plugin, and entries reach down to a command's arguments.

### Changed

- A player who types a command word they may not use reads "you don't have permission" in their language, and the
  word stays out of their command list and tab completion. Before, they read Minecraft's "Incorrect argument for
  command". A feature that is turned off answers with the new `command.unavailable` line.
- The kit cooldown refusal says how long is left (`kit.on-cooldown` takes `{time}`).
- Command-control edits apply on `/uxmess reload commandcontrol`.
- `/rtp` keeps out of guard regions under `respect-worldguard`, as it keeps out of WorldGuard regions.
- The Latin-script languages are written in small capitals.

### Fixed

- Command words may use the letters of every language.
- `/setspawn` is described as the current world's spawn.
- The example menu's title carries no colour.

### Removed

- The `regions` module, `/regions` and the `uxmessentials.regions.*` nodes.

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
