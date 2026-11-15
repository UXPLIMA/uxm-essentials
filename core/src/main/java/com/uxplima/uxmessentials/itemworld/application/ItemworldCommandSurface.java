package com.uxplima.uxmessentials.itemworld.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.permission.ItemworldPermissions;

/**
 * The itemworld context's full command surface as platform-neutral {@link CommandSpec}s, the authoritative {@code
 * literal → permission node → sub-feature group} table the inbound adapter realises into Brigadier nodes on the next
 * {@code COMMANDS} fire. Collected here so the command/permission/group pairing is one greppable table, and so the
 * module can resolve a command's owning group for the per-sub-feature-group + per-command disable gate ({@link
 * ItemworldConfig#commandEnabled}).
 *
 * <p>Every verb in the ~40-verb surface is present and none is dropped. The {@code /repair}, {@code
 * /repairall}, {@code /hat} and {@code /more} verbs overlap the playerstate context by name but are owned
 * here (playerstate deferred them, §15.6); they are registered here and the two modules do not
 * double-register. Abusable verbs (bulk {@code /give}, {@code /spawnmob}, {@code /spawner}, the entity-purge
 * family, the admin-fun verbs) are audit-logged in their use cases; all inputs are validated at the adapter
 * boundary before a domain call.
 */
final class ItemworldCommandSurface {

    private ItemworldCommandSurface() {}

    private static final List<Verb> VERBS = buildVerbs();

    /** Every itemworld command as a {@link CommandSpec}, in surface order grouped by sub-feature group. */
    static List<CommandSpec> all() {
        List<CommandSpec> specs = new ArrayList<>(VERBS.size());
        for (Verb verb : VERBS) {
            specs.add(verb.toSpec());
        }
        return List.copyOf(specs);
    }

    /** The owning sub-feature group of a command literal, for the per-group + per-command disable gate. */
    static Optional<SubFeatureGroup> groupOf(String literal) {
        Objects.requireNonNull(literal, "literal");
        for (Verb verb : VERBS) {
            if (verb.literal().equals(literal)) {
                return Optional.of(verb.group());
            }
        }
        return Optional.empty();
    }

    /** The {@code literal → owning group} map, exposed for the module's disable-gate wiring and tests. */
    static Map<String, SubFeatureGroup> groupsByLiteral() {
        Map<String, SubFeatureGroup> map = new java.util.LinkedHashMap<>();
        for (Verb verb : VERBS) {
            map.put(verb.literal(), verb.group());
        }
        return Map.copyOf(map);
    }

