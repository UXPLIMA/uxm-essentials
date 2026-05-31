package com.uxplima.uxmessentials.moderation.domain.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A connecting player's IP matched an existing sanction or the known-alt set at login. The {@code matched}
 * UUIDs are the other accounts sharing the connecting IP; {@code kicked} is whether the login was refused
 * (an active IP ban on the address) or merely flagged for the audit trail.
 *
 * @param uuid the connecting player's UUID
 * @param ip the connecting address
 * @param matched the other UUIDs sharing this IP
 * @param kicked true when the login was refused, false when only flagged
 */
public record AltDetected(UUID uuid, String ip, List<UUID> matched, boolean kicked) implements ModerationEvent {

    public AltDetected {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(ip, "ip");
        matched = List.copyOf(Objects.requireNonNull(matched, "matched"));
    }
}
