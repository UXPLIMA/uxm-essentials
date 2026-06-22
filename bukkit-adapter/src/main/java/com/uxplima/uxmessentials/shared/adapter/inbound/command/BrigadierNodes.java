package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Brigadier command nodes are immutable once built, so any binding that wants to change a root's literal or
 * its root executor has to rebuild the tree rather than mutate it. This collects the rebuild the catalog,
 * usage and GUI bindings all need: a new root literal carrying the source node's requirement predicate and
 * every child verbatim, with the root executor chosen by the caller. Children are copied through their own
 * {@code createBuilder()} so each keeps its argument type and suggestions.
 */
@NullMarked
final class BrigadierNodes {

    private BrigadierNodes() {}

    /**
     * Rebuild {@code node} under {@code literal}, carrying its requirement and children across verbatim and
     * setting the root executor to {@code command} (pass {@code null} to leave the root with no executor).
     */
    static LiteralCommandNode<CommandSourceStack> rebindRoot(
            LiteralCommandNode<CommandSourceStack> node,
            String literal,
            @Nullable Command<CommandSourceStack> command) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(literal);
        if (node.getRequirement() != null) {
            builder.requires(node.getRequirement());
        }
        if (command != null) {
            builder.executes(command);
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(rebindChild(child));
        }
        return builder.build();
    }

    /** Copy a non-root node and its descendants through their own builders, preserving every executor. */
    static ArgumentBuilder<CommandSourceStack, ?> rebindChild(CommandNode<CommandSourceStack> child) {
        @SuppressWarnings("unchecked") // createBuilder() reproduces the node's own builder type
        ArgumentBuilder<CommandSourceStack, ?> builder = (ArgumentBuilder<CommandSourceStack, ?>) child.createBuilder();
        if (child.getCommand() != null) {
            builder.executes(child.getCommand());
        }
        for (CommandNode<CommandSourceStack> grandchild : child.getChildren()) {
            builder.then(rebindChild(grandchild));
        }
        return builder;
    }
}
