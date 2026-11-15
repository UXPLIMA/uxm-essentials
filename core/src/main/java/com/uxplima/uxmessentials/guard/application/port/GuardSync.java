package com.uxplima.uxmessentials.guard.application.port;

import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.shared.domain.ServerScope;

/** Tells the other servers that load a scope that one of its regions changed. A no-op on a single server. */
public interface GuardSync {

    /** Announce a change after it is stored. */
    void changed(ServerScope scope, RegionKey key);

    /** The sync that tells nobody. */
    GuardSync NONE = (scope, key) -> {};
}
