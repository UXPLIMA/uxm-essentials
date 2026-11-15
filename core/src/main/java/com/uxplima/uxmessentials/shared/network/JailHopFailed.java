package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A jailed player could not be taken to their jail on another backend, seen by the origin backend. Every receiver
 * tells its own staff, in each reader's language, since the staff member who ran the jail may be anywhere on the
 * network. The player stays jailed: the record in the shared database is applied again when they next join.
 *
 * @param originServer the backend that saw the hop fail
 * @param frameId identifies this report, so a copy delivered twice is shown once
 * @param target the jailed player
 * @param targetName the jailed player's name, for the staff line
 * @param jail the jail they were being taken to
 */
public record JailHopFailed(String originServer, UUID frameId, UUID target, String targetName, String jail)
        implements NetworkMessage {

    public JailHopFailed {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(jail, "jail");
    }

    @Override
    public MessageType type() {
        return MessageType.JAIL_HOP_FAILED;
    }
}
