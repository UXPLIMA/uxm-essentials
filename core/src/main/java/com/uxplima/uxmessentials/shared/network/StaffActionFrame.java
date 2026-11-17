package com.uxplima.uxmessentials.shared.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A staff action on a player who is connected to another backend, or a staff notice for the staff on every backend.
 * The staff member's own backend checks the permission and decides; the frame carries the part only the target's
 * backend can carry out, since only it holds the player's connection.
 *
 * <p>A frame names the backend it is for in {@code toServer}; every other backend ignores it. A notice is for every
 * backend and names none; any other action that names none is for no backend. The values are the line's placeholders or the action's arguments, never more than
 * {@link #MAX_VALUES} of them.
 *
 * @param originServer the backend the staff member is on
 * @param frameId identifies the frame, so a copy delivered twice acts once
 * @param action what to do, by {@link Action} name; a name this backend does not know is ignored
 * @param toServer the backend that carries it out, or blank for every backend
 * @param target the player it is about
 * @param targetName the player's name when it was sent
 * @param actorName who asked, for the target's backend to name in its log
 * @param key the message key a notice shows, or blank
 * @param values the placeholders or arguments
 */
public record StaffActionFrame(
        String originServer,
        UUID frameId,
        String action,
        String toServer,
        UUID target,
        String targetName,
        String actorName,
        String key,
        Map<String, String> values)
        implements NetworkMessage {

    /** The most values one frame carries. */
    public static final int MAX_VALUES = 32;

    /** The actions a frame carries. */
    public enum Action {
        /** Disconnect the target, with {@code reason}. */
        KICK,
        /** Send the target back through second-factor verification now. */
        SECURITY_FORCE,
        /** Apply the revoked-access policy to the target, whose second factor staff just cleared. */
        SECURITY_REVOKED,
        /** Toggle the target's staff mode. */
        STAFF_MODE,
        /** Answer which client brand the target reported, with a {@link #CLIENT_BRAND_ANSWER} to the origin. */
        CLIENT_BRAND_ASK,
        /** The brand the target reported, in {@code brand}; absent when they reported none. */
        CLIENT_BRAND_ANSWER,
        /** Show the line {@code key} to the local holders of {@code node}, with the other values as placeholders. */
        STAFF_NOTICE;

        /** The action named {@code name}, or empty for one a newer backend added. */
        public static Optional<Action> named(String name) {
            for (Action action : values()) {
                if (action.name().equals(name)) {
                    return Optional.of(action);
                }
            }
            return Optional.empty();
        }
    }

    public StaffActionFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(toServer, "toServer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(actorName, "actorName");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_VALUES) {
            throw new IllegalArgumentException("a staff action carries at most " + MAX_VALUES + " values");
        }
        values = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Whether this frame is for {@code server}: it names that backend, or it is a notice, which is for every backend.
     * An action that names no backend is for none, so a frame cannot act on a player everywhere at once.
     */
    public boolean isFor(String server) {
        return toServer.isEmpty() ? Action.STAFF_NOTICE.name().equals(action) : toServer.equals(server);
    }

    @Override
    public MessageType type() {
        return MessageType.STAFF_ACTION;
    }
}
