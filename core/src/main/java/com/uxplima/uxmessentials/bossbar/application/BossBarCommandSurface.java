package com.uxplima.uxmessentials.bossbar.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The boss bar context's one command. It is {@code /bossbars} and not {@code /bossbar}: the vanilla command of that
 * name is what map makers and data packs run, and a plugin command would stand in front of it.
 */
final class BossBarCommandSurface {

    private static final String USE_PERMISSION = "uxmessentials.bossbar.use";

    private BossBarCommandSurface() {}

    static List<CommandSpec> all() {
        Function<ModuleContext, BrigadierCommand> factory =
                ctx -> new BossBarsCommand("bossbars", "Hide or show your boss bars, or announce one");
        return List.of(new CommandSpec("bossbars", USE_PERMISSION, factory));
    }

    private record BossBarsCommand(String literal, String description) implements BrigadierCommand {}
}
