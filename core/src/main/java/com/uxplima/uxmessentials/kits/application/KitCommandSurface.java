package com.uxplima.uxmessentials.kits.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The kits context's command surface (docs/10-feature-modules.md §15.5) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The kits inbound adapter realises the Brigadier node from
 * the started module's context on the next {@code COMMANDS} fire. Collected here so {@code KitsModule} stays
 * small and the command/permission pairing is one greppable table the permissions guard checks against
 * {@code paper-plugin.yml}.
 *
 * <p>The per-kit permission node {@code uxmessentials.kit.<id>} is not a command base permission — it is the
 * data-driven gate the claim/list use cases check per kit, so it is intentionally absent from this table;
 * only the base {@code uxmessentials.kit.use} node guards {@code /kit} and {@code /kits}.
 */
final class KitCommandSurface {

    private KitCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("kit", "uxmessentials.kit.use", KitCommand.of("kit", "Claim a kit")),
                spec("kits", "uxmessentials.kit.use", KitCommand.of("kits", "List kits you may claim")),
                spec("showkit", "uxmessentials.kit.preview", KitCommand.of("showkit", "Preview a kit's contents")),
                spec("createkit", "uxmessentials.kit.edit", KitCommand.of("createkit", "Define a kit")),
                spec("delkit", "uxmessentials.kit.edit", KitCommand.of("delkit", "Remove a kit")),
                spec("kiteditor", "uxmessentials.kit.edit", KitCommand.of("kiteditor", "Edit a kit's contents")),
                spec("kitreset", "uxmessentials.kit.reset", KitCommand.of("kitreset", "Clear a player's kit claims")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one kit command — literal and help text, no Brigadier type. */
    private record KitCommand(String literal, String description) implements BrigadierCommand {

        static KitCommand of(String literal, String description) {
            return new KitCommand(literal, description);
        }
    }
}
