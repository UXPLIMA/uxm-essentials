package com.uxplima.uxmessentials.shared.application.hop.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The teleport warmups still counting down on this backend. A player sent to another backend has started a new
 * teleport, so whatever they were waiting on here must not land behind them; a paid one is given back through its
 * own settlement. It is a port because the teleport context tracks those warmups and the shared hop must not depend
 * on it.
 */
public interface LocalWarmups {

    /** A backend with no teleport warmups to end. */
    LocalWarmups NONE = who -> {};

    /** On {@code who}'s region thread: stop their running warmup, if any, as their next teleport would. */
    void supersede(PlayerRef who);
}
