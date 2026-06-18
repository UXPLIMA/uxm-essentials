package com.uxplima.uxmessentials.worlds.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The worlds context's command surface as platform-neutral {@link CommandSpec}s. The {@code /worlds}
 * root carries the base {@code uxmessentials.world.use} node; its create/import/load/unload/
 * unregister/delete/list/info subcommands are Brigadier literals gated individually by the inbound
 * adapter. {@code /worldsconfirm} is the delete confirmation companion, gated by the delete node.
 * The literal is {@code worlds} (plural) because {@code playerstate} already owns {@code /world}
 * (a read-only "which world am I in" command); literals must be globally unique.
 */
final class WorldCommandSurface {

    private WorldCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("worlds", "uxmessentials.world.use", WorldsCommandDescriptor.of("worlds", "Manage worlds")),
                spec(
                        "worldsconfirm",
                        "uxmessentials.world.delete",
                        WorldsCommandDescriptor.of("worldsconfirm", "Confirm a pending world deletion")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private record WorldsCommandDescriptor(String literal, String description) implements BrigadierCommand {
        static WorldsCommandDescriptor of(String literal, String description) {
            return new WorldsCommandDescriptor(literal, description);
        }
    }
}
