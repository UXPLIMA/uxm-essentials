package com.uxplima.uxmessentials.teleport.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The teleport context's full command surface (docs/10-feature-modules.md §15.3) as platform-neutral
 * {@link CommandSpec}s. Each spec pairs a literal with its permission node and a factory that produces
 * the kernel-side {@link BrigadierCommand} description; the teleport inbound adapter realises the
 * Brigadier node from the started module's context on the next {@code COMMANDS} fire.
 *
 * <p>Collected here so {@code TeleportModule} stays small and the command/permission pairing is a single
 * greppable table the permissions guard can check against {@code paper-plugin.yml}.
 */
final class TeleportCommandSurface {

    private TeleportCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                // tpa lifecycle
                spec("tpa", "uxmessentials.tpa.use", DescribedCommand.of("tpa", "Request to teleport to a player")),
                spec(
                        "tpahere",
                        "uxmessentials.tpahere.use",
                        DescribedCommand.of("tpahere", "Ask a player to teleport to you")),
                spec(
                        "tpaccept",
                        "uxmessentials.tpa.use",
                        DescribedCommand.of("tpaccept", "Accept a teleport request", "tpyes")),
                spec(
                        "tpdeny",
                        "uxmessentials.tpa.use",
                        DescribedCommand.of("tpdeny", "Deny a teleport request", "tpno")),
                spec(
                        "tpcancel",
                        "uxmessentials.tpa.cancel",
                        DescribedCommand.of("tpcancel", "Withdraw your outgoing request", "tpacancel")),
                spec(
                        "tpalist",
                        "uxmessentials.tpa.use",
                        DescribedCommand.of("tpalist", "List players waiting on your teleport reply", "tprequests")),
                spec(
                        "tptoggle",
                        "uxmessentials.tpa.toggle",
                        DescribedCommand.of("tptoggle", "Refuse all incoming requests", "toggletp")),
                spec(
                        "tpon",
                        "uxmessentials.tpa.toggle",
                        DescribedCommand.of("tpon", "Accept incoming teleport requests")),
                spec(
                        "tpoff",
                        "uxmessentials.tpa.toggle",
                        DescribedCommand.of("tpoff", "Refuse incoming teleport requests")),
                spec(
                        "tpauto",
                        "uxmessentials.tpa.auto",
                        DescribedCommand.of("tpauto", "Auto-accept incoming teleport requests")),
                spec("tpblock", "uxmessentials.tpa.block", DescribedCommand.of("tpblock", "Block a player's requests")),
                spec(
                        "tpunblock",
                        "uxmessentials.tpa.block",
                        DescribedCommand.of("tpunblock", "Unblock a player's requests")),
                spec(
                        "tpaall",
                        "uxmessentials.tpa.all",
                        DescribedCommand.of("tpaall", "Request every player to teleport to you")),
                // back
                spec("back", "uxmessentials.back.use", DescribedCommand.of("back", "Return to your last location")),
                spec(
                        "deathback",
                        "uxmessentials.back.use",
                        DescribedCommand.of("deathback", "Return to your last death location", "dback")),
                // rtp
                spec("rtp", "uxmessentials.rtp.use", DescribedCommand.of("rtp", "Random teleport", "wild")),
                spec(
                        "settpr",
                        "uxmessentials.teleport.settpr",
                        DescribedCommand.of("settpr", "Set the random-teleport zone for /rtp")),
                // spawn
                spec("spawn", "uxmessentials.spawn.use", DescribedCommand.of("spawn", "Teleport to spawn")),
                spec("setspawn", "uxmessentials.spawn.set", DescribedCommand.of("setspawn", "Set the server spawn")),
                spec(
                        "setmainspawn",
                        "uxmessentials.spawn.set",
                        DescribedCommand.of("setmainspawn", "Set the global main spawn")),
                spec(
                        "removespawn",
                        "uxmessentials.spawn.set",
                        DescribedCommand.of("removespawn", "Clear this world's own spawn")),
                spec(
                        "mirrorspawn",
                        "uxmessentials.spawn.set",
                        DescribedCommand.of("mirrorspawn", "Redirect this world's spawn to another world")),
                // admin direct tp
                spec("tp", "uxmessentials.tp.use", DescribedCommand.of("tp", "Teleport to a player or coordinates")),
                spec("tphere", "uxmessentials.tp.use", DescribedCommand.of("tphere", "Pull a player to you")),
                spec("goto", "uxmessentials.tp.use", DescribedCommand.of("goto", "Teleport to a player")),
                spec("bring", "uxmessentials.tp.use", DescribedCommand.of("bring", "Pull a player to you")),
                spec(
                        "tprandomplayer",
                        "uxmessentials.tp.use",
                        DescribedCommand.of("tprandomplayer", "Teleport to a random online player", "tprp")),
                spec("tpo", "uxmessentials.tp.others", DescribedCommand.of("tpo", "Teleport overriding no-tp flags")),
                spec(
                        "tpohere",
                        "uxmessentials.tp.others",
                        DescribedCommand.of("tpohere", "Pull a player overriding flags")),
                spec("tppos", "uxmessentials.tp.position", DescribedCommand.of("tppos", "Teleport to raw coordinates")),
                spec("tpall", "uxmessentials.tp.all", DescribedCommand.of("tpall", "Pull every player to you")),
                spec(
                        "tpoffline",
                        "uxmessentials.tp.offline",
                        DescribedCommand.of("tpoffline", "Teleport to a player's logout point")),
                spec(
                        "tpofflinehere",
                        "uxmessentials.tp.offline",
                        DescribedCommand.of("tpofflinehere", "Bring a player from their logout point")),
                // vertical / positional
                spec(
                        "top",
                        "uxmessentials.tp.vertical",
                        DescribedCommand.of("top", "Teleport to the highest block above")),
                spec(
                        "bottom",
                        "uxmessentials.tp.vertical",
                        DescribedCommand.of("bottom", "Teleport to the lowest safe block")),
                spec(
                        "jump",
                        "uxmessentials.tp.vertical",
                        DescribedCommand.of("jump", "Teleport to the block you look at")),
                spec("up", "uxmessentials.tp.vertical", DescribedCommand.of("up", "Teleport up onto a placed block")),
                spec(
                        "down",
                        "uxmessentials.tp.vertical",
                        DescribedCommand.of("down", "Teleport down to the first block below")),
                spec(
                        "ascend",
                        "uxmessentials.tp.vertical",
                        DescribedCommand.of("ascend", "Teleport up to the next open space")),
                spec(
                        "descend",
                        "uxmessentials.tp.vertical",
                        DescribedCommand.of("descend", "Teleport down to the next open space")),
                spec(
                        "thru",
                        "uxmessentials.tp.vertical",
                        DescribedCommand.of("thru", "Teleport through the wall you are facing")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }
}
