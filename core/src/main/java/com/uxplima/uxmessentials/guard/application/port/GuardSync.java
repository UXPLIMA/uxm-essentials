package com.uxplima.uxmessentials.guard.application.port;

import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.RegionKey;

/** Tells the other servers that load a scope that one of its regions changed. A no-op on a single server. */
public interface GuardSync {

    /** Announce a change after it is stored. */
    void changed(GuardScope scope, RegionKey key);

    /** The sync that tells nobody. */
    GuardSync NONE = (scope, key) -> {};
}
