package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the teleport context. Data, not logic: one row per node, read by {@link PermissionCatalog}
 * and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class TeleportPermissions {

    private static final ModuleId TELEPORT = ModuleId.of("teleport");

    public static final String TPA_ALL = "uxmessentials.tpa.all";
    public static final String TPA_AUTO = "uxmessentials.tpa.auto";
    public static final String TPA_BLOCK = "uxmessentials.tpa.block";
    public static final String TPA_CANCEL = "uxmessentials.tpa.cancel";
    public static final String TPA_TOGGLE = "uxmessentials.tpa.toggle";
    public static final String TPA_USE = "uxmessentials.tpa.use";
    public static final String TPAHERE_USE = "uxmessentials.tpahere.use";
    public static final String TPSETTINGS_USE = "uxmessentials.tpsettings.use";
    public static final String BACK_ONDEATH = "uxmessentials.back.ondeath";
    public static final String BACK_USE = "uxmessentials.back.use";
    public static final String MODULE_TELEPORT = "uxmessentials.module.teleport";
    public static final String RTP_BIOME = "uxmessentials.rtp.biome";
    public static final String RTP_GUI = "uxmessentials.rtp.gui";
    public static final String RTP_OTHERS = "uxmessentials.rtp.others";
    public static final String RTP_USE = "uxmessentials.rtp.use";
    public static final String SPAWN_JOIN_EXEMPT = "uxmessentials.bypass.join-spawn";
    public static final String SPAWN_NAMED = "uxmessentials.spawn.named";
    public static final String SPAWN_SET = "uxmessentials.spawn.set";
    public static final String SPAWN_USE = "uxmessentials.spawn.use";
    public static final String TELEPORT_GUI = "uxmessentials.teleport.gui";
    public static final String TELEPORT_SETTPR = "uxmessentials.teleport.settpr";
    public static final String TP_ALL = "uxmessentials.tp.all";
    public static final String TP_OFFLINE = "uxmessentials.tp.offline";
    public static final String TP_OTHERS = "uxmessentials.tp.others";
    public static final String TP_POSITION = "uxmessentials.tp.position";
    public static final String TP_USE = "uxmessentials.tp.use";
    public static final String TP_VERTICAL = "uxmessentials.tp.vertical";
    public static final String TP_WARMUP_BYPASS = "uxmessentials.bypass.warmup.tp";

    /** The head of the random-teleport radius quota; the blocks held are appended to it. */
    public static final String RTP_RADIUS = "uxmessentials.rtp.radius";

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
                        TPA_ALL,
                        "/tpaall to request every online player to teleport to you.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        TPA_AUTO,
                        "/tpauto to auto-accept incoming teleport requests.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        TPA_BLOCK,
                        "/tpblock / /tpunblock to block a player's requests.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        TPA_CANCEL,
                        "/tpcancel / /tpacancel to withdraw your outgoing request.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        TPA_TOGGLE,
                        "/tptoggle to refuse all incoming teleport requests.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        TPA_USE,
                        "/tpa, /tpaccept, /tpdeny to request and resolve a teleport.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        TPAHERE_USE, "/tpahere to ask a player to come to you.", PermissionDefault.TRUE, TELEPORT),
                PermissionSpec.of(
                        TPSETTINGS_USE,
                        "/tpsettings opens your personal teleport settings panel.",
                        PermissionDefault.TRUE,
                        TELEPORT));
    }

    /** The direct hops: /tp, /back, /spawn and /rtp. */
    private static List<PermissionSpec> teleportMovement() {
        return List.of(
                PermissionSpec.of(
                        BACK_ONDEATH,
                        "Allow /back and /deathback to return to a death location.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        BACK_USE,
                        "/back to return to your last captured location; /deathback (alias /dback) to return to your last death location.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        MODULE_TELEPORT,
                        "Hot-reload / inspect the teleport module (/tp, /tpa, /back, /spawn and /rtp).",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        RTP_BIOME,
                        "/rtp biome <biome> to random teleport into a specific biome.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        RTP_GUI,
                        "/rtp gui to open the random-teleport world picker.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        RTP_OTHERS,
                        "/rtp <player> to force another online player to random teleport (staff).",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.family(
                        RTP_RADIUS + ".<blocks>",
                        "How far from the world centre /rtp may drop you; the largest tier held wins.",
                        PermissionDefault.TRUE,
                        PermissionShape.QUOTA,
                        TELEPORT),
                PermissionSpec.of(
                        RTP_USE,
                        "/rtp random teleport from the pre-warmed safe-location queue.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        SPAWN_JOIN_EXEMPT,
                        "Exempt the player from automatic first-join/every-join spawn movement.",
                        PermissionDefault.FALSE,
                        TELEPORT),
                PermissionSpec.of(
                        SPAWN_NAMED, "/spawn <name> to teleport to a named spawn.", PermissionDefault.TRUE, TELEPORT),
                PermissionSpec.of(
                        SPAWN_SET,
                        "/setspawn, /setmainspawn, /removespawn and /mirrorspawn to define and manage spawns.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        SPAWN_USE,
                        "/spawn to teleport to the resolved server spawn.",
                        PermissionDefault.TRUE,
                        TELEPORT),
                PermissionSpec.of(
                        TELEPORT_GUI,
                        "Show the teleport settings panel on the /uxmess gui hub.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        TELEPORT_SETTPR,
                        "/settpr <minRange> <maxRange> to set the /rtp search zone at runtime.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(TP_ALL, "/tpall to pull every online player to you.", PermissionDefault.OP, TELEPORT),
                PermissionSpec.of(
                        TP_OFFLINE,
                        "/tpoffline / /tpofflinehere to a player's logout location.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        TP_OTHERS,
                        "/tpo and /tpohere to teleport overriding no-tp flags.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        TP_POSITION, "/tppos to teleport to raw coordinates.", PermissionDefault.OP, TELEPORT),
                PermissionSpec.of(
                        TP_USE,
                        "/tp, /tphere, /goto, /bring and /tprandomplayer (/tprp) direct staff teleport.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.of(
                        TP_VERTICAL,
                        "/top, /bottom, /jump, /up, /down, /ascend, /descend, /thru vertical teleports.",
                        PermissionDefault.OP,
                        TELEPORT),
                PermissionSpec.family(
                        SharedPermissions.featureWarmup("tp") + ".<seconds>",
                        "The stand-still countdown before a teleport runs, in seconds; the shortest tier held wins and 0 removes it.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER,
                        TELEPORT),
                PermissionSpec.of(
                        TP_WARMUP_BYPASS,
                        "Start teleports with no warmup, immune to move-cancel.",
                        PermissionDefault.OP,
                        TELEPORT));
    }
}
