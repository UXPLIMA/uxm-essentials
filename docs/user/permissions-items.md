# Permissions: item and world tools

Every node the itemworld module checks, with the default it gets when no permission plugin grants it: `true` for every player, `op` for operators, `false` for nobody. A node ending in `<enchantment>`, `<item>` or `<mob>` takes the name of one. The gameplay modules are in [permissions-gameplay.md](permissions-gameplay.md), and the staff and server modules and the plugin-wide nodes are in [permissions-server.md](permissions-server.md). `/uxmess permissions export` writes the same catalogue from the running build to `permissions.md` in the plugin folder.

## itemworld

| Permission | What it allows | Default |
|---|---|---|
| `uxmessentials.book.use` | /book: unlock a written book for editing. | op |
| `uxmessentials.condense.use` | /condense (alias /compact) [all]: recipe-stack inventory items. | op |
| `uxmessentials.disenchant.use` | /disenchant [all\|<enchant>]: remove all or one enchantment from the held item. | op |
| `uxmessentials.editsign.use` | /editsign: edit the sign you are looking at (respects build access). | op |
| `uxmessentials.enchant.use` | /enchant <enchant> [level]: enchant the held item (level clamped at the boundary). | op |
| `uxmessentials.firework.use` | /firework <color\|clear\|power>: style or power the held firework rocket. | op |
| `uxmessentials.give.use` | /give <player> <item> [amount] (alias /i): give an item to a player; bulk gives are audited. | op |
| `uxmessentials.giveall.use` | /giveall <item> [amount]: give an item to every online player; bulk gives are audited per recipient. | op |
| `uxmessentials.hat.use` | /hat: wear the held item as a helmet (itemworld-owned; playerstate defers it). | op |
| `uxmessentials.item.use` | /item <item> [amount]: give an item to yourself. | op |
| `uxmessentials.itemamount.use` | /itemamount <amount> (/amount): set the held stack amount, clamped to the give cap. | op |
| `uxmessentials.itemdamage.use` | /itemdamage <damage> (/durability): set the held item's durability damage. | op |
| `uxmessentials.itemdb.use` | /itemdb [item]: look up an item's id / data. | op |
| `uxmessentials.itemflag.use` | /itemflag <flag> <on\|off>: toggle an item meta flag. | op |
| `uxmessentials.iteminfo.use` | /iteminfo: inspect the metadata of the item in your hand. | op |
| `uxmessentials.itemlore.use` | /itemlore <set\|add\|clear> [text]: edit the held item's lore. | op |
| `uxmessentials.itemmodel.use` | /itemmodel <id\|clear> (alias /custommodeldata): set or clear the held item's custom model data. | op |
| `uxmessentials.itemname.use` | /itemname <name>: rename the held item. | op |
| `uxmessentials.itemworld.enchant.<enchantment>` | Apply one specific enchantment through /enchant, when per-enchantment gating is switched on. | op |
| `uxmessentials.itemworld.give.<item>` | Give one specific item through /give, when per-item gating is switched on. | op |
| `uxmessentials.itemworld.gui` | Open the itemworld utilities hub (/itemworld gui and on the /uxmess gui hub). | op |
| `uxmessentials.itemworld.itemedit` | /itemedit <rename\|resetname\|lore\|enchant\|unenchant\|flag\|attribute\|durability\|repair\|unbreakable\|custommodeldata>: edit the held item's name, lore and meta (item-edit.enabled). | op |
| `uxmessentials.itemworld.itemedit.rename` | /itemedit rename and resetname. Defaults on for anyone who already holds the editor node; negate it to withhold just this verb. | true |
| `uxmessentials.itemworld.itemedit.lore` | /itemedit lore add\|set\|insert\|remove\|clear. Defaults on for an editor-node holder; negate it to withhold just this verb. | true |
| `uxmessentials.itemworld.itemedit.enchant` | /itemedit enchant and unenchant. Defaults on for an editor-node holder; negate it to withhold just this verb. | true |
| `uxmessentials.itemworld.itemedit.flag` | /itemedit flag: toggle an item flag such as HIDE_ENCHANTS. Defaults on for an editor-node holder. | true |
| `uxmessentials.itemworld.itemedit.attribute` | /itemedit attribute add\|remove: edit attribute modifiers. Defaults on for an editor-node holder; the verb most worth withholding on a survival server. | true |
| `uxmessentials.itemworld.itemedit.durability` | /itemedit durability and repair. Defaults on for an editor-node holder; negate it to withhold just this verb. | true |
| `uxmessentials.itemworld.itemedit.unbreakable` | /itemedit unbreakable: mark the held item unbreakable. Defaults on for an editor-node holder. | true |
| `uxmessentials.itemworld.itemedit.model` | /itemedit custommodeldata: set or clear the resource-pack model selector. Defaults on for an editor-node holder. | true |
| `uxmessentials.itemworld.itemedit.gui` | A bare /itemedit, which opens the click-driven editor. Defaults on for an editor-node holder; negate it to leave only the typed verbs. | true |
| `uxmessentials.itemworld.shulker` | Right-click a shulker box in the inventory to open its contents in place (shulkers.enabled). | op |
| `uxmessentials.itemworld.spawnmob.<mob>` | Spawn one specific mob through /spawnmob, when per-mob gating is switched on. | op |
| `uxmessentials.module.itemworld` | Hot-reload / inspect the itemworld module and its sub-feature groups. | op |
| `uxmessentials.more.use` | /more: fill the held stack to max (itemworld-owned; playerstate defers it). | op |
| `uxmessentials.potion.use` | /potion <effect> [duration] [amplifier]: add a potion effect to the held potion. | op |
| `uxmessentials.recipe.use` | /recipe [item]: show an item's crafting recipe. | op |
| `uxmessentials.repair.itemworld` | /repair and /repairall in the itemworld surface (itemworld-owned; playerstate defers them). | op |
| `uxmessentials.showitem.use` | /showitem: broadcast the held item to chat for everyone online. | true |
| `uxmessentials.skull.use` | /skull [player]: get a player-head skull. | op |
| `uxmessentials.unbreakable.use` | /unbreakable [true\|false]: toggle or set the held item's unbreakable flag. | op |
| `uxmessentials.unlimited.use` | /unlimited: toggle unlimited placement of held blocks. | op |
| `uxmessentials.butcher.use` | /butcher [radius]: purge nearby mobs (audit-logged). | op |
| `uxmessentials.entitycount.use` | /entitycount [radius]: tally nearby entities by type for lag diagnosis. | op |
| `uxmessentials.kill.use` | /kill [player\|entity]: kill a target (audit-logged). | op |
| `uxmessentials.killall.use` | /killall [type]: purge entities world-wide (audit-logged). | op |
| `uxmessentials.kittycannon.use` | /kittycannon: launch an exploding cat (audit-logged). | op |
| `uxmessentials.remove.use` | /remove <type> [radius]: remove entities by type (audit-logged). | op |
| `uxmessentials.spawner.use` | /spawner <type>: set a spawner's mob type (audit-logged). | op |
| `uxmessentials.spawnmob.use` | /spawnmob <type> [amount]: spawn mobs (audit-logged). | op |
| `uxmessentials.copyinv.use` | /copyinv <player>: copy a player's inventory into yours. | op |
| `uxmessentials.disposal.use` | /disposal (alias /trash): open a throwaway GUI. | op |
| `uxmessentials.enderclear.use` | /enderclear (alias /clearec) [player]: clear an ender chest. | op |
| `uxmessentials.endercopy.use` | /endercopy <player>: copy a player's ender chest into yours. | op |
| `uxmessentials.workstation.anvil` | /anvil: open a virtual anvil. | op |
| `uxmessentials.workstation.cartography` | /cartography: open a virtual cartography table. | op |
| `uxmessentials.workstation.enderchest` | /enderchest (alias /echest): open your ender chest. | op |
| `uxmessentials.workstation.furnace` | /furnace: open a virtual furnace. | op |
| `uxmessentials.workstation.grindstone` | /grindstone: open a virtual grindstone. | op |
| `uxmessentials.workstation.loom` | /loom: open a virtual loom. | op |
| `uxmessentials.workstation.others` | Open any virtual workstation on another player with the [player] target form. | op |
| `uxmessentials.workstation.smithingtable` | /smithingtable: open a virtual smithing table. | op |
| `uxmessentials.workstation.stonecutter` | /stonecutter: open a virtual stonecutter. | op |
| `uxmessentials.workstation.workbench` | /workbench (alias /craft): open a virtual crafting table. | op |
| `uxmessentials.antioch.use` | /antioch (alias /grenade): throw a primed TNT grenade (audit-logged). | op |
| `uxmessentials.beezooka.use` | /beezooka (alias /beecannon): launch an angry bee (audit-logged). | op |
| `uxmessentials.break.use` | /break: instantly break the block you are looking at (audit-logged). | op |
| `uxmessentials.fireball.use` | /fireball: launch a fireball (audit-logged). | op |
| `uxmessentials.lightning.use` | /lightning (alias /smite) [player]: strike lightning (audit-logged). | op |
| `uxmessentials.nuke.use` | /nuke [player]: rain lightning over an area (audit-logged). | op |
| `uxmessentials.tree.use` | /tree <type>: generate a tree of the given type where you are looking (audit-logged). | op |
| `uxmessentials.powertool.toggle` | /powertooltoggle: enable/disable your powertool bindings. | op |
| `uxmessentials.powertool.use` | /powertool <command> (alias /pt): bind a command to the held item. | op |
| `uxmessentials.time.alias` | /day / /night quick time aliases. | op |
| `uxmessentials.time.use` | /time <set\|add> <value>: per-world time. | op |
| `uxmessentials.weather.alias` | /sun / /rain / /thunder quick weather aliases. | op |
| `uxmessentials.weather.use` | /weather <clear\|rain\|thunder> [duration]. | op |
