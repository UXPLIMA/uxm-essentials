# Permissions: gameplay modules

<!-- Generated from the code catalogues by ./gradlew :core:generateReference. Edit the catalogue, not this page. -->

Every node the plugin checks, by module, with the default it gets when no permission plugin grants it: `true` for every player, `op` for operators, `false` for nobody. A node with `<n>` takes a number; its description says whether the highest or the lowest granted number wins. A cooldown tier sets the wait: the shortest tier held wins, and the configured wait applies only to a player who holds none. Where one tier covers waits configured apart (each kit's own cooldown, a teleport verb's or warp's own cooldown), it only lowers them, so a tier meant as a perk never makes a shorter wait longer. A warmup tier only lowers the configured warmup. The staff and server modules and the plugin-wide nodes are in [permissions-server.md](permissions-server.md), and the item and world tools are in [permissions-items.md](permissions-items.md). `/uxmess permissions export` writes the same catalogue from the running build to `permissions.md` in the plugin folder.

Contents: [economy](#economy) · [homes](#homes) · [kits](#kits) · [playerstate](#playerstate) · [playerwarps](#playerwarps) · [poses](#poses) · [survival](#survival) · [teleport](#teleport) · [trade](#trade) · [vaults](#vaults) · [villagers](#villagers) · [warps](#warps) · [worlds](#worlds)

## economy

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.economy.admin` | Umbrella for eco-admin mutations (/eco give, take, set). | op |
| `uxmessentials.economy.admin.backup` | /eco backup and /eco export: write every wallet balance to a timestamped JSON backup or a CSV export under backups/economy/. | op |
| `uxmessentials.economy.admin.bulk` | /eco giveall, /eco giverandom, /eco resetall: change every player's balance, or those of the players online here. | op |
| `uxmessentials.economy.admin.give` | /eco give <player> <amount> [currency] only. | op |
| `uxmessentials.economy.admin.history` | /eco history [player]: open the transaction history of yourself or another player, online or not. | op |
| `uxmessentials.economy.admin.logs` | /eco logs: open the server-wide transaction log. | op |
| `uxmessentials.economy.admin.note` | /eco note give <player> <amount> [currency]: hand an online player a banknote without debiting anyone. | op |
| `uxmessentials.economy.admin.restore` | /eco restore <player> <backup>: put one player's wallet balances back from an /eco backup file. | op |
| `uxmessentials.economy.admin.set` | /eco set <player> <amount> [currency] and /eco reset only. | op |
| `uxmessentials.economy.admin.sweeps` | /eco sweeps and /eco sweeps cancel <id>: list the unfinished changes to every balance and stop one. | op |
| `uxmessentials.economy.admin.take` | /eco take <player> <amount> [currency] only. | op |
| `uxmessentials.economy.balance` | /balance [currency] to see your own balance. | true |
| `uxmessentials.economy.balance.others` | /balance <player> [currency] to view another player's balance. | op |
| `uxmessentials.economy.baltop` | /baltop [currency] [page] to view the top balances. | true |
| `uxmessentials.bypass.baltop` | Marks the holder as hidden from every /baltop leaderboard. | false |
| `uxmessentials.economy.bank` | /bank: open the bank panel to move money between your wallet and your bank balance. | true |
| `uxmessentials.economy.bank.create` | /bank create <name>: open a new shared bank account. Held by default alongside the bank node. | true |
| `uxmessentials.economy.bank.deposit` | /bank deposit: pay into a shared bank account. Held by default alongside the bank node. | true |
| `uxmessentials.economy.bank.withdraw` | /bank withdraw: take money out of a shared bank account; the capability worth withholding from junior members. | true |
| `uxmessentials.economy.bank.members` | /bank addmember and removemember: change who may use a shared bank account. | true |
| `uxmessentials.bypass.cost.command` | Skip the configured per-command economy charge (command-costs in economy.conf). | op |
| `uxmessentials.economy.currency.<currency>` | Use one currency that is configured to require a permission. | op |
| `uxmessentials.economy.deposit` | /deposit <amount> [currency]: move money from your wallet into your bank balance. | true |
| `uxmessentials.economy.exchange` | /exchange <amount> <from> <to>: convert between two currencies at the configured rate. | true |
| `uxmessentials.economy.loan` | /loan: take, review and repay a loan against the configured limit and interest. | false |
| `uxmessentials.economy.loan.take` | /loan take: borrow. Held by default alongside the loan node, so negating it leaves a borrower able to check and repay what they already owe but not to take more. | true |
| `uxmessentials.economy.pay` | /pay <player> <amount> [currency] and /payconfirm to transfer funds. | true |
| `uxmessentials.economy.pay.toggle` | /paytoggle to refuse all incoming /pay transfers. | true |
| `uxmessentials.economy.payall` | /payall <amount> [currency]: pay every online player from your own wallet. | op |
| `uxmessentials.economy.salary.amount.<amount>` | The periodic salary you are paid; the largest tier held wins. | false |
| `uxmessentials.economy.sell` | /sell [amount] to sell held items at their configured worth. | true |
| `uxmessentials.economy.setworth` | /setworth [item] <price>\|clear to set or clear an item's sell worth override. | op |
| `uxmessentials.bypass.tax` | Send a /pay without the configured transfer tax being deducted. | op |
| `uxmessentials.economy.wallet` | /wallet: open your own wallet panel listing every currency you hold. | true |
| `uxmessentials.economy.withdraw` | /withdraw <amount> [currency]: move money from your bank balance back into your wallet. | true |
| `uxmessentials.economy.worth` | /worth [item] to report an item's configured sell value. | true |
| `uxmessentials.module.economy` | Hot-reload / inspect the economy module (wallets, banks, currencies and the provider bridge). | op |

## homes

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.home.admin` | /home admin <player> to list, teleport to, set, delete, clear and inspect another player's homes. | op |
| `uxmessentials.bypass.cost.home` | Skip the per-action economy cost for home create, relocate, and teleport actions. | op |
| `uxmessentials.bypass.safety.home` | Skip the unsafe-destination confirm when teleporting to a home via the GUI, and land at an unsafe home on another server. | op |
| `uxmessentials.home.icon` | Pick a custom GUI icon for one of your homes from the grid. | true |
| `uxmessentials.home.invite` | /home invite and /home uninvite to grant or revoke another player's access to one of your homes. | true |
| `uxmessentials.home.limit.<n>` | How many homes you may keep; the largest tier held wins. | true |
| `uxmessentials.home.limit.<world>.<n>` | How many homes you may keep in one world, counting only your homes there; the largest tier held wins. The limit across every world still applies. | true |
| `uxmessentials.home.setserver` | /home setserver <from> <to> to move every home recorded on one server to another without moving any. | op |
| `uxmessentials.home.use` | /home to open and manage your slot-based home grid. | true |
| `uxmessentials.home.visit` | /home visit <player> [slot] to teleport to another player's public home or one you were invited to. | true |
| `uxmessentials.module.homes` | Hot-reload / inspect the homes module (per-player homes and the slot grid). | op |

## kits

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.kit.<kit>` | Claim a kit whose file sets permission = true. | op |
| `uxmessentials.kit.cooldown.<group>` | Use the wait a kit's permission-cooldowns block sets for <group>; the shortest wait held wins. A group named by a number is skipped with a warning, since that name is a kit cooldown tier. | false |
| `uxmessentials.kit.cooldown.<seconds>` | A cap on the wait before a kit can be claimed again, in seconds; the shortest tier held wins and 0 removes it. It lowers each kit's own wait but never lengthens it. | true |
| `uxmessentials.bypass.cooldown.kit` | Skip kit cooldowns and re-claim one-time kits. | op |
| `uxmessentials.kit.edit` | /kit create, /kit del, /kit editor to define, remove and edit kit contents. | op |
| `uxmessentials.kit.others` | /kit <name> <player> to give a kit to another player. | op |
| `uxmessentials.kit.preview` | /kit show <name> to preview a kit's contents without claiming it. | true |
| `uxmessentials.kit.reset` | /kit reset <player> [kit] to clear a player's claim/cooldown stamps. | op |
| `uxmessentials.kit.use` | /kit <name> to claim a kit and /kit list to list the kits you may claim. | true |
| `uxmessentials.module.kits` | Hot-reload / inspect the kits module (kit definitions, cooldowns and claims). | op |
| `uxmessentials.oversizedstacks` | Receive kit items in stacks larger than the material normally allows. | op |

## playerstate

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.air.use` | /air <seconds> to set a player's remaining air. | op |
| `uxmessentials.burn.use` | /burn <seconds> to set a player on fire. | op |
| `uxmessentials.exp.use` | /exp (/xp) get\|set\|give\|take\|reset to read or change experience. | op |
| `uxmessentials.extinguish.use` | /ext (/extinguish) [player] to put out a burning player. | op |
| `uxmessentials.feed.use` | /feed [player] to restore hunger. | op |
| `uxmessentials.fly.use` | /fly [player] to toggle flight. | op |
| `uxmessentials.foodlevel.use` | /foodlevel <amount> [player] to set a player's food level. | op |
| `uxmessentials.gamemode.use` | /gamemode <mode> [player] and the /gmc /gms /gma /gmsp aliases. | op |
| `uxmessentials.glow.color` | /glow <color> to pick the colour of the outline. Without it, /glow still toggles the plain one. | op |
| `uxmessentials.glow.use` | /glow [color] [player] to toggle a glowing outline. | op |
| `uxmessentials.god.use` | /god [player] to toggle damage immunity. | op |
| `uxmessentials.heal.use` | /heal [player] to restore health. | op |
| `uxmessentials.health.use` | /health <amount> [player] to set a player's health. | op |
| `uxmessentials.ice.use` | /ice [seconds] [player] to freeze a player (inverse of /burn). | op |
| `uxmessentials.nightvision.use` | /nightvision (/nv) [player] to toggle a night-vision effect. | op |
| `uxmessentials.playerstate.fly.allworlds` | Keep flight in worlds where flying is switched off. | op |
| `uxmessentials.ptime.use` | /ptime <value\|reset> to set a per-player client-side time. | op |
| `uxmessentials.pweather.use` | /pweather <clear\|rain\|reset> to set a per-player client-side weather. | op |
| `uxmessentials.rest.use` | /rest to reset a player's time-since-rest so phantoms stop. | op |
| `uxmessentials.speed.use` | /speed, /walkspeed and /flyspeed to set walk/fly speed. | op |
| `uxmessentials.suicide.use` | /suicide to kill yourself. | true |
| `uxmessentials.biome.use` | /biome to show the biome you are standing in. | true |
| `uxmessentials.depth.use` | /depth to show your height relative to sea level. | true |
| `uxmessentials.dimension.use` | /dimension to show the dimension you are standing in. | true |
| `uxmessentials.endersee.use` | /endersee [player] (and the endersee entry on the /uxmess gui hub) to view a player's ender chest. | op |
| `uxmessentials.getpos.use` | /getpos (/coords /whereami) to show a player's coordinates. | op |
| `uxmessentials.invsee.modify` | Edit a player's inventory through the /invsee menu (without this it is view-only). | op |
| `uxmessentials.invsee.use` | /invsee [player] (and the invsee entry on the /uxmess gui hub) to view a player's inventory. | op |
| `uxmessentials.near.use` | /near [radius] to list nearby players. | op |
| `uxmessentials.ping.use` | /ping to show a player's round-trip latency. | true |
| `uxmessentials.playtime.import` | /playtime import <player>\|all to bring the server's vanilla playtime into the tracked total (naming another player also needs uxmessentials.playerstate.others). | op |
| `uxmessentials.playtime.reset` | /playtime reset [player] to wipe a player's tracked playtime (resetting others also needs uxmessentials.playerstate.others). | op |
| `uxmessentials.playtime.set` | /playtime set\|add\|take <player> <time> to correct a player's all-time playtime (naming another player also needs uxmessentials.playerstate.others). | op |
| `uxmessentials.playtime.use` | /playtime [player] (and your own panel from the /uxmess gui hub) to show a player's playtime breakdown (active/afk, today/week/month/all-time). | op |
| `uxmessentials.seed.use` | /seed to show the seed of the world you are standing in. | true |
| `uxmessentials.bypass.blocked-commands.world` | Run commands a world blocks through its per-world command list. | op |
| `uxmessentials.world.use` | /world to show the world you are standing in, and /worlds to reach the world manager. | true |
| `uxmessentials.clearinventory.confirmtoggle` | /clearinventoryconfirmtoggle (/citoggle) to require a confirmation before /clearinventory clears your own inventory. | true |
| `uxmessentials.clearinventory.use` | /clearinventory (/ci /clear) [player] to empty a player's inventory. | op |
| `uxmessentials.compass.use` | /compass to show the direction you are facing. | true |
| `uxmessentials.module.playerstate` | Hot-reload / inspect the playerstate module (flight, god mode, speed, health and the rest). | op |
| `uxmessentials.air.others` | /air on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.burn.others` | /burn on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.clearinventory.others` | /clearinventory on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.endersee.others` | /endersee to open another player ender chest. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.exp.others` | /exp on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.extinguish.others` | /extinguish on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.feed.others` | /feed on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.fly.others` | /fly on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.foodlevel.others` | /foodlevel on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.gamemode.others` | /gamemode on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.getpos.others` | /getpos on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.glow.others` | /glow on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.god.others` | /god on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.heal.others` | /heal on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.health.others` | /health on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.ice.others` | /ice on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.invsee.others` | /invsee to open another player inventory. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.nightvision.others` | /nightvision on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.ping.others` | /ping on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.playtime.others` | /playtime on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.rest.others` | /rest on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.speed.others` | /speed on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others. | op |
| `uxmessentials.playerstate.others` | Use any playerstate command with a [player] target other than yourself. | op |

## playerwarps

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.pwarp.ban` | /pwarp ban\|unban <name> <player> to bar a player from one of your warps or lift that bar. | true |
| `uxmessentials.bypass.ban.pwarp` | Enter a player warp you are banned from (skips the ban check on /pwarp). | op |
| `uxmessentials.bypass.password.pwarp` | Enter a password-protected player warp without the password (skips the check on /pwarp). | op |
| `uxmessentials.bypass.whitelist.pwarp` | Enter a whitelist-only player warp without being on the whitelist (skips the check on /pwarp). | op |
| `uxmessentials.pwarp.category` | /pwarp category <name> [categoryId] to file a player warp under a browse category. | true |
| `uxmessentials.pwarp.delete` | /pwarp del <name> to remove one of your player warps. | true |
| `uxmessentials.pwarp.description` | /pwarp description <name> [text] to set or clear a player warp's description. | true |
| `uxmessentials.pwarp.displayname` | /pwarp displayname <name> [text] to set or clear a player warp's display name. | true |
| `uxmessentials.pwarp.icon` | /pwarp icon <name> [icon] to set or clear a player warp's browse icon. | true |
| `uxmessentials.pwarp.limit.<n>` | How many player warps you may own across every world; the largest tier held wins. | true |
| `uxmessentials.pwarp.limit.<world>.<n>` | How many player warps you may own in one world, counting only your warps there; the largest tier held wins. The limit across every world still applies. | true |
| `uxmessentials.pwarp.members` | /pwarp members <name> add\|remove <player> to grant or revoke a co-owner or manager on one of your warps. | true |
| `uxmessentials.pwarp.move` | /pwarp move <name> to re-anchor one of your player warps at your location. | true |
| `uxmessentials.pwarp.password` | /pwarp password <name> <password>\|clear to set or clear a player warp's password. | true |
| `uxmessentials.pwarp.price` | /pwarp price <name> <amount> [currency] to set a player warp's entry price. | true |
| `uxmessentials.pwarp.public` | /pwarp visibility public\|private <name> to toggle a player warp's visibility. | true |
| `uxmessentials.pwarp.rename` | /pwarp rename <name> <newName> to rename one of your player warps. | true |
| `uxmessentials.pwarp.set` | /setpwarp <name> to create or move a player-owned warp at your location. | true |
| `uxmessentials.pwarp.sponsor` | /pwarp sponsor <name> [days] to buy a paid, time-limited pinned browse slot for one of your warps. | true |
| `uxmessentials.pwarp.transfer` | /pwarp transfer <name> <player> to hand ownership of one of your warps to another player. | true |
| `uxmessentials.pwarp.whitelist` | /pwarp whitelist <name> add\|remove <player> to manage a whitelist-access warp's guest list. | true |
| `uxmessentials.pwarp.withdraw` | /pwarp withdraw <name> to pay one of your warps' accrued earnings out to its owner. | true |
| `uxmessentials.module.playerwarps` | Hot-reload / inspect the playerwarps module (player-owned warps behind /pwarp). | op |
| `uxmessentials.pwarp.access` | /pwarp access <name> <PUBLIC\|PASSWORD\|WHITELIST\|PRIVATE> to set a player warp's access axis. | true |
| `uxmessentials.pwarp.admin` | /pwarp admin restore\|purge\|setowner\|reload to manage any player's warp by its id. | op |
| `uxmessentials.bypass.cost.pwarp` | Use a priced player warp without paying its entry cost (skips the charge on /pwarp). | op |
| `uxmessentials.bypass.safety.pwarp` | Use a player warp whose destination is unsafe (skips the safe-landing check on /pwarp). | op |
| `uxmessentials.pwarp.favourite` | /pwarp favourite\|unfavourite <name> to star or un-star a player warp. | true |
| `uxmessentials.pwarp.edit` | /pwarp edit <name> to open one warp's property editor, the click-driven form of the typed verbs. Held by default alongside the use node. | true |
| `uxmessentials.pwarp.gui` | Manage every player's warps in the /pwarp GUI (a player without it edits only their own). | op |
| `uxmessentials.pwarp.info` | /pwarp info <name> to show a player warp's owner, access, price, visits, and rating. | true |
| `uxmessentials.pwarp.list` | /pwarps [player] to list your warps or a player's public warps. | true |
| `uxmessentials.pwarp.rate` | /pwarp rate <name> <1-5> to award a player warp a star rating. | true |
| `uxmessentials.pwarp.use` | /pwarp <name> [password] to teleport to a player warp you may enter; a password warp takes its password. | true |

## poses

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.bellyflop.use` | /bellyflop: flop onto your front where you stand. | true |
| `uxmessentials.crawl.use` | /crawl: crawl through a one-block gap; run again to stand up. | true |
| `uxmessentials.lay.use` | /lay: lie down on your back where you stand. | true |
| `uxmessentials.module.poses` | Hot-reload / inspect the poses module (built-in GSit-parity sitting and posing). | op |
| `uxmessentials.playersit.use` | Right-click another player to sit on them (the stacking pose), when player-sit is enabled. | true |
| `uxmessentials.poses.gui` | /poses (or /poses gui): open your personal poses settings & status panel. | true |
| `uxmessentials.poses.toggle` | /poses toggle: allow or refuse other players sitting on you. | true |
| `uxmessentials.poses.reveal` | /poses reveal <player>: give back a body a pose left unseen. | op |
| `uxmessentials.poses.cooldown.<seconds>` | The wait between starting poses, in seconds; a held tier sets the wait, the shortest held wins and 0 removes it; with none held there is no wait. | true |
| `uxmessentials.sit.use` | /sit and right-click-to-sit: sit down where you stand or on a sittable block. | true |
| `uxmessentials.spin.use` | /spin: sit and spin in place. | true |

## survival

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.survival` | Hot-reload / inspect the survival module (opt-in gameplay mechanics). | op |
| `uxmessentials.survival.autopickup` | Auto-pickup acts for you: mined drops go straight to your inventory. | true |
| `uxmessentials.survival.autopickup.toggle` | /autopickup: switch your personal auto-pickup on or off. | true |
| `uxmessentials.survival.autosell` | Auto-sell acts for you: priced drops are sold for coin as you mine. | true |
| `uxmessentials.survival.autosell.toggle` | /autosell: switch your personal auto-sell on or off. | true |
| `uxmessentials.survival.autosmelt` | Auto-smelt acts for you: ores are smelted as you mine them. | true |
| `uxmessentials.survival.autosmelt.toggle` | /autosmelt: switch your personal auto-smelt on or off. | true |
| `uxmessentials.survival.autotool` | Auto-tool acts for you: the best tool swaps to hand as you mine. | true |
| `uxmessentials.survival.autotool.toggle` | /autotool: switch your personal auto-tool on or off. | true |
| `uxmessentials.survival.farmassist` | Right-click a mature crop to harvest and replant it, spending one seed. | true |
| `uxmessentials.survival.farmprotect` | Farmland protection acts for you: you will not trample your crops. | true |
| `uxmessentials.survival.farmprotect.toggle` | /farmprotect: switch your personal farmland protection on or off. | true |
| `uxmessentials.survival.gui` | /survival: open your personal survival mechanics settings panel. | true |
| `uxmessentials.survival.treefeller` | Tree-feller acts for you: break one log to fell the whole trunk. | true |
| `uxmessentials.survival.treefeller.toggle` | /treefeller: switch your personal tree-feller on or off. | true |
| `uxmessentials.survival.veinminer` | Veinminer acts for you: break one block to mine the connected vein. | true |
| `uxmessentials.survival.veinminer.toggle` | /veinminer: switch your personal veinminer on or off. | true |

## teleport

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.tpa.all` | /tpaall to request every online player to teleport to you. | op |
| `uxmessentials.tpa.auto` | /tpauto to auto-accept incoming teleport requests. | true |
| `uxmessentials.tpa.block` | /tpblock / /tpunblock to block a player's requests. | true |
| `uxmessentials.tpa.cancel` | /tpcancel / /tpacancel to withdraw your outgoing request. | true |
| `uxmessentials.tpa.toggle` | /tptoggle to refuse incoming teleport requests and staff /tp and /tphere; /tpo and /tpohere still pass. | true |
| `uxmessentials.tpa.use` | /tpa, /tpaccept, /tpdeny to request and resolve a teleport. | true |
| `uxmessentials.tpahere.use` | /tpahere to ask a player to come to you. | true |
| `uxmessentials.tpsettings.use` | /tpsettings opens your personal teleport settings panel. | true |
| `uxmessentials.back.ondeath` | Allow /back and /deathback to return to a death location. | true |
| `uxmessentials.back.use` | /back to return to your last captured location; /deathback (alias /dback) to return to your last death location. | true |
| `uxmessentials.module.teleport` | Hot-reload / inspect the teleport module (/tp, /tpa, /back, /spawn and /rtp). | op |
| `uxmessentials.rtp.biome` | /rtp biome <biome> to random teleport into a specific biome. | true |
| `uxmessentials.rtp.gui` | /rtp gui to open the random-teleport world picker. | true |
| `uxmessentials.rtp.others` | /rtp <player> to force another online player to random teleport (staff). | op |
| `uxmessentials.rtp.cooldown.<seconds>` | The wait between random teleports, in seconds; a held tier sets the wait, the shortest held wins and 0 removes it; with none held the configured wait applies. | true |
| `uxmessentials.rtp.radius.<blocks>` | How far from the world centre /rtp biome may search; the largest tier held wins. It only narrows rtp.max-radius, never widens it. | true |
| `uxmessentials.rtp.use` | /rtp random teleport from the pre-warmed safe-location queue. | true |
| `uxmessentials.bypass.join-spawn` | Exempt the player from automatic first-join/every-join spawn movement. | false |
| `uxmessentials.spawn.named` | /spawn <name> to teleport to a named spawn. | true |
| `uxmessentials.spawn.set` | /setspawn, /setmainspawn, /removespawn and /mirrorspawn to define and manage spawns. | op |
| `uxmessentials.spawn.use` | /spawn to teleport to the resolved server spawn. | true |
| `uxmessentials.teleport.gui` | Show the teleport settings panel on the /uxmess gui hub. | op |
| `uxmessentials.teleport.settpr` | /settpr <minRange> <maxRange> to set the /rtp search zone at runtime. | op |
| `uxmessentials.tp.all` | /tpall to pull every online player to you. | op |
| `uxmessentials.tp.offline` | /tpoffline / /tpofflinehere to a player's logout location. | op |
| `uxmessentials.tp.others` | /tpo and /tpohere to teleport overriding no-tp flags. | op |
| `uxmessentials.tp.position` | /tppos to teleport to raw coordinates. | op |
| `uxmessentials.tp.use` | /tp, /tphere, /goto, /bring and /tprandomplayer (/tprp) direct staff teleport. | op |
| `uxmessentials.tp.vertical` | /top, /bottom, /jump, /up, /down, /ascend, /descend, /thru vertical teleports. | op |
| `uxmessentials.tp.cooldown.<seconds>` | The wait between teleports, in seconds; a held tier sets the wait, the shortest held wins and 0 removes it; with none held the configured wait applies. A verb or warp with its own cooldown keeps it as a ceiling the tier can only lower. | true |
| `uxmessentials.tp.warmup.<seconds>` | The stand-still countdown before a teleport runs, in seconds; the shortest tier held wins and 0 removes it. | true |
| `uxmessentials.bypass.warmup.tp` | Start teleports with no warmup, immune to move-cancel. | op |

## trade

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.trade` | Hot-reload / inspect the trade module. | op |
| `uxmessentials.trade.use` | /trade (and the trade entry on the /uxmess gui hub): request a trade with another player and accept or deny requests. | true |

## vaults

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.vaults` | Hot-reload / inspect the vaults module (DB-persisted player vault storage). | op |
| `uxmessentials.vault.admin.delete` | /vault delete <player> <n>: delete another player's vault (audit-logged, no refund). | op |
| `uxmessentials.vault.amount.<n>` | How many vaults you may open; the largest tier held wins. | true |
| `uxmessentials.bypass.blacklist.vault` | Store any item in a vault, ignoring the blacklist-materials list (items are not returned). | op |
| `uxmessentials.bypass.cost.vault` | Bypass every vault economy fee (create/open); no refund is paid on delete. | false |
| `uxmessentials.vault.icon` | /vault icon <n> [material]: set or clear the icon of your own vault (held item if omitted). | true |
| `uxmessentials.vault.others` | /vault <player> [n]: open and audit another player's vault, offline too (audit-logged). | op |
| `uxmessentials.vault.rename` | /vault rename <n> [name]: set or clear the display name of your own vault. | true |
| `uxmessentials.vault.size.<rows>` | How many rows each of your vaults holds; the largest tier held wins. | true |
| `uxmessentials.vault.use` | /vault to open your default vault (or list them), /vault <n> to open the Nth, and /vault delete <n> to delete your own. | true |

## villagers

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.villagers` | Hot-reload / inspect the villagers module (villager trade management). | op |
| `uxmessentials.villagers.bucket` | Sneak-right-click a villager to pick it up into a captured-villager item, and place it back later. | op |
| `uxmessentials.villagers.follow` | /villager follow: toggle whether the villager you are looking at pathfinds after you. | op |
| `uxmessentials.villagers.leash` | Right-click a villager with a lead to leash it, when leashing is enabled. | op |
| `uxmessentials.villagers.manager` | /villager manager (and the villagers entry on the /uxmess gui hub): open and edit the trades of the villager you are looking at. | op |
| `uxmessentials.villagers.protect` | /villager protect: toggle whether the villager you are looking at is protected from death and despawn. | op |
| `uxmessentials.villagers.trade` | Open a villager's trade window directly on right-click, when click-to-trade is enabled. | op |
| `uxmessentials.villagers.use` | /villager: the root command's base node; its subcommands each gate further on their own node. | op |

## warps

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.warps` | Hot-reload / inspect the warps module (server warps and their access rules). | op |
| `uxmessentials.bypass.lock.warp` | Use a locked warp regardless of its lock state. | op |
| `uxmessentials.bypass.password.warp` | Use a password-protected warp without entering its password. | op |
| `uxmessentials.bypass.safety.warp` | Use a warp whose destination fails the safety check. | op |
| `uxmessentials.warp.delete` | /warp del <name> to remove a warp. | op |
| `uxmessentials.warp.edit` | /warp editor [name] to open the warp manager, or one warp's editor (icon, category, lock, password, welcome messages, sounds, particles, warmup and cooldown). | op |
| `uxmessentials.warp.info` | /warp info <name> to show a warp's owner, creation time, cost and required permission. | true |
| `uxmessentials.warp.list` | /warp list to list the warps you may use. | true |
| `uxmessentials.warp.lock` | /warp lock <name> to lock or unlock a warp against use. | op |
| `uxmessentials.warp.move` | /warp move <name> to move an existing warp to your current location, or to a given one with at. | op |
| `uxmessentials.warp.others` | /warp <name> <player> to send another player to a warp. | op |
| `uxmessentials.warp.password` | /warp password <name> [password] to set a warp's access password, or clear it when none is given. | op |
| `uxmessentials.warp.set` | /warp create <name> to create a server-wide warp, or move one that exists, at your location or a given one. | op |
| `uxmessentials.warp.setserver` | /warp setserver <name\|all> [server] to record which server a warp lives on without moving it. | op |
| `uxmessentials.warp.sign.create` | Create a [warp] sign that teleports players to a warp on click. | op |
| `uxmessentials.warp.sign.use` | Use a [warp] sign to teleport to its warp. | true |
| `uxmessentials.warp.use` | /warp <name> to teleport to a server warp. | true |
| `uxmessentials.warp.use.<warp>` | Use one server warp; asked for every warp, before any permission the warp itself requires. | true |

## worlds

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.module.worlds` | Hot-reload / inspect the worlds module (world creation, properties and access). | op |
| `uxmessentials.world.<world>.enter` | Enter one world that is configured as restricted. | op |
| `uxmessentials.bypass.access.world` | Enter a restricted world without holding that world's own entry node. | op |
| `uxmessentials.world.backup` | /worlds backup <name>: snapshot a world's folder. | op |
| `uxmessentials.bypass.safety.world` | Land on the spawn of a world on another server even when the safe-landing check refuses it. | op |
| `uxmessentials.world.claim` | /worlds claim <name>: take a world no server has claimed onto this server. | op |
| `uxmessentials.world.create` | /worlds create <name>: generate and register a new world. | op |
| `uxmessentials.world.delete` | /worlds delete <name>: unregister a world and delete its folder from disk. | op |
| `uxmessentials.bypass.gamemode.world` | Keep your own game mode in a world that forces one on entry. | op |
| `uxmessentials.world.gamerule` | /worlds gamerule <name> <rule> <value>: change one gamerule on a world. | op |
| `uxmessentials.world.gui` | /worlds gui: open the world management GUI. | op |
| `uxmessentials.world.import` | /worlds import <folder>: adopt an existing world folder into the registry. | op |
| `uxmessentials.world.info` | /worlds info <name>: read one world's generator, properties, gamerules and spawn. | op |
| `uxmessentials.world.list` | /worlds list: list every registered world with its load state and player count. | op |
| `uxmessentials.world.load` | /worlds load <name>: load a registered world that is currently unloaded. | op |
| `uxmessentials.world.pregen` | /worlds pregen <name> <radius>: pre-generate a world's chunks in the background. | op |
| `uxmessentials.world.restore` | /worlds restore <name> <backup>: restore a world from one of its snapshots. | op |
| `uxmessentials.world.set` | /worlds set <name> <property> <value>: change a world property such as difficulty or PvP. | op |
| `uxmessentials.world.setserver` | /worlds setserver <from> <to>: move every world recorded on one server to another, settings included, without touching a world's files. | op |
| `uxmessentials.world.setspawn` | /worlds setspawn [name]: set a world's spawn point to where you are standing. | op |
| `uxmessentials.world.spawn` | /worlds spawn to teleport to a world's spawn (subject to per-world access rules). | true |
| `uxmessentials.world.tp` | /worlds tp <name>: teleport yourself to a world's spawn. | op |
| `uxmessentials.world.tp.others` | /worlds tp <name> <player>: teleport somebody else to a world's spawn. | op |
| `uxmessentials.world.unload` | /worlds unload <name>: unload a loaded world, moving anybody inside to spawn. | op |
| `uxmessentials.world.unregister` | /worlds unregister <name>: drop a world from the registry, leaving its folder on disk. | op |
| `uxmessentials.bypass.void-rescue` | Keep falling in a world that catches players out of the void. | false |
