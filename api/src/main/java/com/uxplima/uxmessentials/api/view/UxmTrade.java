package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.UUID;

/**
 * A trade that is open right now.
 *
 * <p>The two sides are named by the roles the trade itself gives them: the initiator opened it, the partner
 * accepted. Which of them you asked about is something you already know.
 *
 * <p>The confirmation flags are the current ones, and they do not stick: changing an offer clears both, which is
 * the anti-scam rule the window enforces. A snapshot read a moment before a change reports the state as it was.
 *
 * @param id the trade's own id
 * @param initiatorId the player who opened the trade
 * @param initiatorName their name at the time the trade opened
 * @param partnerId the player who accepted it
 * @param partnerName their name at the time the trade opened
 * @param initiatorConfirmed whether the initiator has confirmed the offers as they stand
 * @param partnerConfirmed whether the partner has confirmed the offers as they stand
 */
public record UxmTrade(
        UUID id,
        UUID initiatorId,
        String initiatorName,
        UUID partnerId,
        String partnerName,
        boolean initiatorConfirmed,
        boolean partnerConfirmed) {

    public UxmTrade {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(initiatorId, "initiatorId");
        Objects.requireNonNull(initiatorName, "initiatorName");
        Objects.requireNonNull(partnerId, "partnerId");
        Objects.requireNonNull(partnerName, "partnerName");
    }

    /** Whether both sides have confirmed, which is the moment before the swap. */
    public boolean bothConfirmed() {
        return initiatorConfirmed && partnerConfirmed;
    }
}
