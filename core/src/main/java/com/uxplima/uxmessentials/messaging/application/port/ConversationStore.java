package com.uxplima.uxmessentials.messaging.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the {@code /reply} targets, kept in the database every backend shares, held in memory for the players online here, so a reply still finds
 * its partner after a move to another backend. It records
 * each player's {@link LastConversation} (their last PM partner and when it was last touched), captured both
 * when they send and when they receive a message, so either side can {@code /reply}.
 *
 * <p>Reads never touch the database, so they are safe on the command thread, and a write from either side of a
 * delivery is written back off the tick. The reply-TTL rule lives on the
 * {@link LastConversation} aggregate; this port only stores and returns the latest entry.
 */
public interface ConversationStore {

    /** Record that {@code owner}'s most recent conversation is {@code conversation}, replacing any prior. */
    void remember(PlayerRef owner, LastConversation conversation);

    /** The owner's most recent conversation, if any has been recorded. */
    Optional<LastConversation> latest(PlayerRef owner);

    /** Forget the owner's reply target, e.g. on quit. */
    void forget(PlayerRef owner);
}
