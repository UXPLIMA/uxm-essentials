package com.uxplima.uxmessentials.worlds.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Persistence of world metadata (the {@code world} table), shared by every backend of a network. DB-backed, cached.
 *
 * <p>Every method but {@link #network()} sees only the worlds this backend runs: a world of the same name on another
 * backend, and a world no backend has claimed yet, is neither found, listed nor saved over here.
 */
public interface WorldRepository {

    Optional<ManagedWorld> find(WorldName name);

    List<ManagedWorld> all();

    boolean exists(WorldName name);

    void save(ManagedWorld world);

    /**
     * Delete this backend's world {@code name}. A backend that records its server also drops the unclaimed template of
     * that name, which the start-up claim would otherwise copy back onto it at the next start.
     */
    void delete(WorldName name);

    /** Every managed world of the network with the backend that runs it, this backend's and unclaimed ones included. */
    List<NetworkWorld> network();
}
