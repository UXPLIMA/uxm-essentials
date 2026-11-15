package com.uxplima.uxmessentials.teleport.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.permission.TeleportPermissions;

/**
 * The teleport context's full command surface as platform-neutral
 * {@link CommandSpec}s. Each spec pairs a literal with its permission node and a factory that produces
 * the kernel-side {@link BrigadierCommand} description; the teleport inbound adapter realises the
 * Brigadier node from the started module's context on the next {@code COMMANDS} fire.
 *
 * <p>Collected here so {@code TeleportModule} stays small and the command/permission pairing is a single greppable
 * table.
 */
final class TeleportCommandSurface {

    private TeleportCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                // tpa lifecycle
                spec("tpa", TeleportPermissions.TPA_USE, DescribedCommand.of("tpa", "Request to teleport to a player")),
                spec(
                        "tpahere",
                        TeleportPermissions.TPAHERE_USE,
                        DescribedCommand.of("tpahere", "Ask a player to teleport to you")),
                spec(
                        "tpaccept",
                        TeleportPermissions.TPA_USE,
                        DescribedCommand.of("tpaccept", "Accept a teleport request", "tpyes")),
                spec(
                        "tpdeny",
                        TeleportPermissions.TPA_USE,
                        DescribedCommand.of("tpdeny", "Deny a teleport request", "tpno")),
                spec(
                        "tpcancel",
                        TeleportPermissions.TPA_CANCEL,
                        DescribedCommand.of("tpcancel", "Withdraw your outgoing request", "tpacancel")),
                spec(
                        "tpalist",
                        TeleportPermissions.TPA_USE,
                        DescribedCommand.of("tpalist", "List players waiting on your teleport reply", "tprequests")),
                spec(
                        "tptoggle",
                        TeleportPermissions.TPA_TOGGLE,
                        DescribedCommand.of("tptoggle", "Refuse all incoming requests", "toggletp")),
                spec(
                        "tpon",
                        TeleportPermissions.TPA_TOGGLE,
                        DescribedCommand.of("tpon", "Accept incoming teleport requests")),
                spec(
                        "tpoff",
                        TeleportPermissions.TPA_TOGGLE,
                        DescribedCommand.of("tpoff", "Refuse incoming teleport requests")),
                spec(
                        "tpauto",
                        TeleportPermissions.TPA_AUTO,
                        DescribedCommand.of("tpauto", "Auto-accept incoming teleport requests")),
                spec(
                        "tpblock",
                        TeleportPermissions.TPA_BLOCK,
                        DescribedCommand.of("tpblock", "Block a player's requests")),
                spec(
                        "tpunblock",
                        TeleportPermissions.TPA_BLOCK,
                        DescribedCommand.of("tpunblock", "Unblock a player's requests")),
                spec(
                        "tpaall",
                        TeleportPermissions.TPA_ALL,
                        DescribedCommand.of("tpaall", "Request every player to teleport to you")),
                // back
                spec("back", TeleportPermissions.BACK_USE, DescribedCommand.of("back", "Return to your last location")),
                spec(
                        "deathback",
                        TeleportPermissions.BACK_USE,
                        DescribedCommand.of("deathback", "Return to your last death location", "dback")),
                // rtp
                spec("rtp", TeleportPermissions.RTP_USE, DescribedCommand.of("rtp", "Random teleport", "wild")),
                spec(
                        "settpr",
                        TeleportPermissions.TELEPORT_SETTPR,
                        DescribedCommand.of("settpr", "Set the random-teleport zone for /rtp")),
                // spawn
                spec("spawn", TeleportPermissions.SPAWN_USE, DescribedCommand.of("spawn", "Teleport to spawn")),
                spec(
                        "setspawn",
                        TeleportPermissions.SPAWN_SET,
                        DescribedCommand.of("setspawn", "Set this world's spawn")),
                spec(
                        "setmainspawn",
                        TeleportPermissions.SPAWN_SET,
                        DescribedCommand.of("setmainspawn", "Set the main spawn for worlds without their own")),
                spec(
                        "removespawn",
                        TeleportPermissions.SPAWN_SET,
                        DescribedCommand.of("removespawn", "Clear this world's own spawn")),
                spec(
                        "mirrorspawn",
                        TeleportPermissions.SPAWN_SET,
                        DescribedCommand.of("mirrorspawn", "Redirect this world's spawn to another world")),
                // admin direct tp
                spec(
                        "tp",
                        TeleportPermissions.TP_USE,
                        DescribedCommand.of("tp", "Teleport to a player or coordinates")),
                spec("tphere", TeleportPermissions.TP_USE, DescribedCommand.of("tphere", "Pull a player to you")),
                spec("goto", TeleportPermissions.TP_USE, DescribedCommand.of("goto", "Teleport to a player")),
                spec("bring", TeleportPermissions.TP_USE, DescribedCommand.of("bring", "Pull a player to you")),
                spec(
                        "tprandomplayer",
                        TeleportPermissions.TP_USE,
                        DescribedCommand.of("tprandomplayer", "Teleport to a random online player", "tprp")),
                spec(
                        "tpo",
                        TeleportPermissions.TP_OTHERS,
                        DescribedCommand.of("tpo", "Teleport overriding no-tp flags")),
                spec(
                        "tpohere",
                        TeleportPermissions.TP_OTHERS,
                        DescribedCommand.of("tpohere", "Pull a player overriding flags")),
                spec(
                        "tppos",
                        TeleportPermissions.TP_POSITION,
                        DescribedCommand.of("tppos", "Teleport to raw coordinates")),
                spec("tpall", TeleportPermissions.TP_ALL, DescribedCommand.of("tpall", "Pull every player to you")),
                spec(
                        "tpoffline",
                        TeleportPermissions.TP_OFFLINE,
                        DescribedCommand.of("tpoffline", "Teleport to a player's logout point")),
                spec(
                        "tpofflinehere",
                        TeleportPermissions.TP_OFFLINE,
                        DescribedCommand.of("tpofflinehere", "Bring a player from their logout point")),
                // vertical / positional
                spec(
                        "top",
                        TeleportPermissions.TP_VERTICAL,
                        DescribedCommand.of("top", "Teleport to the highest block above")),
                spec(
                        "bottom",
                        TeleportPermissions.TP_VERTICAL,
                        DescribedCommand.of("bottom", "Teleport to the lowest safe block")),
                spec(
                        "jump",
                        TeleportPermissions.TP_VERTICAL,
                        DescribedCommand.of("jump", "Teleport to the block you look at")),
                spec(
                        "up",
                        TeleportPermissions.TP_VERTICAL,
                        DescribedCommand.of("up", "Teleport up onto a placed block")),
                spec(
                        "down",
                        TeleportPermissions.TP_VERTICAL,
                        DescribedCommand.of("down", "Teleport down to the first block below")),
                spec(
                        "ascend",
                        TeleportPermissions.TP_VERTICAL,
                        DescribedCommand.of("ascend", "Teleport up to the next open space")),
                spec(
                        "descend",
                        TeleportPermissions.TP_VERTICAL,
                        DescribedCommand.of("descend", "Teleport down to the next open space")),
                spec(
                        "thru",
                        TeleportPermissions.TP_VERTICAL,
                        DescribedCommand.of("thru", "Teleport through the wall you are facing")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }
}
