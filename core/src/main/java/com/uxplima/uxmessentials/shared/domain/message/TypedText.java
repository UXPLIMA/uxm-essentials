package com.uxplima.uxmessentials.shared.domain.message;

import java.util.Locale;
import java.util.Objects;

/**
 * Text a player typed, made ready to go into a catalog line as the words they wrote. Catalog lines are MiniMessage, so
 * a {@code <} or a backslash in typed text would be read as markup: a reason given for being away could carry a click
 * into a line other players read. Typed text is stored as typed, and passed through {@link #asWords} at the place it
 * is put into a line, so every reader of the stored value (the API, a REST client, a placeholder) gets it as written.
 */
public final class TypedText {

    /** The characters that follow an {@code &} or {@code §} in a legacy colour, format or hex code. */
    private static final String LEGACY_CODES = "0123456789abcdefklmnorx#";

    /** What a legacy code is written as instead: a full-width ampersand, which reads the same and is no code. */
    private static final char NO_CODE = '\uff06';

    private TypedText() {}

    /**
     * {@code typed} with no colour or format code a legacy line would read: an {@code &} or {@code §} followed by a
     * code character is written as a full-width ampersand.
     */
    public static String withoutLegacyCodes(String typed) {
        Objects.requireNonNull(typed, "typed");
        StringBuilder kept = new StringBuilder(typed.length());
        for (int i = 0; i < typed.length(); i++) {
            char character = typed.charAt(i);
            boolean code = (character == '&' || character == '\u00a7')
                    && i + 1 < typed.length()
                    && LEGACY_CODES.indexOf(Character.toLowerCase(typed.charAt(i + 1))) >= 0;
            kept.append(code ? NO_CODE : character);
        }
        return kept.toString();
    }

    /**
     * {@code typed} as the inside of a JSON string, so it cannot close the string and add keys of its own. A quote of
     * either kind is escaped, since Gson also reads a string written in single quotes, and so are a backslash and every
     * control character.
     */
    public static String asJsonString(String typed) {
        Objects.requireNonNull(typed, "typed");
        StringBuilder escaped = new StringBuilder(typed.length() + 8);
        for (int i = 0; i < typed.length(); i++) {
            char character = typed.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\'' -> escaped.append("\\u0027");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

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
