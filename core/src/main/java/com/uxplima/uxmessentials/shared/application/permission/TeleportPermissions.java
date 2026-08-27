package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the teleport context. Data, not logic: one row per node, read by {@link PermissionCatalog}
 * and through it by the server registration, the reference page and the in-game listing.
 */
final class TeleportPermissions {

    private static final ModuleId TELEPORT = ModuleId.of("teleport");

    private TeleportPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(teleportRequests(), teleportMovement())
                .flatMap(List::stream)
                .toList();
    }

    /** The player-to-player requests and their settings. */
    private static List<PermissionSpec> teleportRequests() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.tpa.all",
                        "/tpaall to request every online player to teleport to you.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tpa.auto",
                        "/tpauto to auto-accept incoming teleport requests.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tpa.block",
                        "/tpblock / /tpunblock to block a player's requests.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tpa.cancel",
                        "/tpcancel / /tpacancel to withdraw your outgoing request.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tpa.toggle",
                        "/tptoggle to refuse all incoming teleport requests.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tpa.use",
                        "/tpa, /tpaccept, /tpdeny to request and resolve a teleport.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tpahere.use",
                        "/tpahere to ask a player to come to you.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tpsettings.use",
                        "/tpsettings opens your personal teleport settings panel.",
                        PermissionDefault.TRUE,
                        TELEPORT));
    }

    /** The direct hops: /tp, /back, /spawn and /rtp. */
    private static List<PermissionSpec> teleportMovement() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.back.ondeath",
                        "Allow /back and /deathback to return to a death location.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.back.use",
                        "/back to return to your last captured location; /deathback (alias /dback) to return to your last death location.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.module.teleport",
                        "Hot-reload / inspect the teleport module (/tp, /tpa, /back, /spawn and /rtp).",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.rtp.biome",
                        "/rtp biome <biome> to random teleport into a specific biome.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.rtp.gui",
                        "/rtp gui to open the random-teleport world picker.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.rtp.others",
                        "/rtp <player> to force another online player to random teleport (staff).",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.family(
                        "uxmessentials.rtp.radius.<blocks>",
                        "How far from the world centre /rtp may drop you; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.rtp.use",
                        "/rtp random teleport from the pre-warmed safe-location queue.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.spawn.join.exempt",
                        "Exempt the player from automatic first-join/every-join spawn movement.",
                        PermissionDefault.FALSE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.spawn.named",
                        "/spawn <name> to teleport to a named spawn.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.spawn.set",
                        "/setspawn, /setmainspawn, /removespawn and /mirrorspawn to define and manage spawns.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.spawn.use",
                        "/spawn to teleport to the resolved server spawn.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.teleport.gui",
                        "Show the teleport settings panel on the /uxmess gui hub.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.teleport.settpr",
                        "/settpr <minRange> <maxRange> to set the /rtp search zone at runtime.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tp.all",
                        "/tpall to pull every online player to you.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tp.offline",
                        "/tpoffline / /tpofflinehere to a player's logout location.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tp.others",
                        "/tpo and /tpohere to teleport overriding no-tp flags.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tp.position",
                        "/tppos to teleport to raw coordinates.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tp.use",
                        "/tp, /tphere, /goto, /bring and /tprandomplayer (/tprp) direct staff teleport.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tp.vertical",
                        "/top, /bottom, /jump, /up, /down, /ascend, /descend, /thru vertical teleports.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.family(
                        "uxmessentials.tp.warmup.<seconds>",
                        "The stand-still countdown before a teleport runs, in seconds; the shortest tier held wins and 0 removes it.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER,
                        TELEPORT),
                PermissionSpec.of(
                        "uxmessentials.tp.warmup.bypass",
                        "Start teleports with no warmup, immune to move-cancel.",
                        PermissionDefault.OP,
                        TELEPORT));
    }
}
