package com.uxplima.uxmessentials.presence.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The presence context's command surface (docs/10-feature-modules.md §15.8) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The presence inbound adapter realises the Brigadier node
 * from the started module's context on the next {@code COMMANDS} fire. Collected here so {@code PresenceModule}
 * stays small and the command/permission pairing is one greppable table the permissions guard checks against
 * {@code paper-plugin.yml}.
 *
 * <p>Four commands: {@code /afk [reason]} ({@code uxmessentials.afk.use}), {@code /vanish}
 * ({@code uxmessentials.vanish.use}), {@code /list} ({@code uxmessentials.list.use}), and {@code /realname <player>}
 * ({@code uxmessentials.realname.use}). The vanish-see node ({@code uxmessentials.vanish.see}) gates visibility,
 * not a command, so it is enforced in the {@code VisibilityApplier} rather than declared as a literal here.
 */
final class PresenceCommandSurface {

    private PresenceCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("afk", "uxmessentials.afk.use", cmd("afk", "Toggle your AFK state")),
                spec("vanish", "uxmessentials.vanish.use", cmd("vanish", "Toggle staff vanish")),
                spec("list", "uxmessentials.list.use", cmd("list", "List online players")),
                spec("realname", "uxmessentials.realname.use", cmd("realname", "Look up a player's real name")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand cmd(String literal, String description) {
        return new PresenceCommand(literal, description);
    }

    /** The kernel-side description of one presence command — literal and help text, no Brigadier type. */
    private record PresenceCommand(String literal, String description) implements BrigadierCommand {}
}
