package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the itemworld context. Data, not logic: one row per node, read by {@link PermissionCatalog}
 * and through it by the server registration, the reference page and the in-game listing.
 */
final class ItemworldPermissions {

    private static final ModuleId ITEMWORLD = ModuleId.of("itemworld");

    private ItemworldPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(
                        itemworldItems(),
                        itemworldEntities(),
                        itemworldContainers(),
                        itemworldAdminFun(),
                        itemworldUtilities())
                .flatMap(List::stream)
                .toList();
    }

    /** The item verbs: creating, editing and inspecting what a player is holding. */
    private static List<PermissionSpec> itemworldItems() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.book.use",
                        "/book: unlock a written book for editing.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.condense.use",
                        "/condense (alias /compact) [all]: recipe-stack inventory items.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.disenchant.use",
                        "/disenchant [all|<enchant>]: remove all or one enchantment from the held item.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.editsign.use",
                        "/editsign: edit the sign you are looking at (respects build access).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.enchant.use",
                        "/enchant <enchant> [level]: enchant the held item (level clamped at the boundary).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.firework.use",
                        "/firework <color|clear|power>: style or power the held firework rocket.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.give.use",
                        "/give <player> <item> [amount] (alias /i): give an item to a player; bulk gives are audited.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.giveall.use",
                        "/giveall <item> [amount]: give an item to every online player; bulk gives are audited per recipient.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.hat.use",
                        "/hat: wear the held item as a helmet (itemworld-owned; playerstate defers it).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.item.use",
                        "/item <item> [amount]: give an item to yourself.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemamount.use",
                        "/itemamount <amount> (/amount): set the held stack amount, clamped to the give cap.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemdamage.use",
                        "/itemdamage <damage> (/durability): set the held item's durability damage.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemdb.use",
                        "/itemdb [item]: look up an item's id / data.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemflag.use",
                        "/itemflag <flag> <on|off>: toggle an item meta flag.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.iteminfo.use",
                        "/iteminfo: inspect the metadata of the item in your hand.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemlore.use",
                        "/itemlore <set|add|clear> [text]: edit the held item's lore.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemmodel.use",
                        "/itemmodel <id|clear> (alias /custommodeldata): set or clear the held item's custom model data.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemname.use",
                        "/itemname <name>: rename the held item.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.family(
                        "uxmessentials.itemworld.enchant.<enchantment>",
                        "Apply one specific enchantment through /enchant, when per-enchantment gating is switched on.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        ITEMWORLD),
                PermissionSpec.family(
                        "uxmessentials.itemworld.give.<item>",
                        "Give one specific item through /give, when per-item gating is switched on.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemworld.gui",
                        "Open the itemworld utilities hub (/itemworld gui and on the /uxmess gui hub).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemworld.itemedit",
                        "/itemedit <rename|resetname|lore|enchant|unenchant|flag|attribute|durability|repair|unbreakable|custommodeldata>: edit the held item's name, lore and meta (item-edit.enabled).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.itemworld.shulker",
                        "Right-click a shulker box in the inventory to open its contents in place (shulkers.enabled).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.family(
                        "uxmessentials.itemworld.spawnmob.<mob>",
                        "Spawn one specific mob through /spawnmob, when per-mob gating is switched on.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.module.itemworld",
                        "Hot-reload / inspect the itemworld module and its sub-feature groups.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.more.use",
                        "/more: fill the held stack to max (itemworld-owned; playerstate defers it).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.potion.use",
                        "/potion <effect> [duration] [amplifier]: add a potion effect to the held potion.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.recipe.use",
                        "/recipe [item]: show an item's crafting recipe.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.repair.itemworld",
                        "/repair and /repairall in the itemworld surface (itemworld-owned; playerstate defers them).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.showitem.use",
                        "/showitem: broadcast the held item to chat for everyone online.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.skull.use",
                        "/skull [player]: get a player-head skull.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.unbreakable.use",
                        "/unbreakable [true|false]: toggle or set the held item's unbreakable flag.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.unlimited.use",
                        "/unlimited: toggle unlimited placement of held blocks.",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }

    /** The entity verbs: spawning, counting and clearing what walks around. */
    private static List<PermissionSpec> itemworldEntities() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.butcher.use",
                        "/butcher [radius]: purge nearby mobs (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.entitycount.use",
                        "/entitycount [radius]: tally nearby entities by type for lag diagnosis.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.kill.use",
                        "/kill [player|entity]: kill a target (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.killall.use",
                        "/killall [type]: purge entities world-wide (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.kittycannon.use",
                        "/kittycannon: launch an exploding cat (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.remove.use",
                        "/remove <type> [radius]: remove entities by type (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.spawner.use",
                        "/spawner <type>: set a spawner's mob type (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.spawnmob.use",
                        "/spawnmob <type> [amount]: spawn mobs (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }

    /** The container verbs: the throwaway inventories and the copy verbs. */
    private static List<PermissionSpec> itemworldContainers() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.copyinv.use",
                        "/copyinv <player>: copy a player's inventory into yours.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.disposal.use",
                        "/disposal (alias /trash): open a throwaway GUI.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.enderclear.use",
                        "/enderclear (alias /clearec) [player]: clear an ender chest.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.endercopy.use",
                        "/endercopy <player>: copy a player's ender chest into yours.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.anvil",
                        "/anvil: open a virtual anvil.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.cartography",
                        "/cartography: open a virtual cartography table.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.enderchest",
                        "/enderchest (alias /echest): open your ender chest.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.furnace",
                        "/furnace: open a virtual furnace.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.grindstone",
                        "/grindstone: open a virtual grindstone.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.loom",
                        "/loom: open a virtual loom.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.others",
                        "Open any virtual workstation on another player with the [player] target form.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.smithingtable",
                        "/smithingtable: open a virtual smithing table.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.stonecutter",
                        "/stonecutter: open a virtual stonecutter.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.workstation.workbench",
                        "/workbench (alias /craft): open a virtual crafting table.",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }

    /** The admin-fun verbs, every one of which changes the world around somebody. */
    private static List<PermissionSpec> itemworldAdminFun() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.antioch.use",
                        "/antioch (alias /grenade): throw a primed TNT grenade (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.beezooka.use",
                        "/beezooka (alias /beecannon): launch an angry bee (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.break.use",
                        "/break: instantly break the block you are looking at (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.fireball.use",
                        "/fireball: launch a fireball (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.lightning.use",
                        "/lightning (alias /smite) [player]: strike lightning (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.nuke.use",
                        "/nuke [player]: rain lightning over an area (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.tree.use",
                        "/tree <type>: generate a tree of the given type where you are looking (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }

    /** What is left: the time and weather aliases, the powertool and the hub. */
    private static List<PermissionSpec> itemworldUtilities() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.powertool.toggle",
                        "/powertooltoggle: enable/disable your powertool bindings.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.powertool.use",
                        "/powertool <command> (alias /pt): bind a command to the held item.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.time.alias",
                        "/day / /night quick time aliases.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.time.use",
                        "/time <set|add> <value>: per-world time.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.weather.alias",
                        "/sun / /rain / /thunder quick weather aliases.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        "uxmessentials.weather.use",
                        "/weather <clear|rain|thunder> [duration].",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }
}
