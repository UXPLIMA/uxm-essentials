package com.uxplima.uxmessentials.homes.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The homes context's command surface (docs/10-feature-modules.md §15.1) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The homes inbound adapter realises the Brigadier node
 * from the started module's context on the next {@code COMMANDS} fire. Collected here so {@code
 * HomesModule} stays small and the command/permission pairing is one greppable table the permissions
 * guard checks against {@code paper-plugin.yml}.
 */
final class HomeCommandSurface {

    private HomeCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("home", "uxmessentials.home.use", HomeCommand.of("home", "Teleport to one of your homes")),
                spec("sethome", "uxmessentials.home.set", HomeCommand.of("sethome", "Create or move a home")),
                spec("delhome", "uxmessentials.home.delete", HomeCommand.of("delhome", "Delete one of your homes")),
                spec("homes", "uxmessentials.home.list", HomeCommand.of("homes", "List your homes")),
                spec(
                        "renamehome",
                        "uxmessentials.home.rename",
                        HomeCommand.of("renamehome", "Rename one of your homes")),
                spec(
                        "movehome",
                        "uxmessentials.home.move",
                        HomeCommand.of("movehome", "Move a home to your current location")),
                spec(
                        "homeadmin",
                        "uxmessentials.home.admin",
                        HomeCommand.of("homeadmin", "Manage another player's homes")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one home command — literal and help text, no Brigadier type. */
    private record HomeCommand(String literal, String description) implements BrigadierCommand {

        static HomeCommand of(String literal, String description) {
            return new HomeCommand(literal, description);
        }
    }
}
