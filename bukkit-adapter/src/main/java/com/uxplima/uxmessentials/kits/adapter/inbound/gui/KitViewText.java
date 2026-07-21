package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Small text helpers shared by the kit settings panels: the {@code none}-aware pipe split that turns an anvil-typed
 * lore or command line into the list its aggregate stores.
 */
@NullMarked
final class KitViewText {

    private KitViewText() {}

    /**
     * Split a pipe-delimited anvil input into lines, treating the literal {@code none} (any case) as an explicit clear
     * that yields an empty list.
     */
    static List<String> splitLines(String input) {
        return input.equalsIgnoreCase("none") ? List.of() : Arrays.asList(input.split("\\|"));
    }
}
