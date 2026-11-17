package com.uxplima.uxmessentials.messaging.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.domain.LastConversation;

/**
 * Where each player's last private message partner, the one {@code /reply} answers, is kept between backends: the
 * shared database. A port because it is a storage boundary. Both calls do I/O and run off the tick.
 */
public interface ReplyPartnerStore {

    /** The last conversation of {@code owner}, if they had one. */
    Optional<LastConversation> load(UUID owner);

    /** Store {@code conversation} as the last conversation of {@code owner}, replacing the one before. */
    void save(UUID owner, LastConversation conversation);
}
