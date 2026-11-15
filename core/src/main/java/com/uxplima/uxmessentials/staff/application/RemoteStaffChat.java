package com.uxplima.uxmessentials.staff.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A staff chat line sent on one backend, for the staff on the others.
 *
 * @param id identifies the line
 * @param from who sent it
 * @param message the line as typed
 */
public record RemoteStaffChat(UUID id, PlayerRef from, String message) {

    public RemoteStaffChat {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(message, "message");
    }
}
