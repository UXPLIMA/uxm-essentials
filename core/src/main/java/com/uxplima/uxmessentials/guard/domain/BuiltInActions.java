package com.uxplima.uxmessentials.guard.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * The actions the guard ships with, as constants the listeners name.
 *
 * <p>Player actions are decided for the player who did them. The {@code world.*} family has no actor: nature
 * does it, so only {@code everyone} rules and the border test apply. When a player starts a world action (lights
 * a fire, primes TNT) both are checked.
 */
public final class BuiltInActions {

    private BuiltInActions() {}

    public static final ActionId BREAK = id("break");
    public static final ActionId PLACE = id("place");

    public static final ActionId USE = id("use");
    public static final ActionId USE_DOOR = id("use.door");
    public static final ActionId USE_TRAPDOOR = id("use.trapdoor");
    public static final ActionId USE_GATE = id("use.gate");
    public static final ActionId USE_BUTTON = id("use.button");
    public static final ActionId USE_LEVER = id("use.lever");
    public static final ActionId USE_PRESSURE_PLATE = id("use.pressure-plate");
    public static final ActionId USE_CONTAINER = id("use.container");
    public static final ActionId USE_WORKSTATION = id("use.workstation");
    public static final ActionId USE_BED = id("use.bed");
    public static final ActionId USE_RESPAWN_ANCHOR = id("use.respawn-anchor");
    public static final ActionId USE_REDSTONE = id("use.redstone");
    public static final ActionId USE_SIGN = id("use.sign");
    public static final ActionId USE_LECTERN = id("use.lectern");
    public static final ActionId USE_CAKE = id("use.cake");
    public static final ActionId USE_BEACON = id("use.beacon");
    public static final ActionId USE_JUKEBOX = id("use.jukebox");
    public static final ActionId USE_COMPOSTER = id("use.composter");
    public static final ActionId USE_CAULDRON = id("use.cauldron");
    public static final ActionId USE_BELL = id("use.bell");
    public static final ActionId USE_OTHER = id("use.other");

    public static final ActionId INTERACT = id("interact");
    public static final ActionId INTERACT_VILLAGER = id("interact.villager");
    public static final ActionId INTERACT_ANIMAL = id("interact.animal");
    public static final ActionId INTERACT_ARMOR_STAND = id("interact.armor-stand");
    public static final ActionId INTERACT_ITEM_FRAME = id("interact.item-frame");
    public static final ActionId INTERACT_VEHICLE = id("interact.vehicle");
    public static final ActionId INTERACT_OTHER = id("interact.other");

    public static final ActionId DAMAGE = id("damage");
    public static final ActionId DAMAGE_PLAYER = id("damage.player");
    public static final ActionId DAMAGE_ANIMAL = id("damage.animal");
    public static final ActionId DAMAGE_MONSTER = id("damage.monster");
    public static final ActionId DAMAGE_VILLAGER = id("damage.villager");
    public static final ActionId DAMAGE_ARMOR_STAND = id("damage.armor-stand");
    public static final ActionId DAMAGE_HANGING = id("damage.hanging");
    public static final ActionId DAMAGE_VEHICLE = id("damage.vehicle");
    public static final ActionId DAMAGE_OTHER = id("damage.other");

    public static final ActionId ITEM_DROP = id("item.drop");
    public static final ActionId ITEM_PICKUP = id("item.pickup");
    public static final ActionId ITEM_USE = id("item.use");
    public static final ActionId ITEM_USE_ENDER_PEARL = id("item.use.ender-pearl");
    public static final ActionId ITEM_USE_CHORUS_FRUIT = id("item.use.chorus-fruit");
    public static final ActionId ITEM_USE_FIREWORK = id("item.use.firework");
    public static final ActionId ITEM_USE_POTION = id("item.use.potion");
    public static final ActionId ITEM_USE_WIND_CHARGE = id("item.use.wind-charge");
    public static final ActionId ITEM_USE_BUCKET = id("item.use.bucket");
    public static final ActionId ITEM_USE_SPAWN_EGG = id("item.use.spawn-egg");
    public static final ActionId ITEM_USE_BONE_MEAL = id("item.use.bone-meal");
    public static final ActionId ITEM_USE_LIGHTER = id("item.use.lighter");
    public static final ActionId ITEM_USE_OTHER = id("item.use.other");

