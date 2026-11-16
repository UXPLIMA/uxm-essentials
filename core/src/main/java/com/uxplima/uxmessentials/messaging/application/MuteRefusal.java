package com.uxplima.uxmessentials.messaging.application;

import java.util.Optional;

import com.uxplima.uxmessentials.messaging.application.port.MuteAnswer;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;

/** The refusal a sender's mute answer calls for: none when they are known not to be muted. */
final class MuteRefusal {

    private MuteRefusal() {}

    /** A mute that could not be read refuses too, so a muted sender is never let through by a failed read. */
    static Optional<MessagingError> of(MuteAnswer answer) {
        return switch (answer) {
            case NOT_MUTED -> Optional.empty();
            case MUTED -> Optional.of(MessagingError.SENDER_MUTED);
            case UNKNOWN -> Optional.of(MessagingError.MUTE_UNCHECKED);
        };
    }
}
