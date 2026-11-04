package com.uxplima.uxmessentials.guard.adapter;

import static com.uxplima.uxmessentials.guard.domain.BuiltInActions.*;

import com.uxplima.uxmessentials.guard.domain.ActionId;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WaterMob;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Ambient;

/**
 * Which action a block, item or entity stands for. Anything not named falls into the {@code other} leaf of its
 * family, so a block or mob added by a new Minecraft version is protected as a change rather than let through.
 */
public final class ActionKinds {

    private ActionKinds() {}

    /** The action a right click on {@code block} is, for an interactable block. */
    public static ActionId use(Block block) {
        Material type = block.getType();
        if (Tag.DOORS.isTagged(type)) {
            return USE_DOOR;
        }
        if (Tag.TRAPDOORS.isTagged(type)) {
            return USE_TRAPDOOR;
        }
        if (Tag.FENCE_GATES.isTagged(type)) {
            return USE_GATE;
        }
        if (Tag.BUTTONS.isTagged(type)) {
            return USE_BUTTON;
        }
        if (type == Material.LEVER) {
            return USE_LEVER;
        }
        if (Tag.PRESSURE_PLATES.isTagged(type)) {
            return USE_PRESSURE_PLATE;
        }
        if (Tag.BEDS.isTagged(type)) {
            return USE_BED;
        }
        if (type == Material.RESPAWN_ANCHOR) {
            return USE_RESPAWN_ANCHOR;
        }
        if (Tag.ALL_SIGNS.isTagged(type)) {
            return USE_SIGN;
        }
        if (type == Material.LECTERN) {
            return USE_LECTERN;
        }
        if (Tag.CANDLE_CAKES.isTagged(type) || type == Material.CAKE) {
            return USE_CAKE;
        }
        if (type == Material.BEACON) {
            return USE_BEACON;
        }
        if (type == Material.JUKEBOX || type == Material.NOTE_BLOCK) {
            return USE_JUKEBOX;
        }
        if (type == Material.COMPOSTER) {
            return USE_COMPOSTER;
        }
        if (Tag.CAULDRONS.isTagged(type)) {
            return USE_CAULDRON;
        }
        if (type == Material.BELL) {
            return USE_BELL;
        }
        if (type == Material.REPEATER || type == Material.COMPARATOR || type == Material.DAYLIGHT_DETECTOR) {
            return USE_REDSTONE;
        }
        if (workstation(type)) {
            return USE_WORKSTATION;
        }
        BlockState state = block.getState(false);
        if (state instanceof Container || type == Material.CHISELED_BOOKSHELF || type == Material.DECORATED_POT
                || Tag.ITEMS_BOOKSHELF_BOOKS.isTagged(type) || type == Material.ENDER_CHEST) {
            return type == Material.ENDER_CHEST ? USE_OTHER : USE_CONTAINER;
        }
        return USE_OTHER;
    }

    private static boolean workstation(Material type) {
        return switch (type) {
            case CRAFTING_TABLE, CRAFTER, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, ENCHANTING_TABLE, SMITHING_TABLE,
                    LOOM, GRINDSTONE, STONECUTTER, CARTOGRAPHY_TABLE, FLETCHING_TABLE -> true;
            default -> false;
        };
    }

    /** The action a right click on an entity is. */
    public static ActionId interact(Entity entity) {
        if (entity instanceof AbstractVillager) {
            return INTERACT_VILLAGER;
        }
        if (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Ambient) {
            return INTERACT_ANIMAL;
        }
        if (entity instanceof ArmorStand) {
            return INTERACT_ARMOR_STAND;
        }
        if (entity instanceof ItemFrame) {
            return INTERACT_ITEM_FRAME;
        }
        if (entity instanceof Vehicle) {
            return INTERACT_VEHICLE;
        }
        return INTERACT_OTHER;
    }

    /** The action hurting an entity is. */
    public static ActionId damage(Entity victim) {
        if (victim instanceof Player) {
            return DAMAGE_PLAYER;
        }
        if (victim instanceof AbstractVillager) {
            return DAMAGE_VILLAGER;
        }
        if (victim instanceof Enemy) {
            return DAMAGE_MONSTER;
        }
        if (victim instanceof Animals || victim instanceof WaterMob || victim instanceof Ambient) {
            return DAMAGE_ANIMAL;
        }
        if (victim instanceof ArmorStand) {
            return DAMAGE_ARMOR_STAND;
        }
        if (victim instanceof Hanging) {
            return DAMAGE_HANGING;
        }
        if (victim instanceof Vehicle) {
            return DAMAGE_VEHICLE;
        }
        return DAMAGE_OTHER;
    }

    /** The explosion action for whatever blew up. */
    public static ActionId explosion(Entity source) {
        if (source instanceof Creeper) {
            return WORLD_EXPLOSION_CREEPER;
        }
        if (source instanceof TNTPrimed || source.getType().name().contains("TNT")) {
            return WORLD_EXPLOSION_TNT;
        }
        if (source instanceof Wither || source instanceof WitherSkull) {
            return WORLD_EXPLOSION_WITHER;
        }
        if (source instanceof Fireball) {
            return WORLD_EXPLOSION_GHAST;
        }
        if (source instanceof EnderCrystal) {
            return WORLD_EXPLOSION_CRYSTAL;
        }
        if (source instanceof AbstractWindCharge) {
            return WORLD_EXPLOSION_BREEZE;
        }
        return WORLD_EXPLOSION_OTHER;
    }

    /** The item action for using {@code item} on nothing in particular or on a block. */
    public static ActionId item(Material item) {
        if (item == Material.ENDER_PEARL) {
            return ITEM_USE_ENDER_PEARL;
        }
        if (item == Material.CHORUS_FRUIT) {
            return ITEM_USE_CHORUS_FRUIT;
        }
        if (item == Material.FIREWORK_ROCKET) {
            return ITEM_USE_FIREWORK;
        }
        if (item == Material.SPLASH_POTION || item == Material.LINGERING_POTION) {
            return ITEM_USE_POTION;
        }
        if (item == Material.WIND_CHARGE) {
            return ITEM_USE_WIND_CHARGE;
        }
        if (item == Material.BUCKET || item.name().endsWith("_BUCKET")) {
            return ITEM_USE_BUCKET;
        }
        if (item.name().endsWith("_SPAWN_EGG")) {
            return ITEM_USE_SPAWN_EGG;
        }
        if (item == Material.BONE_MEAL) {
            return ITEM_USE_BONE_MEAL;
        }
        if (item == Material.FLINT_AND_STEEL || item == Material.FIRE_CHARGE) {
            return ITEM_USE_LIGHTER;
        }
        return ITEM_USE_OTHER;
    }

    /** True for items whose right click changes the world on their own (handled as item actions). */
    public static boolean worldChangingItem(Material item) {
        return item.name().endsWith("_SPAWN_EGG") || item == Material.BONE_MEAL || item == Material.FIREWORK_ROCKET
                || item == Material.FLINT_AND_STEEL || item == Material.FIRE_CHARGE
                || item == Material.ARMOR_STAND || item == Material.END_CRYSTAL || item == Material.ITEM_FRAME
                || item == Material.GLOW_ITEM_FRAME || item == Material.PAINTING;
    }
}
