package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The network secret every backend signs and checks plugin-channel frames with, read from {@code network.bus.secret}.
 * A player's client can put a message on the bus channel whenever nothing in front of the backend stops it, so a frame
 * is trusted only when it carries a signature made with this secret, which the backends share and no client holds.
 *
 * <p>A secret shorter than {@link #MIN_LENGTH} characters is not used: a client that sees signed frames (one connected
 * straight to a backend, or behind a proxy that passes the channel on) could guess a short one offline. Its text never
 * leaves this class except as key bytes, and {@link #toString()} names only its state, so a record that holds it can be
 * logged.
 */
@NullMarked
public final class BusSecret {

    /** The fewest characters a usable secret has. */
    public static final int MIN_LENGTH = 32;

    private final String value;

    private BusSecret(String value) {
        this.value = value;
    }

    /** The secret as written, without the spaces around it, which are easy to copy by accident. */
    public static BusSecret of(String written) {
        return new BusSecret(Objects.requireNonNull(written, "written").strip());
    }

    /** Whether nothing was written. */
    public boolean blank() {
        return value.isEmpty();
    }

    /** Whether the secret is long enough to sign with. */
    public boolean usable() {
        return value.length() >= MIN_LENGTH;
    }

    /** The key bytes a signature is made with. */
    byte[] key() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return blank() ? "BusSecret[blank]" : usable() ? "BusSecret[set]" : "BusSecret[too short]";
    }
}
