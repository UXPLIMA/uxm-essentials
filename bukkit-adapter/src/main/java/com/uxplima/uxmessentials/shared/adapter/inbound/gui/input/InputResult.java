package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The raw outcome a {@link TextInputBackend} reports: the player either {@link Submitted submitted} a line of text or
 * {@link Cancelled cancelled} it structurally (closed the anvil, or it could not open). This mirrors the uxmLib
 * {@code AnvilResult} shape so the anvil backend maps one-to-one, while keeping the seam's own type at the boundary
 * so a call site never imports a uxmLib class. The cancel-keyword check lives one layer up in {@link TextInput}, which
 * turns a {@code Submitted} whose text is a cancel keyword into a cancellation, so both backends share one policy.
 */
@NullMarked
public sealed interface InputResult permits InputResult.Submitted, InputResult.Cancelled {

    /** The player submitted {@code text} (possibly empty); not yet checked against the cancel keywords. */
    record Submitted(String text) implements InputResult {
        public Submitted {
            Objects.requireNonNull(text, "text");
        }
    }

    /** The prompt was dismissed without a submission. */
    record Cancelled() implements InputResult {
        public static final Cancelled INSTANCE = new Cancelled();
    }
}