    private static List<Verb> buildVerbs() {
        List<Verb> verbs = new ArrayList<>();
        // Item utils. /give (/i is the adapter alias of /give), /item, cosmetics, /more, repair, /enchant, /hat,
        // /itemdb.
        verbs.add(new Verb(
                "give", ItemworldPermissions.GIVE_USE, SubFeatureGroup.ITEM_UTILS, "Give an item to a player"));
        verbs.add(new Verb(
                "giveall",
                ItemworldPermissions.GIVEALL_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Give an item to every online player"));
        verbs.add(new Verb("item", ItemworldPermissions.ITEM_USE, SubFeatureGroup.ITEM_UTILS, "Give yourself an item"));
        verbs.add(new Verb(
                "itemname", ItemworldPermissions.ITEMNAME_USE, SubFeatureGroup.ITEM_UTILS, "Rename the held item"));
        verbs.add(new Verb(
                "itemlore", ItemworldPermissions.ITEMLORE_USE, SubFeatureGroup.ITEM_UTILS, "Edit held-item lore"));
        verbs.add(new Verb(
                "itemflag", ItemworldPermissions.ITEMFLAG_USE, SubFeatureGroup.ITEM_UTILS, "Toggle an item meta flag"));
        verbs.add(new Verb(
                "skull", ItemworldPermissions.SKULL_USE, SubFeatureGroup.ITEM_UTILS, "Get a player-head skull"));
        verbs.add(new Verb(
                "firework",
                ItemworldPermissions.FIREWORK_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Style or power the held firework rocket"));
        verbs.add(new Verb(
                "potion",
                ItemworldPermissions.POTION_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Add a potion effect to the held potion"));
        verbs.add(new Verb(
                "book",
                ItemworldPermissions.BOOK_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Unlock a written book for editing"));
        verbs.add(new Verb(
                "more", ItemworldPermissions.MORE_USE, SubFeatureGroup.ITEM_UTILS, "Fill the held stack to max"));
        verbs.add(new Verb(
                "itemamount",
                ItemworldPermissions.ITEMAMOUNT_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Set the held stack's amount"));
        verbs.add(new Verb(
                "itemdamage",
                ItemworldPermissions.ITEMDAMAGE_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Set the held item's durability damage"));
        verbs.add(new Verb(
                "repair", ItemworldPermissions.REPAIR_ITEMWORLD, SubFeatureGroup.ITEM_UTILS, "Repair the held item"));
        verbs.add(new Verb(
                "repairall",
                ItemworldPermissions.REPAIR_ITEMWORLD,
                SubFeatureGroup.ITEM_UTILS,
                "Repair the whole inventory"));
        verbs.add(new Verb(
                "enchant", ItemworldPermissions.ENCHANT_USE, SubFeatureGroup.ITEM_UTILS, "Enchant the held item"));
        verbs.add(new Verb(
                "hat", ItemworldPermissions.HAT_USE, SubFeatureGroup.ITEM_UTILS, "Wear the held item as a hat"));
        verbs.add(new Verb(
                "itemdb", ItemworldPermissions.ITEMDB_USE, SubFeatureGroup.ITEM_UTILS, "Look up an item's id"));
        verbs.add(new Verb(
                "showitem",
                ItemworldPermissions.SHOWITEM_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Broadcast the held item to chat"));
        verbs.add(new Verb(
                "recipe",
                ItemworldPermissions.RECIPE_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Show an item's crafting recipe"));
        verbs.add(new Verb(
                "unbreakable",
                ItemworldPermissions.UNBREAKABLE_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Toggle the held item's unbreakable flag"));
        verbs.add(new Verb(
                "disenchant",
                ItemworldPermissions.DISENCHANT_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Remove enchantments from the held item"));
        verbs.add(new Verb(
                "itemmodel",
                ItemworldPermissions.ITEMMODEL_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Set the held item's custom model data"));
        verbs.add(new Verb(
                "editsign",
                ItemworldPermissions.EDITSIGN_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Edit the sign you are looking at"));
        verbs.add(new Verb(
                "iteminfo",
                ItemworldPermissions.ITEMINFO_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Inspect the item you are holding"));
        verbs.add(new Verb(
                "copyinv",
                ItemworldPermissions.COPYINV_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Copy a player's inventory into yours"));
        verbs.add(new Verb(
                "endercopy",
                ItemworldPermissions.ENDERCOPY_USE,
                SubFeatureGroup.ITEM_UTILS,
                "Copy a player's ender chest into yours"));
        // Virtual workstations.
        verbs.add(new Verb(
                "anvil", ItemworldPermissions.WORKSTATION_ANVIL, SubFeatureGroup.WORKSTATIONS, "Open a virtual anvil"));
        verbs.add(new Verb(
                "workbench",
                ItemworldPermissions.WORKSTATION_WORKBENCH,
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual crafting table"));
        verbs.add(new Verb(
                "enderchest",
                ItemworldPermissions.WORKSTATION_ENDERCHEST,
                SubFeatureGroup.WORKSTATIONS,
                "Open your ender chest"));
        verbs.add(new Verb(
                "grindstone",
                ItemworldPermissions.WORKSTATION_GRINDSTONE,
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual grindstone"));
        verbs.add(new Verb(
                "cartography",
                ItemworldPermissions.WORKSTATION_CARTOGRAPHY,
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual cartography table"));
        verbs.add(new Verb(
                "loom", ItemworldPermissions.WORKSTATION_LOOM, SubFeatureGroup.WORKSTATIONS, "Open a virtual loom"));
        verbs.add(new Verb(
                "smithingtable",
                ItemworldPermissions.WORKSTATION_SMITHINGTABLE,
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual smithing table"));
        verbs.add(new Verb(
                "stonecutter",
                ItemworldPermissions.WORKSTATION_STONECUTTER,
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual stonecutter"));
        verbs.add(new Verb(
                "furnace",
                ItemworldPermissions.WORKSTATION_FURNACE,
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual furnace"));
        // Cleanup.
        verbs.add(new Verb(
                "disposal", ItemworldPermissions.DISPOSAL_USE, SubFeatureGroup.CLEANUP, "Open a throwaway GUI"));
        verbs.add(new Verb(
                "condense",
                ItemworldPermissions.CONDENSE_USE,
                SubFeatureGroup.CLEANUP,
                "Recipe-stack inventory items"));
        verbs.add(new Verb(
                "enderclear", ItemworldPermissions.ENDERCLEAR_USE, SubFeatureGroup.CLEANUP, "Clear an ender chest"));
        // Powertool.
        verbs.add(new Verb(
                "powertool",
                ItemworldPermissions.POWERTOOL_USE,
                SubFeatureGroup.POWERTOOL,
                "Bind a command to the held item"));
        verbs.add(new Verb(
                "powertooltoggle",
                ItemworldPermissions.POWERTOOL_TOGGLE,
                SubFeatureGroup.POWERTOOL,
                "Toggle your powertool bindings"));
        verbs.add(new Verb(
                "powertoollist",
                ItemworldPermissions.POWERTOOL_USE,
                SubFeatureGroup.POWERTOOL,
                "List the held item's powertool bindings"));
        // Mob & entity.
        verbs.add(new Verb("spawnmob", ItemworldPermissions.SPAWNMOB_USE, SubFeatureGroup.MOB_ENTITY, "Spawn mobs"));
        verbs.add(new Verb(
                "spawner", ItemworldPermissions.SPAWNER_USE, SubFeatureGroup.MOB_ENTITY, "Set a spawner's mob type"));
        verbs.add(new Verb("kill", ItemworldPermissions.KILL_USE, SubFeatureGroup.MOB_ENTITY, "Kill a target"));
        verbs.add(
                new Verb("butcher", ItemworldPermissions.BUTCHER_USE, SubFeatureGroup.MOB_ENTITY, "Purge nearby mobs"));
        verbs.add(new Verb(
                "killall", ItemworldPermissions.KILLALL_USE, SubFeatureGroup.MOB_ENTITY, "Purge entities world-wide"));
        verbs.add(new Verb(
                "remove", ItemworldPermissions.REMOVE_USE, SubFeatureGroup.MOB_ENTITY, "Remove entities by type"));
        verbs.add(new Verb(
                "entitycount",
                ItemworldPermissions.ENTITYCOUNT_USE,
                SubFeatureGroup.MOB_ENTITY,
                "Count nearby entities by type"));
        verbs.add(new Verb(
                "unlimited",
                ItemworldPermissions.UNLIMITED_USE,
                SubFeatureGroup.MOB_ENTITY,
                "Toggle unlimited block placement"));
        // Time & weather (core verbs plus the alias verbs in the same group).
        verbs.add(
                new Verb("time", ItemworldPermissions.TIME_USE, SubFeatureGroup.TIME_WEATHER, "Set or add world time"));
        verbs.add(
                new Verb("weather", ItemworldPermissions.WEATHER_USE, SubFeatureGroup.TIME_WEATHER, "Set the weather"));
        verbs.add(
                new Verb("day", ItemworldPermissions.TIME_ALIAS, SubFeatureGroup.TIME_WEATHER, "Set the time to day"));
        verbs.add(new Verb(
                "night", ItemworldPermissions.TIME_ALIAS, SubFeatureGroup.TIME_WEATHER, "Set the time to night"));
        verbs.add(
                new Verb("sun", ItemworldPermissions.WEATHER_ALIAS, SubFeatureGroup.TIME_WEATHER, "Clear the weather"));
        verbs.add(new Verb("rain", ItemworldPermissions.WEATHER_ALIAS, SubFeatureGroup.TIME_WEATHER, "Start rain"));
        verbs.add(new Verb(
                "thunder", ItemworldPermissions.WEATHER_ALIAS, SubFeatureGroup.TIME_WEATHER, "Start a thunderstorm"));
        // Admin-fun.
        verbs.add(new Verb(
                "lightning", ItemworldPermissions.LIGHTNING_USE, SubFeatureGroup.ADMIN_FUN, "Strike lightning"));
        verbs.add(new Verb(
                "fireball", ItemworldPermissions.FIREBALL_USE, SubFeatureGroup.ADMIN_FUN, "Launch a fireball"));
        verbs.add(new Verb(
                "kittycannon",
                ItemworldPermissions.KITTYCANNON_USE,
                SubFeatureGroup.ADMIN_FUN,
                "Launch an exploding cat"));
        verbs.add(new Verb(
                "antioch", ItemworldPermissions.ANTIOCH_USE, SubFeatureGroup.ADMIN_FUN, "Throw a primed TNT grenade"));
        verbs.add(new Verb(
                "beezooka", ItemworldPermissions.BEEZOOKA_USE, SubFeatureGroup.ADMIN_FUN, "Launch an angry bee"));
        verbs.add(new Verb(
                "break",
                ItemworldPermissions.BREAK_USE,
                SubFeatureGroup.ADMIN_FUN,
                "Break the block you are looking at"));
        verbs.add(new Verb(
                "tree",
                ItemworldPermissions.TREE_USE,
                SubFeatureGroup.ADMIN_FUN,
                "Generate a tree where you are looking"));
        verbs.add(new Verb(
                "bigtree",
                ItemworldPermissions.TREE_USE,
                SubFeatureGroup.ADMIN_FUN,
                "Generate a large tree where you are looking"));
        verbs.add(new Verb(
                "nuke", ItemworldPermissions.NUKE_USE, SubFeatureGroup.ADMIN_FUN, "Rain lightning over an area"));
        return List.copyOf(verbs);
    }

    /** One itemworld command: literal, permission node, owning sub-feature group, and help text. */
    private record Verb(String literal, String permission, SubFeatureGroup group, String description) {
        Verb {
            Objects.requireNonNull(literal, "literal");
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(description, "description");
        }

        CommandSpec toSpec() {
            Function<ModuleContext, BrigadierCommand> factory = ctx -> new ItemworldCommand(literal, description);
            return new CommandSpec(literal, permission, factory);
        }
    }

    /** The kernel-side description of one itemworld command, literal and help text, no Brigadier type. */
    private record ItemworldCommand(String literal, String description) implements BrigadierCommand {}
}
