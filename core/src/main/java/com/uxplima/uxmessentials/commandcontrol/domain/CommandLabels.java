package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Pure helpers for the raw command message the adapter sees on the command-preprocess path.
 *
 * <p>The auto-lowercase normalisation ({@link #lowerBaseLabel}) lowercases only the leading command label so
 * {@code /GAMEMODE creative} is treated and executed as {@code /gamemode creative}: the label runs up to the first
 * space (or the whole message when there are none), and everything from that space onward, including the argument
 * casing and any leading slash, is preserved untouched. This lets the whitelist / plugin-hide checks and the vanilla
 * dispatcher see a single canonical label without ever mangling an argument (a world name, a target player, a message
 * body).
 */
public final class CommandLabels {

    private CommandLabels() {}

    /**
     * Lowercase only the base command label of {@code rawMessage}, preserving a leading slash and every argument
     * exactly. {@code "/GAMEMODE Creative"} becomes {@code "/gamemode Creative"}; {@code "Hello World"} becomes
     * {@code "hello World"}. A blank message is returned unchanged.
     */
    public static String lowerBaseLabel(String rawMessage) {
        Objects.requireNonNull(rawMessage, "rawMessage");
        if (rawMessage.isBlank()) {
            return rawMessage;
        }
        boolean slash = rawMessage.startsWith("/");
        String body = slash ? rawMessage.substring(1) : rawMessage;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        String rest = space < 0 ? "" : body.substring(space);
        String lowered = label.toLowerCase(Locale.ROOT);
        return (slash ? "/" : "") + lowered + rest;
    }
}
