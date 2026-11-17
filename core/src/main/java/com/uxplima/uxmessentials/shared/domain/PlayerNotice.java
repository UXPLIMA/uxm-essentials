package com.uxplima.uxmessentials.shared.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.message.MessageLine;

/**
 * A catalogue line for a player who was not online when it was meant for them, kept until they next are.
 *
 * @param recipient the player it is for
 * @param line the line and the values it is filled with, rendered in the recipient's language when it is shown
 * @param at when it was kept
 */
public record PlayerNotice(UUID recipient, MessageLine line, Instant at) {

    public PlayerNotice {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(at, "at");
    }
}
