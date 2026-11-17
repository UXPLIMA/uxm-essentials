package com.uxplima.uxmessentials.security.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.security.application.SessionCarry;
import com.uxplima.uxmessentials.security.application.SessionCarry.Arrival;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.CarrierReceipts;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.RemoteSyncListener;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 * <p>A frame counts for a join only when it names this backend, arrived over the connection of the player it is about
 * ({@link CarrierReceipts}), and arrived after that join began: what the proxy said about an earlier visit never
 * speaks for a later one.
 *
 * <p>Once any backend of the network has heard from the proxy plugin, it is remembered in the shared store, and every
 * arrival waits for the proxy's word from then on: a proof is never carried on the address alone on a network that
 * runs the proxy plugin. Until that is read at start, the proxy counts as seen. Only a network that has never heard
 * from it gets {@link Arrival.NoProxy}.
 */
@NullMarked
public final class ProxyArrivals implements RemoteSyncListener {

    /** How long a join decision waits for the proxy's word before it counts the arrival as unconfirmed. */
    static final Duration WAIT = Duration.ofSeconds(3);

    private final Scheduler scheduler;
    private final CarrierReceipts receipts;
    private final String serverId;
    private final BooleanSupplier proxyAnswering;
    private final SessionCarry carry;
    private final Logger log;
    private final Clock clock;

    /** UUID to when their current join began, on the clock of the receipts. */
    private final Map<UUID, Long> joins = new ConcurrentHashMap<>();

    private final Map<UUID, Heard> heard = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Arrival>> waiting = new ConcurrentHashMap<>();
    private final Object seenLock = new Object();

    /** Whether the network has heard from the proxy plugin; true until the shared store says otherwise. */
    private boolean seen = true;

    /** Whether the shared store already holds that the proxy was heard, so it is written once. */
    private boolean stored;

    /**
     * Arrivals heard through the bus by {@code serverId}. {@code proxyAnswering} says whether the proxy answers on the
     * bus now; {@code carry} keeps whether it ever did.
     */
    public ProxyArrivals(
            Scheduler scheduler,
            CarrierReceipts receipts,
            String serverId,
            BooleanSupplier proxyAnswering,
            SessionCarry carry,
            Logger log,
            Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.proxyAnswering = Objects.requireNonNull(proxyAnswering, "proxyAnswering");
        this.carry = Objects.requireNonNull(carry, "carry");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Read whether the network has ever heard from the proxy plugin. Does I/O: run it off the tick, at start. */
    public void load() {
        boolean known = carry.proxySeen();
        synchronized (seenLock) {
            if (known) {
                stored = true;
            } else if (!stored) {
                seen = false;
            }
        }
    }

    /** {@code player}'s join begins now: nothing the proxy said before it speaks for it. Call it on their thread. */
    public void joined(UUID player) {
        Objects.requireNonNull(player, "player");
        joins.put(player, receipts.now());
        heard.remove(player);
    }

    @Override
    public void onRemoteChange(NetworkMessage message) {
        if (!(message instanceof ProxyConnectionFrame frame)
                || !frame.originServer().equals(ProxyConnectionFrame.PROXY)) {
            return;
        }
        Optional<CarrierReceipts.Receipt> receipt = receipts.take(frame.frameId());
        if (receipt.isEmpty()
                || !receipt.get().carrier().equals(frame.player())
                || !frame.server().equals(serverId)) {
            return;
        }
        heardFromProxy();
        Heard word = new Heard(frame, receipt.get().receivedNanos());
        heard.merge(frame.player(), word, (old, now) -> now.receivedNanos() - old.receivedNanos() >= 0 ? now : old);
        CompletableFuture<Arrival> asked = waiting.get(frame.player());
        if (asked != null && current(frame.player(), word) && waiting.remove(frame.player(), asked)) {
            asked.complete(arrival(frame));
        }
    }

    /**
     * How {@code player}, who just joined, arrived: once the proxy has said so, or as unconfirmed when it has not
     * within {@link #WAIT}. Never completes exceptionally.
     */
    public CompletableFuture<Arrival> arrival(UUID player) {
        Objects.requireNonNull(player, "player");
        if (proxyAnswering.getAsBoolean()) {
            heardFromProxy();
        } else if (!proxySeen()) {
            return CompletableFuture.completedFuture(new Arrival.NoProxy());
        }
        Optional<ProxyConnectionFrame> known = said(player);
        if (known.isPresent()) {
            return CompletableFuture.completedFuture(arrival(known.get()));
        }
        CompletableFuture<Arrival> answer = new CompletableFuture<>();
        waiting.put(player, answer);
        // The frame may have landed between the read above and the wait being registered.
        Optional<ProxyConnectionFrame> late = said(player);
        if (late.isPresent() && waiting.remove(player, answer)) {
            answer.complete(arrival(late.get()));
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

    /** The proxy connection {@code player} is on here, when the proxy said so for their current join. */
    public Optional<UUID> connection(UUID player) {
        return said(Objects.requireNonNull(player, "player")).map(ProxyConnectionFrame::connection);
    }

    /** {@code player} left: what the proxy said about this visit no longer holds. */
    public void left(UUID player) {
        Objects.requireNonNull(player, "player");
        joins.remove(player);
        heard.remove(player);
    }

    private Optional<ProxyConnectionFrame> said(UUID player) {
        Heard word = heard.get(player);
        return word != null && current(player, word) ? Optional.of(word.frame()) : Optional.empty();
    }

    /** Whether {@code word} arrived during {@code player}'s current join. */
    private boolean current(UUID player, Heard word) {
        Long began = joins.get(player);
        return began != null && word.receivedNanos() - began >= 0;
    }

    private boolean proxySeen() {
        synchronized (seenLock) {
            return seen;
        }
    }

    /** The proxy plugin answered: remember it here, and once in the shared store for every backend. */
    private void heardFromProxy() {
        synchronized (seenLock) {
            seen = true;
            if (stored) {
                return;
            }
            stored = true;
        }
        try {
            scheduler.async(() -> {
                try {
                    carry.proxyHeard(clock.instant());
                } catch (RuntimeException failure) {
                    synchronized (seenLock) {
                        stored = false;
                    }
                    log.error("event=security_proxy_seen_unwritten", failure);
                }
            });
        } catch (RuntimeException refused) {
            synchronized (seenLock) {
                stored = false;
            }
        }
    }

    private static Arrival arrival(ProxyConnectionFrame frame) {
        return frame.switched() ? new Arrival.ProxySwitch(frame.connection()) : new Arrival.ProxyLogin();
    }

    /** One word from the proxy and when it arrived, on the clock of the receipts. */
    private record Heard(ProxyConnectionFrame frame, long receivedNanos) {}
}
