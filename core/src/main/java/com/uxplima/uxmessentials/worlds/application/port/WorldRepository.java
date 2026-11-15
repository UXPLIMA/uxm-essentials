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
 * backend, and a world no backend has claimed yet, is neither found, listed, saved over nor deleted here.
 */
public interface WorldRepository {

    Optional<ManagedWorld> find(WorldName name);

    List<ManagedWorld> all();

    boolean exists(WorldName name);

    void save(ManagedWorld world);

    void delete(WorldName name);

    /** Every managed world of the network with the backend that runs it, this backend's and unclaimed ones included. */
    List<NetworkWorld> network();
}
