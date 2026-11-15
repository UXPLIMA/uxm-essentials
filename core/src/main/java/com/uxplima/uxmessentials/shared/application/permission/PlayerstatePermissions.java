package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the playerstate context. Data, not logic: one row per node, read by {@link
 * PermissionCatalog} and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class PlayerstatePermissions {

    private static final ModuleId PLAYERSTATE = ModuleId.of("playerstate");

    public static final String AIR_USE = "uxmessentials.air.use";
    public static final String BURN_USE = "uxmessentials.burn.use";
    public static final String EXP_USE = "uxmessentials.exp.use";
    public static final String EXTINGUISH_USE = "uxmessentials.extinguish.use";
    public static final String FEED_USE = "uxmessentials.feed.use";
    public static final String FLY_USE = "uxmessentials.fly.use";
    public static final String FOODLEVEL_USE = "uxmessentials.foodlevel.use";
    public static final String GAMEMODE_USE = "uxmessentials.gamemode.use";
    public static final String GLOW_COLOR = "uxmessentials.glow.color";
    public static final String GLOW_USE = "uxmessentials.glow.use";
    public static final String GOD_USE = "uxmessentials.god.use";
    public static final String HEAL_USE = "uxmessentials.heal.use";
    public static final String HEALTH_USE = "uxmessentials.health.use";
    public static final String ICE_USE = "uxmessentials.ice.use";
    public static final String NIGHTVISION_USE = "uxmessentials.nightvision.use";
    public static final String PLAYERSTATE_FLY_ALLWORLDS = "uxmessentials.playerstate.fly.allworlds";
    public static final String PTIME_USE = "uxmessentials.ptime.use";
    public static final String PWEATHER_USE = "uxmessentials.pweather.use";
    public static final String REST_USE = "uxmessentials.rest.use";
    public static final String SPEED_USE = "uxmessentials.speed.use";
    public static final String SUICIDE_USE = "uxmessentials.suicide.use";
    public static final String BIOME_USE = "uxmessentials.biome.use";
    public static final String DEPTH_USE = "uxmessentials.depth.use";
    public static final String DIMENSION_USE = "uxmessentials.dimension.use";
    public static final String ENDERSEE_USE = "uxmessentials.endersee.use";
    public static final String GETPOS_USE = "uxmessentials.getpos.use";
    public static final String INVSEE_MODIFY = "uxmessentials.invsee.modify";
    public static final String INVSEE_USE = "uxmessentials.invsee.use";
    public static final String NEAR_USE = "uxmessentials.near.use";
    public static final String PING_USE = "uxmessentials.ping.use";
    public static final String PLAYTIME_IMPORT = "uxmessentials.playtime.import";
    public static final String PLAYTIME_RESET = "uxmessentials.playtime.reset";
    public static final String PLAYTIME_SET = "uxmessentials.playtime.set";
    public static final String PLAYTIME_USE = "uxmessentials.playtime.use";
    public static final String SEED_USE = "uxmessentials.seed.use";
    public static final String WORLD_COMMAND_BYPASS = "uxmessentials.bypass.blocked-commands.world";
    public static final String WORLD_USE = "uxmessentials.world.use";
    public static final String CLEARINVENTORY_CONFIRMTOGGLE = "uxmessentials.clearinventory.confirmtoggle";
    public static final String CLEARINVENTORY_USE = "uxmessentials.clearinventory.use";
    public static final String COMPASS_USE = "uxmessentials.compass.use";
    public static final String MODULE_PLAYERSTATE = "uxmessentials.module.playerstate";
    public static final String AIR_OTHERS = "uxmessentials.air.others";
    public static final String BURN_OTHERS = "uxmessentials.burn.others";
    public static final String CLEARINVENTORY_OTHERS = "uxmessentials.clearinventory.others";
    public static final String ENDERSEE_OTHERS = "uxmessentials.endersee.others";
    public static final String EXP_OTHERS = "uxmessentials.exp.others";
    public static final String EXTINGUISH_OTHERS = "uxmessentials.extinguish.others";
    public static final String FEED_OTHERS = "uxmessentials.feed.others";
    public static final String FLY_OTHERS = "uxmessentials.fly.others";
    public static final String FOODLEVEL_OTHERS = "uxmessentials.foodlevel.others";
    public static final String GAMEMODE_OTHERS = "uxmessentials.gamemode.others";
    public static final String GETPOS_OTHERS = "uxmessentials.getpos.others";
    public static final String GLOW_OTHERS = "uxmessentials.glow.others";
    public static final String GOD_OTHERS = "uxmessentials.god.others";
    public static final String HEAL_OTHERS = "uxmessentials.heal.others";
    public static final String HEALTH_OTHERS = "uxmessentials.health.others";
    public static final String ICE_OTHERS = "uxmessentials.ice.others";
    public static final String INVSEE_OTHERS = "uxmessentials.invsee.others";
    public static final String NIGHTVISION_OTHERS = "uxmessentials.nightvision.others";
    public static final String PING_OTHERS = "uxmessentials.ping.others";
    public static final String PLAYTIME_OTHERS = "uxmessentials.playtime.others";
    public static final String REST_OTHERS = "uxmessentials.rest.others";
    public static final String SPEED_OTHERS = "uxmessentials.speed.others";
    public static final String PLAYERSTATE_OTHERS = "uxmessentials.playerstate.others";

    private PlayerstatePermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(playerstateSelf(), playerstateInspection(), playerstateAdministration())
                .flatMap(List::stream)
                .toList();
    }

    /** The verbs a player points at their own body. */
    private static List<PermissionSpec> playerstateSelf() {
        return List.of(
                PermissionSpec.of(
                        AIR_USE, "/air <seconds> to set a player's remaining air.", PermissionDefault.OP, PLAYERSTATE),
                PermissionSpec.of(
                        BURN_USE, "/burn <seconds> to set a player on fire.", PermissionDefault.OP, PLAYERSTATE),
                PermissionSpec.of(
                        EXP_USE,
                        "/exp (/xp) get|set|give|take|reset to read or change experience.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        EXTINGUISH_USE,
                        "/ext (/extinguish) [player] to put out a burning player.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(FEED_USE, "/feed [player] to restore hunger.", PermissionDefault.OP, PLAYERSTATE),
                PermissionSpec.of(FLY_USE, "/fly [player] to toggle flight.", PermissionDefault.OP, PLAYERSTATE),
                PermissionSpec.of(
                        FOODLEVEL_USE,
                        "/foodlevel <amount> [player] to set a player's food level.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GAMEMODE_USE,
                        "/gamemode <mode> [player] and the /gmc /gms /gma /gmsp aliases.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GLOW_COLOR,
                        "/glow <color> to pick the colour of the outline. Without it, /glow still toggles the plain one.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GLOW_USE,
                        "/glow [color] [player] to toggle a glowing outline.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GOD_USE, "/god [player] to toggle damage immunity.", PermissionDefault.OP, PLAYERSTATE),
                PermissionSpec.of(HEAL_USE, "/heal [player] to restore health.", PermissionDefault.OP, PLAYERSTATE),
                PermissionSpec.of(
                        HEALTH_USE,
                        "/health <amount> [player] to set a player's health.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        ICE_USE,
                        "/ice [seconds] [player] to freeze a player (inverse of /burn).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        NIGHTVISION_USE,
                        "/nightvision (/nv) [player] to toggle a night-vision effect.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PLAYERSTATE_FLY_ALLWORLDS,
                        "Keep flight in worlds where flying is switched off.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PTIME_USE,
                        "/ptime <value|reset> to set a per-player client-side time.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PWEATHER_USE,
                        "/pweather <clear|rain|reset> to set a per-player client-side weather.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        REST_USE,
                        "/rest to reset a player's time-since-rest so phantoms stop.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        SPEED_USE,
                        "/speed, /walkspeed and /flyspeed to set walk/fly speed.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(SUICIDE_USE, "/suicide to kill yourself.", PermissionDefault.TRUE, PLAYERSTATE));
    }

    /** The verbs that read a player or the ground under them. */
    private static List<PermissionSpec> playerstateInspection() {
        return List.of(
                PermissionSpec.of(
                        BIOME_USE,
                        "/biome to show the biome you are standing in.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        DEPTH_USE,
                        "/depth to show your height relative to sea level.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        DIMENSION_USE,
                        "/dimension to show the dimension you are standing in.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        ENDERSEE_USE,
                        "/endersee [player] to view a player's ender chest.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GETPOS_USE,
                        "/getpos (/coords /whereami) to show a player's coordinates.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        INVSEE_MODIFY,
                        "Edit a player's inventory through the /invsee menu (without this it is view-only).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        INVSEE_USE,
                        "/invsee [player] to view a player's inventory.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        NEAR_USE, "/near [radius] to list nearby players.", PermissionDefault.OP, PLAYERSTATE),
                PermissionSpec.of(
                        PING_USE, "/ping to show a player's round-trip latency.", PermissionDefault.TRUE, PLAYERSTATE),
                PermissionSpec.of(
                        PLAYTIME_IMPORT,
                        "/playtime import <player>|all to bring the server's vanilla playtime into the tracked total (naming another player also needs uxmessentials.playerstate.others).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PLAYTIME_RESET,
                        "/playtime reset [player] to wipe a player's tracked playtime (resetting others also needs uxmessentials.playerstate.others).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PLAYTIME_SET,
                        "/playtime set|add|take <player> <time> to correct a player's all-time playtime (naming another player also needs uxmessentials.playerstate.others).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PLAYTIME_USE,
                        "/playtime [player] to show a player's playtime breakdown (active/afk, today/week/month/all-time).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        SEED_USE,
                        "/seed to show the seed of the world you are standing in.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        WORLD_COMMAND_BYPASS,
                        "Run commands a world blocks through its per-world command list.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        WORLD_USE,
                        "/world to show the world you are standing in, and /worlds to reach the world manager.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE));
    }

    /** The verbs that reach somebody else, and the exemptions. */
    private static List<PermissionSpec> playerstateAdministration() {
        return List.of(
                PermissionSpec.of(
                        CLEARINVENTORY_CONFIRMTOGGLE,
                        "/clearinventoryconfirmtoggle (/citoggle) to require a confirmation before /clearinventory clears your own inventory.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        CLEARINVENTORY_USE,
                        "/clearinventory (/ci /clear) [player] to empty a player's inventory.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        COMPASS_USE,
                        "/compass to show the direction you are facing.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        MODULE_PLAYERSTATE,
                        "Hot-reload / inspect the playerstate module (flight, god mode, speed, health and the rest).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        AIR_OTHERS,
                        "/air on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        BURN_OTHERS,
                        "/burn on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        CLEARINVENTORY_OTHERS,
                        "/clearinventory on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        ENDERSEE_OTHERS,
                        "/endersee to open another player ender chest. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        EXP_OTHERS,
                        "/exp on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        EXTINGUISH_OTHERS,
                        "/extinguish on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        FEED_OTHERS,
                        "/feed on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        FLY_OTHERS,
                        "/fly on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        FOODLEVEL_OTHERS,
                        "/foodlevel on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GAMEMODE_OTHERS,
                        "/gamemode on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GETPOS_OTHERS,
                        "/getpos on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GLOW_OTHERS,
                        "/glow on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        GOD_OTHERS,
                        "/god on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        HEAL_OTHERS,
                        "/heal on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        HEALTH_OTHERS,
                        "/health on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        ICE_OTHERS,
                        "/ice on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        INVSEE_OTHERS,
                        "/invsee to open another player inventory. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        NIGHTVISION_OTHERS,
                        "/nightvision on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PING_OTHERS,
                        "/ping on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PLAYTIME_OTHERS,
                        "/playtime on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        REST_OTHERS,
                        "/rest on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        SPEED_OTHERS,
                        "/speed on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        PLAYERSTATE_OTHERS,
                        "Use any playerstate command with a [player] target other than yourself.",
                        PermissionDefault.OP,
                        PLAYERSTATE));
    }
}
