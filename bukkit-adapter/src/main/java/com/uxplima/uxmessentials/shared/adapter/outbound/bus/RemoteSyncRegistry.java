package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The set of {@link RemoteSyncListener}s the bus client dispatches an inbound frame to. Contexts register
 * their listener as they wire (all on the main thread at enable, before any frame can arrive); the bus reads
 * the set when a frame lands. Backed by a {@link CopyOnWriteArrayList} so registration during wiring and the
 * off-tick dispatch never need a shared lock: registration is rare and dispatch is read-only.
 *
 * <p>This is the single seam every context opts into cross-server sync through: homes and economy register
 * here today; warps and vaults register the same way with no change to the bus. The registry is owned by the
 * bus wiring and handed to each context so a disabled bus still accepts registrations harmlessly (they are
 * simply never invoked).
 */
@NullMarked
public final class RemoteSyncRegistry {

    private final List<RemoteSyncListener> listeners = new CopyOnWriteArrayList<>();
    private final CarrierReceipts receipts = new CarrierReceipts(System::nanoTime);
    private final List<Runnable> resyncs = new CopyOnWriteArrayList<>();
    private final List<Runnable> playerCacheDrops = new CopyOnWriteArrayList<>();

    /** Which connection carried each proxy frame these listeners hear, for the listener that must know. */
    public CarrierReceipts receipts() {
        return receipts;
    }

    /** Add a listener the bus invokes for every inbound frame from a peer. */
    public void register(RemoteSyncListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Add a reload of a set cache a peer can change (warps, NPCs, the vote counter), run by
     * {@link SharedCacheRefresh} on the async lane when this backend may have missed frames: on its first join after
     * an empty spell and on the safety timer. It reads the shared database, so it must not touch the Bukkit API, and
     * it must keep the old set readable until the fresh one is in, or tick-thread readers load on their own.
     */
    public void registerResync(Runnable resync) {
        resyncs.add(Objects.requireNonNull(resync, "resync"));
    }

    /**
     * Add a reset of a per-player cache a peer can change (wallets, homes, vaults, ranks). It runs only on the first
     * join after an empty spell, on the joining player's thread before the join handlers warm that player, so it must
     * not read the database there: an in-memory drop, or a refresh that hands its read to the async lane. The timer
     * leaves these caches to their own expiry.
     */
    public void registerPlayerCacheDrop(Runnable drop) {
        playerCacheDrops.add(Objects.requireNonNull(drop, "drop"));
    }

    /** Run every registered resync; one failing never starves the others. */
    void resync(Consumer<RuntimeException> onFailure) {
        runAll(resyncs, onFailure);
    }

    /** Run every registered per-player cache drop; one failing never starves the others. */
    void dropPlayerCaches(Consumer<RuntimeException> onFailure) {
        runAll(playerCacheDrops, onFailure);
    }

    private static void runAll(List<Runnable> tasks, Consumer<RuntimeException> onFailure) {
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (RuntimeException failure) {
                onFailure.accept(failure);
            }
        }
    }

    /** Invoke every registered listener for {@code message}; one failing never starves the others. */
    void dispatch(NetworkMessage message, FailureSink onFailure) {
        for (RemoteSyncListener listener : listeners) {
            try {
                listener.onRemoteChange(message);
            } catch (RuntimeException failure) {
                onFailure.onFailure(message, failure);
            }
        }
    }

    /** How the bus reports a listener that threw, so the registry need not know about logging. */
    @FunctionalInterface
    interface FailureSink {
        void onFailure(NetworkMessage message, RuntimeException failure);
    }
}
