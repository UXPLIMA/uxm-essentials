package com.uxplima.uxmessentials.security.adapter;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.security.application.SessionCarry.Arrival;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.RemoteSyncListener;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import org.jspecify.annotations.NullMarked;

/**
 * What the proxy plugin said about how each player here reached this backend: a new login, or a switch on the proxy
 * connection they already had. The proxy says it in a {@link ProxyConnectionFrame} sealed with the network secret
 * and sent over the player's own connection, right after the backend has let them in, so a join decision that needs
 * it waits for it a short while.
 *
 * <p>Without the proxy plugin answering on the bus, no frame comes, and an arrival is {@link Arrival.NoProxy}.
 */
@NullMarked
public final class ProxyArrivals implements RemoteSyncListener {

    /** How long a join decision waits for the proxy's word before it counts the arrival as unconfirmed. */
    static final Duration WAIT = Duration.ofSeconds(3);

    private final Scheduler scheduler;
    private final BooleanSupplier proxyPresent;
    private final Map<UUID, ProxyConnectionFrame> heard = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Arrival>> waiting = new ConcurrentHashMap<>();

    /** Arrivals heard through the bus; {@code proxyPresent} says whether the proxy plugin answers on it now. */
    public ProxyArrivals(Scheduler scheduler, BooleanSupplier proxyPresent) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.proxyPresent = Objects.requireNonNull(proxyPresent, "proxyPresent");
    }

    @Override
    public void onRemoteChange(NetworkMessage message) {
        if (!(message instanceof ProxyConnectionFrame frame)
                || !frame.originServer().equals(ProxyConnectionFrame.PROXY)) {
            return;
        }
        heard.put(frame.player(), frame);
        CompletableFuture<Arrival> asked = waiting.remove(frame.player());
        if (asked != null) {
            asked.complete(arrival(frame));
        }
    }

    /**
     * How {@code player}, who just joined, arrived: once the proxy has said so, or as unconfirmed when it has not
     * within {@link #WAIT}. Never completes exceptionally.
     */
    public CompletableFuture<Arrival> arrival(UUID player) {
        Objects.requireNonNull(player, "player");
        if (!proxyPresent.getAsBoolean()) {
            return CompletableFuture.completedFuture(new Arrival.NoProxy());
        }
        ProxyConnectionFrame known = heard.get(player);
        if (known != null) {
            return CompletableFuture.completedFuture(arrival(known));
        }
        CompletableFuture<Arrival> answer = new CompletableFuture<>();
        waiting.put(player, answer);
        // The frame may have landed between the read above and the wait being registered.
        ProxyConnectionFrame late = heard.get(player);
        if (late != null && waiting.remove(player, answer)) {
            answer.complete(arrival(late));
            return answer;
        }
        try {
            scheduler.asyncAfter(WAIT, () -> {
                if (waiting.remove(player, answer)) {
                    answer.complete(new Arrival.Unconfirmed());
                }
            });
        } catch (RuntimeException refused) {
            // The scheduler refuses work while the plugin stops.
            waiting.remove(player, answer);
            answer.complete(new Arrival.Unconfirmed());
        }
        return answer;
    }

    /** The proxy connection {@code player} is on here, when the proxy said. */
    public Optional<UUID> connection(UUID player) {
        return Optional.ofNullable(heard.get(Objects.requireNonNull(player, "player")))
                .map(ProxyConnectionFrame::connection);
    }

    /** {@code player} left: what the proxy said about this visit no longer holds. */
    public void left(UUID player) {
        heard.remove(Objects.requireNonNull(player, "player"));
    }

    private static Arrival arrival(ProxyConnectionFrame frame) {
        return frame.switched() ? new Arrival.ProxySwitch(frame.connection()) : new Arrival.ProxyLogin();
    }
}