    public static final ActionId VEHICLE_PLACE = id("vehicle.place");
    public static final ActionId VEHICLE_DESTROY = id("vehicle.destroy");
    public static final ActionId HANGING_PLACE = id("hanging.place");
    public static final ActionId HANGING_BREAK = id("hanging.break");

    public static final ActionId TRAMPLE_CROP = id("trample.crop");
    public static final ActionId TRAMPLE_TURTLE_EGG = id("trample.turtle-egg");
    public static final ActionId TRAMPLE_DRIPLEAF = id("trample.dripleaf");

    public static final ActionId MOVE_ENTER = id("move.enter");
    public static final ActionId MOVE_EXIT = id("move.exit");
    public static final ActionId MOVE_TELEPORT_IN = id("move.teleport-in");
    public static final ActionId MOVE_TELEPORT_OUT = id("move.teleport-out");

    public static final ActionId WORLD_EXPLOSION_CREEPER = id("world.explosion.creeper");
    public static final ActionId WORLD_EXPLOSION_TNT = id("world.explosion.tnt");
    public static final ActionId WORLD_EXPLOSION_WITHER = id("world.explosion.wither");
    public static final ActionId WORLD_EXPLOSION_GHAST = id("world.explosion.ghast");
    public static final ActionId WORLD_EXPLOSION_BED = id("world.explosion.bed");
    public static final ActionId WORLD_EXPLOSION_CRYSTAL = id("world.explosion.crystal");
    public static final ActionId WORLD_EXPLOSION_BREEZE = id("world.explosion.breeze");
    public static final ActionId WORLD_EXPLOSION_OTHER = id("world.explosion.other");

    public static final ActionId WORLD_FIRE_SPREAD = id("world.fire.spread");
    public static final ActionId WORLD_FIRE_BURN = id("world.fire.burn");
    public static final ActionId WORLD_FIRE_LAVA = id("world.fire.lava");
    public static final ActionId WORLD_FIRE_LIGHTNING = id("world.fire.lightning");

    public static final ActionId WORLD_LIQUID_WATER = id("world.liquid.water");
    public static final ActionId WORLD_LIQUID_LAVA = id("world.liquid.lava");

    public static final ActionId WORLD_PISTON = id("world.piston");
    public static final ActionId WORLD_DISPENSER = id("world.dispenser");
    public static final ActionId WORLD_HOPPER = id("world.hopper");

    public static final ActionId WORLD_GROW_TREE = id("world.grow.tree");
    public static final ActionId WORLD_GROW_CROP = id("world.grow.crop");
    public static final ActionId WORLD_GROW_VINE = id("world.grow.vine");
    public static final ActionId WORLD_GROW_SCULK = id("world.grow.sculk");
    public static final ActionId WORLD_GROW_AMETHYST = id("world.grow.amethyst");
    public static final ActionId WORLD_GROW_OTHER = id("world.grow.other");

    public static final ActionId WORLD_SPREAD = id("world.spread");
    public static final ActionId WORLD_DECAY = id("world.decay");
    public static final ActionId WORLD_FORM = id("world.form");
    public static final ActionId WORLD_MELT = id("world.melt");
    public static final ActionId WORLD_FADE = id("world.fade");

    public static final ActionId WORLD_MOB_ENDERMAN = id("world.mob.enderman");
    public static final ActionId WORLD_MOB_RAVAGER = id("world.mob.ravager");
    public static final ActionId WORLD_MOB_WITHER = id("world.mob.wither");
    public static final ActionId WORLD_MOB_SILVERFISH = id("world.mob.silverfish");
    public static final ActionId WORLD_MOB_ZOMBIE_DOOR = id("world.mob.zombie-door");
    public static final ActionId WORLD_MOB_SNOW_GOLEM = id("world.mob.snow-golem");
    public static final ActionId WORLD_MOB_OTHER = id("world.mob.other");

    public static final ActionId WORLD_PORTAL = id("world.portal");
    public static final ActionId WORLD_DRAGON_EGG = id("world.dragon-egg");
    public static final ActionId WORLD_LIGHTNING = id("world.lightning");

