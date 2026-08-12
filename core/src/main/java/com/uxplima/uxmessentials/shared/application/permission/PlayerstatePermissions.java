package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the playerstate context. Data, not logic: one row per node, read by {@link
 * PermissionCatalog} and through it by the server registration, the reference page and the in-game listing.
 */
final class PlayerstatePermissions {

    private static final ModuleId PLAYERSTATE = ModuleId.of("playerstate");

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
                        "uxmessentials.air.use",
                        "/air <seconds> to set a player's remaining air.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.burn.use",
                        "/burn <seconds> to set a player on fire.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.exp.use",
                        "/exp (/xp) get|set|give|take|reset to read or change experience.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.extinguish.use",
                        "/ext (/extinguish) [player] to put out a burning player.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.feed.use",
                        "/feed [player] to restore hunger.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.fly.use", "/fly [player] to toggle flight.", PermissionDefault.OP, PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.foodlevel.use",
                        "/foodlevel <amount> [player] to set a player's food level.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.gamemode.use",
                        "/gamemode <mode> [player] and the /gmc /gms /gma /gmsp aliases.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.glow.use",
                        "/glow to toggle a glowing outline on yourself.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.god.use",
                        "/god [player] to toggle damage immunity.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.heal.use",
                        "/heal [player] to restore health.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.health.use",
                        "/health <amount> [player] to set a player's health.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.ice.use",
                        "/ice [player] [seconds] to freeze a player (inverse of /burn).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.nightvision.use",
                        "/nightvision (/nv) to toggle a night-vision effect on yourself.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.playerstate.fly.allworlds",
                        "Keep flight in worlds where flying is switched off.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.ptime.use",
                        "/ptime <value|reset> to set a per-player client-side time.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.pweather.use",
                        "/pweather <clear|rain|reset> to set a per-player client-side weather.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.rest.use",
                        "/rest to reset a player's time-since-rest so phantoms stop.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.speed.use",
                        "/speed, /walkspeed and /flyspeed to set walk/fly speed.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.suicide.use",
                        "/suicide to kill yourself.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE));
    }

    /** The verbs that read a player or the ground under them. */
    private static List<PermissionSpec> playerstateInspection() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.biome.use",
                        "/biome to show the biome you are standing in.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.depth.use",
                        "/depth to show your height relative to sea level.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.dimension.use",
                        "/dimension to show the dimension you are standing in.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.endersee.use",
                        "/endersee [player] to view a player's ender chest.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.getpos.use",
                        "/getpos (/coords /whereami) to show a player's coordinates.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.invsee.modify",
                        "Edit a player's inventory through the /invsee menu (without this it is view-only).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.invsee.use",
                        "/invsee [player] to view a player's inventory.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.near.use",
                        "/near [radius] to list nearby players.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.ping.use",
                        "/ping to show a player's round-trip latency.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.playtime.reset",
                        "/playtime reset [player] to wipe a player's tracked playtime (resetting others also needs uxmessentials.playerstate.others).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.playtime.use",
                        "/playtime [player] to show a player's playtime breakdown (active/afk, today/week/month/all-time).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.seed.use",
                        "/seed to show the seed of the world you are standing in.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.world.command-bypass",
                        "Run commands a world blocks through its per-world command list.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.world.use",
                        "/world to show the world you are standing in, and /worlds to reach the world manager.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE));
    }

    /** The verbs that reach somebody else, and the exemptions. */
    private static List<PermissionSpec> playerstateAdministration() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.clearinventory.confirmtoggle",
                        "/clearinventoryconfirmtoggle (/citoggle) to require a confirmation before /clearinventory clears your own inventory.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.clearinventory.use",
                        "/clearinventory (/ci /clear) [player] to empty a player's inventory.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.compass.use",
                        "/compass to show the direction you are facing.",
                        PermissionDefault.TRUE,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.module.playerstate",
                        "Hot-reload / inspect the playerstate module (flight, god mode, speed, health and the rest).",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.air.others",
                        "/air on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.burn.others",
                        "/burn on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.clearinventory.others",
                        "/clearinventory on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.endersee.others",
                        "/endersee to open another player ender chest. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.exp.others",
                        "/exp on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.extinguish.others",
                        "/extinguish on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.feed.others",
                        "/feed on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.fly.others",
                        "/fly on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.foodlevel.others",
                        "/foodlevel on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.gamemode.others",
                        "/gamemode on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.getpos.others",
                        "/getpos on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.god.others",
                        "/god on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.heal.others",
                        "/heal on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.health.others",
                        "/health on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.ice.others",
                        "/ice on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.invsee.others",
                        "/invsee to open another player inventory. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.ping.others",
                        "/ping on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.playtime.others",
                        "/playtime on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.rest.others",
                        "/rest on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.speed.others",
                        "/speed on another player. Granted on its own, or by the cross-cutting uxmessentials.playerstate.others.",
                        PermissionDefault.OP,
                        PLAYERSTATE),
                PermissionSpec.of(
                        "uxmessentials.playerstate.others",
                        "Use any playerstate command with a [player] target other than yourself.",
                        PermissionDefault.OP,
                        PLAYERSTATE));
    }
}
