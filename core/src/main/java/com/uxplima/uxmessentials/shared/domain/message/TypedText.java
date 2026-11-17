package com.uxplima.uxmessentials.shared.domain.message;

import java.util.Objects;

/**
 * Text a player typed, made ready to go into a catalog line as the words they wrote. Catalog lines are MiniMessage, so
 * a {@code <} or a backslash in typed text would be read as markup: a reason given for being away could carry a click
 * into a line other players read. Typed text is stored as typed, and passed through {@link #asWords} at the place it
 * is put into a line, so every reader of the stored value (the API, a REST client, a placeholder) gets it as written.
 */
public final class TypedText {

    private TypedText() {}

    /**
     * {@code typed} with every {@code <} and every backslash escaped, whatever follows it, so a line it is put into
     * shows the words and parses no tag. Escaping only what parses as a tag is not enough: a line is read twice, by the
     * token expansion and then by the parser, and the two disagree about a malformed tag.
     */
    public static String asWords(String typed) {
        Objects.requireNonNull(typed, "typed");
        StringBuilder escaped = new StringBuilder(typed.length() + 8);
        for (int i = 0; i < typed.length(); i++) {
            char character = typed.charAt(i);
            if (character == '<' || character == '\\') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
