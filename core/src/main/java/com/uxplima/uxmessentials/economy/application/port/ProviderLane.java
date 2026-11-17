package com.uxplima.uxmessentials.economy.application.port;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Where a sweep calls the plugin that keeps a currency's balances, one player at a time. A Vault economy expects its
 * calls on the server thread, so its lane runs them there a few per tick and the tick never stalls; an economy with
 * an asynchronous API, or a currency backend that moves to the thread it needs by itself, is called in place.
 */
public interface ProviderLane {

    /**
     * Apply {@code work} to each of {@code owners} in order on this lane and wait for them. One result per owner, in
     * order; an owner the lane gave up on before calling {@code work} (the server is stopping) yields
     * {@code notRun}. {@code work} handles its own failures.
     */
    <T> List<T> each(List<PlayerRef> owners, Function<PlayerRef, T> work, T notRun);

    /** Calls in place, on the caller's thread. */
    static ProviderLane inPlace() {
        return new ProviderLane() {
            @Override
            public <T> List<T> each(List<PlayerRef> owners, Function<PlayerRef, T> work, T notRun) {
                List<T> results = new ArrayList<>(owners.size());
                for (PlayerRef owner : owners) {
                    results.add(work.apply(owner));
                }
                return results;
            }
        };
    }
}
