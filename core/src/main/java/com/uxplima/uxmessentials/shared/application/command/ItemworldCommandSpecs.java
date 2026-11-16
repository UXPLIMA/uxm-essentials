package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.ItemworldPermissions;

/** The item and world tools: item edits, workstations, mobs, time, weather and admin fun. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class ItemworldCommandSpecs {

    private static final ModuleId ITEMWORLD = ModuleId.of("itemworld");

    private ItemworldCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.of(ITEMWORLD, "antioch", ItemworldPermissions.ANTIOCH_USE, "grenade"),
                CommandSpec.of(ITEMWORLD, "anvil", ItemworldPermissions.WORKSTATION_ANVIL),
                CommandSpec.of(ITEMWORLD, "beezooka", ItemworldPermissions.BEEZOOKA_USE, "beecannon"),
                CommandSpec.of(ITEMWORLD, "bigtree", ItemworldPermissions.TREE_USE, "largetree"),
                CommandSpec.of(ITEMWORLD, "book", ItemworldPermissions.BOOK_USE),
                CommandSpec.of(ITEMWORLD, "break", ItemworldPermissions.BREAK_USE),
                CommandSpec.of(ITEMWORLD, "butcher", ItemworldPermissions.BUTCHER_USE),
                CommandSpec.of(ITEMWORLD, "cartography", ItemworldPermissions.WORKSTATION_CARTOGRAPHY),
                CommandSpec.of(ITEMWORLD, "condense", ItemworldPermissions.CONDENSE_USE, "compact"),
                CommandSpec.of(ITEMWORLD, "copyinv", ItemworldPermissions.COPYINV_USE),
                CommandSpec.of(ITEMWORLD, "day", ItemworldPermissions.TIME_ALIAS),
                CommandSpec.of(ITEMWORLD, "disenchant", ItemworldPermissions.DISENCHANT_USE),
                CommandSpec.of(ITEMWORLD, "disposal", ItemworldPermissions.DISPOSAL_USE, "trash"),
                CommandSpec.of(ITEMWORLD, "editsign", ItemworldPermissions.EDITSIGN_USE),
                CommandSpec.of(ITEMWORLD, "enchant", ItemworldPermissions.ENCHANT_USE),
                CommandSpec.of(ITEMWORLD, "enderchest", ItemworldPermissions.WORKSTATION_ENDERCHEST, "echest"),
                CommandSpec.of(ITEMWORLD, "enderclear", ItemworldPermissions.ENDERCLEAR_USE, "clearec"),
                CommandSpec.of(ITEMWORLD, "endercopy", ItemworldPermissions.ENDERCOPY_USE),
                CommandSpec.of(ITEMWORLD, "entitycount", ItemworldPermissions.ENTITYCOUNT_USE),
                CommandSpec.of(ITEMWORLD, "fireball", ItemworldPermissions.FIREBALL_USE),
                CommandSpec.of(ITEMWORLD, "firework", ItemworldPermissions.FIREWORK_USE),
                CommandSpec.of(ITEMWORLD, "furnace", ItemworldPermissions.WORKSTATION_FURNACE),
                CommandSpec.of(ITEMWORLD, "give", ItemworldPermissions.GIVE_USE, "i"),
                CommandSpec.of(ITEMWORLD, "giveall", ItemworldPermissions.GIVEALL_USE),
                CommandSpec.of(ITEMWORLD, "grindstone", ItemworldPermissions.WORKSTATION_GRINDSTONE),
                CommandSpec.of(ITEMWORLD, "hat", ItemworldPermissions.HAT_USE),
                CommandSpec.of(ITEMWORLD, "item", ItemworldPermissions.ITEM_USE),
                CommandSpec.of(ITEMWORLD, "itemamount", ItemworldPermissions.ITEMAMOUNT_USE, "amount"),
                CommandSpec.of(ITEMWORLD, "itemdamage", ItemworldPermissions.ITEMDAMAGE_USE, "durability"),
                CommandSpec.of(ITEMWORLD, "itemdb", ItemworldPermissions.ITEMDB_USE, "idb"),
                CommandSpec.of(ITEMWORLD, "itemedit", ItemworldPermissions.ITEMWORLD_ITEMEDIT),
                CommandSpec.of(ITEMWORLD, "itemflag", ItemworldPermissions.ITEMFLAG_USE, "iflag"),
                CommandSpec.of(ITEMWORLD, "iteminfo", ItemworldPermissions.ITEMINFO_USE),
                CommandSpec.of(ITEMWORLD, "itemlore", ItemworldPermissions.ITEMLORE_USE, "lore"),
                CommandSpec.of(ITEMWORLD, "itemmodel", ItemworldPermissions.ITEMMODEL_USE, "custommodeldata"),
                CommandSpec.of(ITEMWORLD, "itemname", ItemworldPermissions.ITEMNAME_USE, "iname"),
                CommandSpec.of(ITEMWORLD, "itemworld", ItemworldPermissions.ITEMWORLD_GUI),
                CommandSpec.of(ITEMWORLD, "kill", ItemworldPermissions.KILL_USE),
                CommandSpec.of(ITEMWORLD, "killall", ItemworldPermissions.KILLALL_USE),
                CommandSpec.of(ITEMWORLD, "kittycannon", ItemworldPermissions.KITTYCANNON_USE),
                CommandSpec.of(ITEMWORLD, "lightning", ItemworldPermissions.LIGHTNING_USE, "smite"),
                CommandSpec.of(ITEMWORLD, "loom", ItemworldPermissions.WORKSTATION_LOOM),
                CommandSpec.of(ITEMWORLD, "more", ItemworldPermissions.MORE_USE),
                CommandSpec.of(ITEMWORLD, "night", ItemworldPermissions.TIME_ALIAS),
                CommandSpec.of(ITEMWORLD, "nuke", ItemworldPermissions.NUKE_USE),
                CommandSpec.of(ITEMWORLD, "potion", ItemworldPermissions.POTION_USE),
                CommandSpec.of(ITEMWORLD, "powertool", ItemworldPermissions.POWERTOOL_USE, "pt"),
                CommandSpec.of(ITEMWORLD, "powertoollist", ItemworldPermissions.POWERTOOL_USE),
                CommandSpec.of(ITEMWORLD, "powertooltoggle", ItemworldPermissions.POWERTOOL_TOGGLE),
                CommandSpec.of(ITEMWORLD, "rain", ItemworldPermissions.WEATHER_ALIAS),
                CommandSpec.of(ITEMWORLD, "recipe", ItemworldPermissions.RECIPE_USE),
                CommandSpec.of(ITEMWORLD, "remove", ItemworldPermissions.REMOVE_USE),
                CommandSpec.of(ITEMWORLD, "repair", ItemworldPermissions.REPAIR_ITEMWORLD),
                CommandSpec.of(ITEMWORLD, "repairall", ItemworldPermissions.REPAIR_ITEMWORLD),
                CommandSpec.of(ITEMWORLD, "showitem", ItemworldPermissions.SHOWITEM_USE),
                CommandSpec.of(ITEMWORLD, "skull", ItemworldPermissions.SKULL_USE),
                CommandSpec.of(ITEMWORLD, "smithingtable", ItemworldPermissions.WORKSTATION_SMITHINGTABLE),
                CommandSpec.of(ITEMWORLD, "spawner", ItemworldPermissions.SPAWNER_USE),
                CommandSpec.of(ITEMWORLD, "spawnmob", ItemworldPermissions.SPAWNMOB_USE),
                CommandSpec.of(ITEMWORLD, "stonecutter", ItemworldPermissions.WORKSTATION_STONECUTTER),
                CommandSpec.of(ITEMWORLD, "sun", ItemworldPermissions.WEATHER_ALIAS),
                CommandSpec.of(ITEMWORLD, "thunder", ItemworldPermissions.WEATHER_ALIAS),
                CommandSpec.of(ITEMWORLD, "time", ItemworldPermissions.TIME_USE),
                CommandSpec.of(ITEMWORLD, "tree", ItemworldPermissions.TREE_USE),
                CommandSpec.of(ITEMWORLD, "unbreakable", ItemworldPermissions.UNBREAKABLE_USE),
                CommandSpec.of(ITEMWORLD, "unlimited", ItemworldPermissions.UNLIMITED_USE),
                CommandSpec.of(ITEMWORLD, "weather", ItemworldPermissions.WEATHER_USE),
                CommandSpec.of(ITEMWORLD, "workbench", ItemworldPermissions.WORKSTATION_WORKBENCH, "craft"));
    }
}
