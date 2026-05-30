package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * A thin contract every Brigadier command class implements so a single registrar can publish them.
 *
 * <p>{@code PluginModule} collects these into one list; the plugin's {@code LifecycleEvents.COMMANDS}
 * handler iterates it and registers each node with its description and aliases in a single loop.
 * Adding a command is one new class plus one line in the owning module's contribution — no central
 * registration file to edit.
 */
public interface CommandRegistration {

    /** Builds the Brigadier command node. */
    LiteralCommandNode<CommandSourceStack> build();

    /** Short human-readable description shown in the command listing. */
    String description();

    /** Additional literals the command answers to. Empty by default. */
    default List<String> aliases() {
        return List.of();
    }
}
