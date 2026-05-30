package com.uxplima.uxmessentials.shared.application.module;

import java.util.List;

/**
 * Kernel-side handle to a command a module contributes, free of any Brigadier or Bukkit type.
 *
 * <p>A {@link CommandSpec} produces one of these from a {@link ModuleContext} inside the module's
 * {@code start}; the bukkit-adapter realises it into a real {@code LiteralCommandNode} when Paper
 * fires {@code LifecycleEvents.COMMANDS}. Keeping the kernel side to the literal, its aliases, and a
 * short description is what lets {@code FeatureModule} stay pure Java while the module still owns the
 * full description of what it registers.
 */
public interface BrigadierCommand {

    /** The primary command literal, without a leading slash: {@code "home"}, {@code "pay"}. */
    String literal();

    /** Additional literals the same node answers to. Empty when there are none. */
    default List<String> aliases() {
        return List.of();
    }

    /** Short human-readable description surfaced in the command help listing. */
    String description();
}
