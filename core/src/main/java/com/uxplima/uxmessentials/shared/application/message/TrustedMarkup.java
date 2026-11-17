package com.uxplima.uxmessentials.shared.application.message;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A message value that is markup on purpose: a catalogue line used inside another one, a list the plugin built with
 * tags, an operator's own display name, a message body already checked against the formatting permission. A value
 * filled into a catalogue line is escaped by default, so a name or a message a player typed reads as typed; a value
 * passed through {@link #of} goes in as written instead.
 *
 * <p>The mark is a token made once for this server run and never shown: a value has to have been passed through
 * {@link #of} on this server to carry it, so a player cannot type a value that counts as markup. A token from an
 * earlier run, say on a line that was stored and read back after a restart, no longer counts and is taken off
 * ({@link #strip}), and the value is escaped like any other.
 */
public final class TrustedMarkup {

    /** A Unicode noncharacter, never used in text, that opens and closes the token. */
    private static final char EDGE = '\uFDD1';

    private static final String TOKEN = EDGE + HexFormat.of().formatHex(nonce()) + EDGE;

    /** Any token, of this run or an earlier one. */
    private static final Pattern ANY_TOKEN = Pattern.compile(EDGE + "[0-9a-f]{32}" + EDGE);

    private TrustedMarkup() {}

    /** {@code markup}, to be filled into a line as written rather than escaped. */
    public static String of(String markup) {
        Objects.requireNonNull(markup, "markup");
        return markup.startsWith(TOKEN) ? markup : TOKEN + markup;
    }

    /** The markup {@code value} carries when it was passed through {@link #of} on this run; empty otherwise. */
    public static Optional<String> markup(String value) {
        Objects.requireNonNull(value, "value");
        return value.startsWith(TOKEN) ? Optional.of(strip(value.substring(TOKEN.length()))) : Optional.empty();
    }

    /**
     * {@code text} with every token taken off, this run's or an earlier one's, and any other use of the character tokens
     * are built from, so none is ever shown, stored or sent, and no text can carry part of one into a line.
     */
    public static String strip(String text) {
        Objects.requireNonNull(text, "text");
        if (text.indexOf(EDGE) < 0) {
            return text;
        }
        return ANY_TOKEN.matcher(text).replaceAll("").replace(String.valueOf(EDGE), "");
    }

    private static byte[] nonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