    /** Every built-in leaf; the tree adds the intermediate nodes. */
    public static final List<ActionId> ALL = Stream.of(
                    BREAK,
                    PLACE,
                    USE,
                    USE_DOOR,
                    USE_TRAPDOOR,
                    USE_GATE,
                    USE_BUTTON,
                    USE_LEVER,
                    USE_PRESSURE_PLATE,
                    USE_CONTAINER,
                    USE_WORKSTATION,
                    USE_BED,
                    USE_RESPAWN_ANCHOR,
                    USE_REDSTONE,
                    USE_SIGN,
                    USE_LECTERN,
                    USE_CAKE,
                    USE_BEACON,
                    USE_JUKEBOX,
                    USE_COMPOSTER,
                    USE_CAULDRON,
                    USE_BELL,
                    USE_OTHER,
                    INTERACT,
                    INTERACT_VILLAGER,
                    INTERACT_ANIMAL,
                    INTERACT_ARMOR_STAND,
                    INTERACT_ITEM_FRAME,
                    INTERACT_VEHICLE,
                    INTERACT_OTHER,
                    DAMAGE,
                    DAMAGE_PLAYER,
                    DAMAGE_ANIMAL,
                    DAMAGE_MONSTER,
                    DAMAGE_VILLAGER,
                    DAMAGE_ARMOR_STAND,
                    DAMAGE_HANGING,
                    DAMAGE_VEHICLE,
                    DAMAGE_OTHER,
                    ITEM_DROP,
                    ITEM_PICKUP,
                    ITEM_USE,
                    ITEM_USE_ENDER_PEARL,
                    ITEM_USE_CHORUS_FRUIT,
                    ITEM_USE_FIREWORK,
                    ITEM_USE_POTION,
                    ITEM_USE_WIND_CHARGE,
                    ITEM_USE_BUCKET,
                    ITEM_USE_SPAWN_EGG,
                    ITEM_USE_BONE_MEAL,
                    ITEM_USE_LIGHTER,
                    ITEM_USE_OTHER,
                    VEHICLE_PLACE,
                    VEHICLE_DESTROY,
                    HANGING_PLACE,
                    HANGING_BREAK,
                    TRAMPLE_CROP,
                    TRAMPLE_TURTLE_EGG,
                    TRAMPLE_DRIPLEAF,
                    MOVE_ENTER,
                    MOVE_EXIT,
                    MOVE_TELEPORT_IN,
                    MOVE_TELEPORT_OUT,
                    WORLD_EXPLOSION_CREEPER,
                    WORLD_EXPLOSION_TNT,
                    WORLD_EXPLOSION_WITHER,
                    WORLD_EXPLOSION_GHAST,
                    WORLD_EXPLOSION_BED,
                    WORLD_EXPLOSION_CRYSTAL,
                    WORLD_EXPLOSION_BREEZE,
                    WORLD_EXPLOSION_OTHER,
                    WORLD_FIRE_SPREAD,
                    WORLD_FIRE_BURN,
                    WORLD_FIRE_LAVA,
                    WORLD_FIRE_LIGHTNING,
                    WORLD_LIQUID_WATER,
                    WORLD_LIQUID_LAVA,
                    WORLD_PISTON,
                    WORLD_DISPENSER,
                    WORLD_HOPPER,
                    WORLD_GROW_TREE,
                    WORLD_GROW_CROP,
                    WORLD_GROW_VINE,
                    WORLD_GROW_SCULK,
                    WORLD_GROW_AMETHYST,
                    WORLD_GROW_OTHER,
                    WORLD_SPREAD,
                    WORLD_DECAY,
                    WORLD_FORM,
                    WORLD_MELT,
                    WORLD_FADE,
                    WORLD_MOB_ENDERMAN,
                    WORLD_MOB_RAVAGER,
                    WORLD_MOB_WITHER,
                    WORLD_MOB_SILVERFISH,
                    WORLD_MOB_ZOMBIE_DOOR,
                    WORLD_MOB_SNOW_GOLEM,
                    WORLD_MOB_OTHER,
                    WORLD_PORTAL,
                    WORLD_DRAGON_EGG,
                    WORLD_LIGHTNING)
            .toList();

    private static ActionId id(String value) {
        return new ActionId(value);
    }
}
