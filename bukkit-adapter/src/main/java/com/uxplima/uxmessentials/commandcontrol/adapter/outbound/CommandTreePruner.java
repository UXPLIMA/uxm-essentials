package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.jspecify.annotations.NullMarked;

/**
 * Takes the fixed words a player may not use out of the command tree the server sends their client, so
 * {@code /gamemode} offers {@code creative} but not {@code spectator} when the lists say so. The client completes those
 * words from the tree on its own, without asking the server, which is why the sent tree has to lose them rather than
 * the suggestions.
 *
 * <p>Only word nodes are judged, by the command they spell from the root ({@code "gamemode spectator"}). An argument
 * node, such as a player name, spells nothing fixed, so the walk stops there and leaves it to the suggestion filter.
 * The tree is the copy built for this one player, so removing a node changes nobody else's.
 */
@NullMarked
public final class CommandTreePruner {

    private CommandTreePruner() {}

    /**
     * Remove from below each command in {@code root} every word whose command {@code visible} rejects, for the
     * commands {@code walk} accepts. A command itself is never removed here: the sent command list decides those.
     */
    public static void prune(RootCommandNode<?> root, Predicate<String> walk, Predicate<String> visible) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(walk, "walk");
        Objects.requireNonNull(visible, "visible");
        for (CommandNode<?> command : root.getChildren()) {
            if (command instanceof LiteralCommandNode<?> && walk.test(command.getName())) {
                pruneBelow(command, command.getName(), visible);
            }
        }
    }

    private static void pruneBelow(CommandNode<?> node, String path, Predicate<String> visible) {
        List<String> removed = new ArrayList<>();
        for (CommandNode<?> child : node.getChildren()) {
            if (!(child instanceof LiteralCommandNode<?>)) {
                continue;
            }
            String childPath = path + " " + child.getName();
            if (visible.test(childPath)) {
                pruneBelow(child, childPath, visible);
            } else {
                removed.add(child.getName());
            }
        }
        removed.forEach(node::removeCommand);
    }
}
