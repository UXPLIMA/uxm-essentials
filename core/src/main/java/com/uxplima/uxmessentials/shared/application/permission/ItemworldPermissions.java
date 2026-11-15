package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the itemworld context. Data, not logic: one row per node, read by {@link PermissionCatalog}
 * and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class ItemworldPermissions {

    private static final ModuleId ITEMWORLD = ModuleId.of("itemworld");

    public static final String BOOK_USE = "uxmessentials.book.use";
    public static final String CONDENSE_USE = "uxmessentials.condense.use";
    public static final String DISENCHANT_USE = "uxmessentials.disenchant.use";
    public static final String EDITSIGN_USE = "uxmessentials.editsign.use";
    public static final String ENCHANT_USE = "uxmessentials.enchant.use";
    public static final String FIREWORK_USE = "uxmessentials.firework.use";
    public static final String GIVE_USE = "uxmessentials.give.use";
    public static final String GIVEALL_USE = "uxmessentials.giveall.use";
    public static final String HAT_USE = "uxmessentials.hat.use";
    public static final String ITEM_USE = "uxmessentials.item.use";
    public static final String ITEMAMOUNT_USE = "uxmessentials.itemamount.use";
    public static final String ITEMDAMAGE_USE = "uxmessentials.itemdamage.use";
    public static final String ITEMDB_USE = "uxmessentials.itemdb.use";
    public static final String ITEMFLAG_USE = "uxmessentials.itemflag.use";
    public static final String ITEMINFO_USE = "uxmessentials.iteminfo.use";
    public static final String ITEMLORE_USE = "uxmessentials.itemlore.use";
    public static final String ITEMMODEL_USE = "uxmessentials.itemmodel.use";
    public static final String ITEMNAME_USE = "uxmessentials.itemname.use";
    public static final String ITEMWORLD_GUI = "uxmessentials.itemworld.gui";
    public static final String ITEMWORLD_ITEMEDIT = "uxmessentials.itemworld.itemedit";
    public static final String ITEMWORLD_ITEMEDIT_RENAME = "uxmessentials.itemworld.itemedit.rename";
    public static final String ITEMWORLD_ITEMEDIT_LORE = "uxmessentials.itemworld.itemedit.lore";
    public static final String ITEMWORLD_ITEMEDIT_ENCHANT = "uxmessentials.itemworld.itemedit.enchant";
    public static final String ITEMWORLD_ITEMEDIT_FLAG = "uxmessentials.itemworld.itemedit.flag";
    public static final String ITEMWORLD_ITEMEDIT_ATTRIBUTE = "uxmessentials.itemworld.itemedit.attribute";
    public static final String ITEMWORLD_ITEMEDIT_DURABILITY = "uxmessentials.itemworld.itemedit.durability";
    public static final String ITEMWORLD_ITEMEDIT_UNBREAKABLE = "uxmessentials.itemworld.itemedit.unbreakable";
    public static final String ITEMWORLD_ITEMEDIT_MODEL = "uxmessentials.itemworld.itemedit.model";
    public static final String ITEMWORLD_ITEMEDIT_GUI = "uxmessentials.itemworld.itemedit.gui";
    public static final String ITEMWORLD_SHULKER = "uxmessentials.itemworld.shulker";
    public static final String MODULE_ITEMWORLD = "uxmessentials.module.itemworld";
    public static final String MORE_USE = "uxmessentials.more.use";
    public static final String POTION_USE = "uxmessentials.potion.use";
    public static final String RECIPE_USE = "uxmessentials.recipe.use";
    public static final String REPAIR_ITEMWORLD = "uxmessentials.repair.itemworld";
    public static final String SHOWITEM_USE = "uxmessentials.showitem.use";
    public static final String SKULL_USE = "uxmessentials.skull.use";
    public static final String UNBREAKABLE_USE = "uxmessentials.unbreakable.use";
    public static final String UNLIMITED_USE = "uxmessentials.unlimited.use";
    public static final String BUTCHER_USE = "uxmessentials.butcher.use";
    public static final String ENTITYCOUNT_USE = "uxmessentials.entitycount.use";
    public static final String KILL_USE = "uxmessentials.kill.use";
    public static final String KILLALL_USE = "uxmessentials.killall.use";
    public static final String KITTYCANNON_USE = "uxmessentials.kittycannon.use";
    public static final String REMOVE_USE = "uxmessentials.remove.use";
    public static final String SPAWNER_USE = "uxmessentials.spawner.use";
    public static final String SPAWNMOB_USE = "uxmessentials.spawnmob.use";
    public static final String COPYINV_USE = "uxmessentials.copyinv.use";
    public static final String DISPOSAL_USE = "uxmessentials.disposal.use";
    public static final String ENDERCLEAR_USE = "uxmessentials.enderclear.use";
    public static final String ENDERCOPY_USE = "uxmessentials.endercopy.use";
    public static final String WORKSTATION_ANVIL = "uxmessentials.workstation.anvil";
    public static final String WORKSTATION_CARTOGRAPHY = "uxmessentials.workstation.cartography";
    public static final String WORKSTATION_ENDERCHEST = "uxmessentials.workstation.enderchest";
    public static final String WORKSTATION_FURNACE = "uxmessentials.workstation.furnace";
    public static final String WORKSTATION_GRINDSTONE = "uxmessentials.workstation.grindstone";
    public static final String WORKSTATION_LOOM = "uxmessentials.workstation.loom";
    public static final String WORKSTATION_OTHERS = "uxmessentials.workstation.others";
    public static final String WORKSTATION_SMITHINGTABLE = "uxmessentials.workstation.smithingtable";
    public static final String WORKSTATION_STONECUTTER = "uxmessentials.workstation.stonecutter";
    public static final String WORKSTATION_WORKBENCH = "uxmessentials.workstation.workbench";
    public static final String ANTIOCH_USE = "uxmessentials.antioch.use";
    public static final String BEEZOOKA_USE = "uxmessentials.beezooka.use";
    public static final String BREAK_USE = "uxmessentials.break.use";
    public static final String FIREBALL_USE = "uxmessentials.fireball.use";
    public static final String LIGHTNING_USE = "uxmessentials.lightning.use";
    public static final String NUKE_USE = "uxmessentials.nuke.use";
    public static final String TREE_USE = "uxmessentials.tree.use";
    public static final String POWERTOOL_TOGGLE = "uxmessentials.powertool.toggle";
    public static final String POWERTOOL_USE = "uxmessentials.powertool.use";
    public static final String TIME_ALIAS = "uxmessentials.time.alias";
    public static final String TIME_USE = "uxmessentials.time.use";
    public static final String WEATHER_ALIAS = "uxmessentials.weather.alias";
    public static final String WEATHER_USE = "uxmessentials.weather.use";

    private static final String ENCHANT_PREFIX = "uxmessentials.itemworld.enchant.";
    private static final String GIVE_PREFIX = "uxmessentials.itemworld.give.";
    private static final String SPAWNMOB_PREFIX = "uxmessentials.itemworld.spawnmob.";

    /** The node that lets {@code /enchant} apply one enchantment when per-enchantment gating is on. */
    public static String enchantment(String enchantment) {
        return ENCHANT_PREFIX + enchantment;
    }

    /** The node that lets {@code /give} hand out one item when per-item gating is on. */
    public static String giveItem(String item) {
        return GIVE_PREFIX + item;
    }

    /** The node that lets {@code /spawnmob} spawn one mob when per-mob gating is on. */
    public static String spawnMob(String mob) {
        return SPAWNMOB_PREFIX + mob;
    }

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
                        BOOK_USE, "/book: unlock a written book for editing.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        CONDENSE_USE,
                        "/condense (alias /compact) [all]: recipe-stack inventory items.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        DISENCHANT_USE,
                        "/disenchant [all|<enchant>]: remove all or one enchantment from the held item.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        EDITSIGN_USE,
                        "/editsign: edit the sign you are looking at (respects build access).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ENCHANT_USE,
                        "/enchant <enchant> [level]: enchant the held item (level clamped at the boundary).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        FIREWORK_USE,
                        "/firework <color|clear|power>: style or power the held firework rocket.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        GIVE_USE,
                        "/give <player> <item> [amount] (alias /i): give an item to a player; bulk gives are audited.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        GIVEALL_USE,
                        "/giveall <item> [amount]: give an item to every online player; bulk gives are audited per recipient.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        HAT_USE,
                        "/hat: wear the held item as a helmet (itemworld-owned; playerstate defers it).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEM_USE, "/item <item> [amount]: give an item to yourself.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        ITEMAMOUNT_USE,
                        "/itemamount <amount> (/amount): set the held stack amount, clamped to the give cap.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMDAMAGE_USE,
                        "/itemdamage <damage> (/durability): set the held item's durability damage.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMDB_USE, "/itemdb [item]: look up an item's id / data.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        ITEMFLAG_USE,
                        "/itemflag <flag> <on|off>: toggle an item meta flag.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMINFO_USE,
                        "/iteminfo: inspect the metadata of the item in your hand.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMLORE_USE,
                        "/itemlore <set|add|clear> [text]: edit the held item's lore.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMMODEL_USE,
                        "/itemmodel <id|clear> (alias /custommodeldata): set or clear the held item's custom model data.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMNAME_USE, "/itemname <name>: rename the held item.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.family(
                        enchantment("<enchantment>"),
                        "Apply one specific enchantment through /enchant, when per-enchantment gating is switched on.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        ITEMWORLD),
                PermissionSpec.family(
                        giveItem("<item>"),
                        "Give one specific item through /give, when per-item gating is switched on.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_GUI,
                        "Open the itemworld utilities hub (/itemworld gui and on the /uxmess gui hub).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT,
                        "/itemedit <rename|resetname|lore|enchant|unenchant|flag|attribute|durability|repair|unbreakable|custommodeldata>: edit the held item's name, lore and meta (item-edit.enabled).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_RENAME,
                        "/itemedit rename and resetname. Defaults on for anyone who already holds the editor node; negate it to withhold just this verb.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_LORE,
                        "/itemedit lore add|set|insert|remove|clear. Defaults on for an editor-node holder; negate it to withhold just this verb.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_ENCHANT,
                        "/itemedit enchant and unenchant. Defaults on for an editor-node holder; negate it to withhold just this verb.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_FLAG,
                        "/itemedit flag: toggle an item flag such as HIDE_ENCHANTS. Defaults on for an editor-node holder.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_ATTRIBUTE,
                        "/itemedit attribute add|remove: edit attribute modifiers. Defaults on for an editor-node holder; the verb most worth withholding on a survival server.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_DURABILITY,
                        "/itemedit durability and repair. Defaults on for an editor-node holder; negate it to withhold just this verb.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_UNBREAKABLE,
                        "/itemedit unbreakable: mark the held item unbreakable. Defaults on for an editor-node holder.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_MODEL,
                        "/itemedit custommodeldata: set or clear the resource-pack model selector. Defaults on for an editor-node holder.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_ITEMEDIT_GUI,
                        "A bare /itemedit, which opens the click-driven editor. Defaults on for an editor-node holder; negate it to leave only the typed verbs.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        ITEMWORLD_SHULKER,
                        "Right-click a shulker box in the inventory to open its contents in place (shulkers.enabled).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.family(
                        spawnMob("<mob>"),
                        "Spawn one specific mob through /spawnmob, when per-mob gating is switched on.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        ITEMWORLD),
                PermissionSpec.of(
                        MODULE_ITEMWORLD,
                        "Hot-reload / inspect the itemworld module and its sub-feature groups.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        MORE_USE,
                        "/more: fill the held stack to max (itemworld-owned; playerstate defers it).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        POTION_USE,
                        "/potion <effect> [duration] [amplifier]: add a potion effect to the held potion.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        RECIPE_USE, "/recipe [item]: show an item's crafting recipe.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        REPAIR_ITEMWORLD,
                        "/repair and /repairall in the itemworld surface (itemworld-owned; playerstate defers them).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        SHOWITEM_USE,
                        "/showitem: broadcast the held item to chat for everyone online.",
                        PermissionDefault.TRUE,
                        ITEMWORLD),
                PermissionSpec.of(
                        SKULL_USE, "/skull [player]: get a player-head skull.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        UNBREAKABLE_USE,
                        "/unbreakable [true|false]: toggle or set the held item's unbreakable flag.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        UNLIMITED_USE,
                        "/unlimited: toggle unlimited placement of held blocks.",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }

    /** The entity verbs: spawning, counting and clearing what walks around. */
    private static List<PermissionSpec> itemworldEntities() {
        return List.of(
                PermissionSpec.of(
                        BUTCHER_USE,
                        "/butcher [radius]: purge nearby mobs (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ENTITYCOUNT_USE,
                        "/entitycount [radius]: tally nearby entities by type for lag diagnosis.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        KILL_USE,
                        "/kill [player|entity]: kill a target (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        KILLALL_USE,
                        "/killall [type]: purge entities world-wide (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        KITTYCANNON_USE,
                        "/kittycannon: launch an exploding cat (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        REMOVE_USE,
                        "/remove <type> [radius]: remove entities by type (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        SPAWNER_USE,
                        "/spawner <type>: set a spawner's mob type (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        SPAWNMOB_USE,
                        "/spawnmob <type> [amount]: spawn mobs (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }

    /** The container verbs: the throwaway inventories and the copy verbs. */
    private static List<PermissionSpec> itemworldContainers() {
        return List.of(
                PermissionSpec.of(
                        COPYINV_USE,
                        "/copyinv <player>: copy a player's inventory into yours.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        DISPOSAL_USE,
                        "/disposal (alias /trash): open a throwaway GUI.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ENDERCLEAR_USE,
                        "/enderclear (alias /clearec) [player]: clear an ender chest.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        ENDERCOPY_USE,
                        "/endercopy <player>: copy a player's ender chest into yours.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(WORKSTATION_ANVIL, "/anvil: open a virtual anvil.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        WORKSTATION_CARTOGRAPHY,
                        "/cartography: open a virtual cartography table.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        WORKSTATION_ENDERCHEST,
                        "/enderchest (alias /echest): open your ender chest.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        WORKSTATION_FURNACE, "/furnace: open a virtual furnace.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        WORKSTATION_GRINDSTONE,
                        "/grindstone: open a virtual grindstone.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(WORKSTATION_LOOM, "/loom: open a virtual loom.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        WORKSTATION_OTHERS,
                        "Open any virtual workstation on another player with the [player] target form.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        WORKSTATION_SMITHINGTABLE,
                        "/smithingtable: open a virtual smithing table.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        WORKSTATION_STONECUTTER,
                        "/stonecutter: open a virtual stonecutter.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        WORKSTATION_WORKBENCH,
                        "/workbench (alias /craft): open a virtual crafting table.",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }

    /** The admin-fun verbs, every one of which changes the world around somebody. */
    private static List<PermissionSpec> itemworldAdminFun() {
        return List.of(
                PermissionSpec.of(
                        ANTIOCH_USE,
                        "/antioch (alias /grenade): throw a primed TNT grenade (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        BEEZOOKA_USE,
                        "/beezooka (alias /beecannon): launch an angry bee (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        BREAK_USE,
                        "/break: instantly break the block you are looking at (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        FIREBALL_USE, "/fireball: launch a fireball (audit-logged).", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        LIGHTNING_USE,
                        "/lightning (alias /smite) [player]: strike lightning (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        NUKE_USE,
                        "/nuke [player]: rain lightning over an area (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        TREE_USE,
                        "/tree <type>: generate a tree of the given type where you are looking (audit-logged).",
                        PermissionDefault.OP,
                        ITEMWORLD));
    }

    /** What is left: the time and weather aliases, the powertool and the hub. */
    private static List<PermissionSpec> itemworldUtilities() {
        return List.of(
                PermissionSpec.of(
                        POWERTOOL_TOGGLE,
                        "/powertooltoggle: enable/disable your powertool bindings.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        POWERTOOL_USE,
                        "/powertool <command> (alias /pt): bind a command to the held item.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(TIME_ALIAS, "/day / /night quick time aliases.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        TIME_USE, "/time <set|add> <value>: per-world time.", PermissionDefault.OP, ITEMWORLD),
                PermissionSpec.of(
                        WEATHER_ALIAS,
                        "/sun / /rain / /thunder quick weather aliases.",
                        PermissionDefault.OP,
                        ITEMWORLD),
                PermissionSpec.of(
                        WEATHER_USE, "/weather <clear|rain|thunder> [duration].", PermissionDefault.OP, ITEMWORLD));
    }
}
