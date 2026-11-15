package com.uxplima.uxmessentials.teleport.application;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;

/** What one backend tells another about a teleport that spans both, carried by the {@code TeleportNetwork} port. */
public sealed interface RemoteTeleport {

    /** The backend that sent the message. */
    String fromServer();

    /**
     * A request sent to a player on {@code targetServer}.
     *
     * @param seeLevel the requester's vanish see level on their own backend
     * @param ttl how long the request stays open on both backends
     */
    record Request(
            String fromServer,
            RequestId id,
            PlayerRef requester,
            PlayerRef target,
            String targetServer,
            RequestDirection direction,
            int seeLevel,
            Duration ttl)
            implements RemoteTeleport {

        public Request {
            Objects.requireNonNull(fromServer, "fromServer");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(targetServer, "targetServer");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(ttl, "ttl");
        }
    }

    /**
     * One step of a request or a staff teleport, for {@code toServer}.
     *
     * @param id the request or staff teleport it belongs to
     * @param about the player the reader's line names
     * @param detail extra text for that line, such as a server name; empty when there is none
     */
    record Answer(String fromServer, UUID id, Step step, String toServer, PlayerRef about, String detail)
            implements RemoteTeleport {

        public Answer {
            Objects.requireNonNull(fromServer, "fromServer");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(toServer, "toServer");
            Objects.requireNonNull(about, "about");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * A staff teleport of a player on {@code subjectServer} to {@code destination}, wherever that player is.
     *
     * @param actor the staff member, or the console, who reads the outcome
     */
    record StaffMove(
            String fromServer, UUID id, PlayerRef actor, PlayerRef subject, PlayerRef destination, String subjectServer)
            implements RemoteTeleport {

        public StaffMove {
            Objects.requireNonNull(fromServer, "fromServer");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(subjectServer, "subjectServer");
        }
    }

    /**
     * The steps an {@link Answer} names. Carried as text, so the names are part of the wire format: a step is added
     * at the end and never renamed, and a peer drops a name it does not know.
     */
    enum Step {
        /** The target's backend showed the request to its player. */
        DELIVERED,
        /** The target is not online there, or is vanished from the requester. */
        OFFLINE,
        /** The target refuses all requests. */
        TOGGLED_OFF,
        /** The target blocked the requester. */
        BLOCKED,
        /** The target accepted. */
        ACCEPTED,
        /** The target denied. */
        DENIED,
        /** Nobody answered the request in time. */
        EXPIRED,
        /** The requester's backend took the request back. */
        WITHDRAWN,
        /** A staff teleport moved its subject on the subject's own backend. */
        MOVED,
        /** A staff teleport sent its subject on to the destination's backend. */
        SENT,
        /** The teleport rules refused a staff teleport's subject. */
        REFUSED,
        /** A staff teleport's subject is not online on the backend it was sent to. */
        SUBJECT_OFFLINE,
        /** A staff teleport's destination player is not online anywhere the subject's backend knows. */
        DESTINATION_OFFLINE
    }
}
