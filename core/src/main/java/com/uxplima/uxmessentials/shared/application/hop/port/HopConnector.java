package com.uxplima.uxmessentials.shared.application.hop.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port to the proxy that moves a player between backends (a platform boundary). The connect is fire and
 * forget: nothing comes back to say whether the proxy acted, which is why every hop has a watchdog.
 */
public interface HopConnector {

    /** Whether a connect request can leave this backend at all. It cannot say whether a proxy is listening. */
    boolean isAvailable();

    /** On {@code player}'s region thread: ask the proxy to move them to {@code server}; a no-op once they left. */
    void connect(PlayerRef player, String server);
}
