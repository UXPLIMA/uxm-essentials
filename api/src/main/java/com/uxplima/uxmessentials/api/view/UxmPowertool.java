package com.uxplima.uxmessentials.api.view;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * A command binding a player put on an item with {@code /powertool}: using the item runs the commands.
 *
 * <p>The binding lives on the item, not on the player, so it travels with the item: dropping it, trading it or
 * putting it in a chest takes the binding along. That is why this names a slot rather than only a command list;
 * the same player can hold two bound items at once, and which one is in hand decides what a use runs.
 *
 * @param slot the inventory slot the bound item sits in, counting from zero as Bukkit does
 * @param item the item's id, in {@code namespace:path} form
 * @param commands the command lines a use runs, in order, each without its leading slash
 */
@NullMarked
public record UxmPowertool(int slot, String item, List<String> commands) {

    public UxmPowertool {
        Objects.requireNonNull(item, "item");
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative, was " + slot);
        }
    }
}
