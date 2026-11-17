package com.uxplima.uxmessentials.shared.network;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The network secret every backend signs and checks plugin-channel frames with, read from {@code network.bus.secret}.
 * A player's client can put a message on the bus channel whenever nothing in front of the backend stops it, so a frame
 * is trusted only when it carries a signature made with this secret, which the backends share and no client holds.
 *
 * <p>Only a secret that is hard to guess is used: a client that sees signed frames (one connected straight to a
 * backend, or behind a proxy that passes the channel on) could try guesses offline. A secret shorter than
 * {@link #MIN_LENGTH} characters is refused, and so is one copied from an example or typed as a short pattern repeated,
 * which is long but guessable. Its text never leaves this class except as key bytes, and {@link #toString()} names only
 * its state, so a record that holds it can be logged.
 */
@NullMarked
public final class BusSecret {

    /** The fewest characters a usable secret has. */
    public static final int MIN_LENGTH = 32;

    /**
     * The fewest different characters a usable secret has. Low enough that random digits pass: {@link #MIN_LENGTH} of
     * them show fewer than eight different ones about once in a thousand tries, random hex far less often.
     */
    static final int MIN_DISTINCT = 8;

    /** The longest pattern whose repeats count as weak; a random secret never repeats itself this way. */
    static final int MAX_REPEATED_PATTERN = 16;

    /** Words that mark a value copied from an example rather than generated. */
    private static final List<String> PLACEHOLDER_WORDS =
            List.of("openssl", "paste", "changeme", "change-me", "change_me", "example", "placeholder");

    private final String value;
    private final State state;

    private BusSecret(String value) {
        this.value = value;
        this.state = stateOf(value);
    }

    /** The secret as written, without the spaces around it, which are easy to copy by accident. */
    public static BusSecret of(String written) {
        return new BusSecret(Objects.requireNonNull(written, "written").strip());
    }

    /** Whether the secret can be signed with, and if not, why. */
    public State state() {
        return state;
    }

    /** Whether the secret is hard enough to guess to sign with. */
    public boolean usable() {
        return state == State.USABLE;
    }

    /** The key bytes a signature is made with. */
    public byte[] key() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "BusSecret[" + state.name().toLowerCase(Locale.ROOT) + "]";
    }

    private static State stateOf(String value) {
        if (value.isEmpty()) {
            return State.BLANK;
        }
        if (value.length() < MIN_LENGTH) {
            return State.SHORT;
        }
        String folded = value.toLowerCase(Locale.ROOT);
        // A value in angle brackets is a template to fill in; a single bracket inside is just a symbol.
        boolean template = value.startsWith("<") && value.endsWith(">");
        if (template || PLACEHOLDER_WORDS.stream().anyMatch(folded::contains)) {
            return State.PLACEHOLDER;
        }
        boolean weak = value.chars().distinct().count() < MIN_DISTINCT || repeatsAShortPattern(value);
        return weak ? State.WEAK : State.USABLE;
    }

    /** Whether {@code value} is one pattern of at most {@link #MAX_REPEATED_PATTERN} characters, repeated. */
    private static boolean repeatsAShortPattern(String value) {
        for (int period = 1; period <= MAX_REPEATED_PATTERN; period++) {
            if (value.regionMatches(period, value, 0, value.length() - period)) {
                return true;
            }
        }
        return false;
    }

    /** What the written secret is. */
    public enum State {
        /** Nothing was written. */
        BLANK,
        /** Fewer than {@link #MIN_LENGTH} characters. */
        SHORT,
        /** Text from an example, such as the one the documentation shows. */
        PLACEHOLDER,
        /**
         * Long enough, but made of fewer than {@link #MIN_DISTINCT} different characters, or one pattern of at most
         * {@link #MAX_REPEATED_PATTERN} characters repeated.
         */
        WEAK,
        /** Good to sign with. */
        USABLE
    }
}
