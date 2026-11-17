package com.uxplima.uxmessentials.moderation.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * How a sanction's reason reads to one reader. A reason a staff member typed reads as typed. A reason the server drew
 * from a catalog line (a warn ladder step) is drawn again for each reader, in their language and with the line as the
 * operator has it now, so a staff member reading {@code /history} in Turkish does not see the English the punished
 * player read. Every place that shows a sanction's reason goes through here.
 *
 * <p>A reader with no language of their own (the API, the console) reads the server's default language.
 */
public final class SanctionReasons {

    /**
     * The longest reason a sanction shows: the width of the reason columns its rows are stored in. A drawn reason
     * quotes a staff member's warning, so without this a long warning would make a reason no row could hold, and a
     * reader would see more than the row keeps.
     */
    public static final int MAX_REASON = 256;

    /** The reader for a reason read by no one in particular: the API, an event, the console. */
    public static final PlayerRef NO_READER = PlayerRef.system("server");

    private SanctionReasons() {}

    /** {@code text}, or {@code drawn} drawn for {@code reader} when the server wrote the reason. */
    public static Optional<String> read(
            Messages messages, PlayerRef reader, Optional<String> text, Optional<DrawnReason> drawn) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(drawn, "drawn");
        return drawn.map(reason -> capped(messages.resolve(reader, reason.line(), reason.args())))
                .or(() -> text);
    }

    /**
     * {@link #read} for a reason filled into another line: a drawn reason is catalogue markup and goes in as written
     * ({@link TrustedMarkup}), a typed one is escaped there like any other value. Empty when there is no reason.
     */
    public static String shown(
            Messages messages, PlayerRef reader, Optional<String> text, Optional<DrawnReason> drawn) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(drawn, "drawn");
        return drawn.map(reason -> TrustedMarkup.of(capped(messages.resolve(reader, reason.line(), reason.args()))))
                .or(() -> text)
                .orElse("");
    }

    /** {@code reason} cut to {@link #MAX_REASON} characters, never splitting a surrogate pair. */
    public static String capped(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason.length() <= MAX_REASON) {
            return reason;
        }
        int end = Character.isHighSurrogate(reason.charAt(MAX_REASON - 1)) ? MAX_REASON - 1 : MAX_REASON;
        return reason.substring(0, end);
    }
}
