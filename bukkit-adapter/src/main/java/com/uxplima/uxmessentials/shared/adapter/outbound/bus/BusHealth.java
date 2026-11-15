package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.NullMarked;

/**
 * A read-only view of the cross-server bus's runtime state for {@code /uxmess doctor}: whether this backend opts
 * into network sync, which transport carries the bus, and what each carrier in use can show about reaching peers.
 * It is the diagnostic seam the bus exposes <em>without</em> handing out the {@link BusCore} or the transport
 * itself, mirroring how {@link Bus} exposes only the publish/register seams.
 *
 * <p>The two carriers prove different things. A Redis subscribe connection that is up reaches the broker every peer
 * subscribes to, so its flag is the answer. The proxy carrier sends frames whether or not anything on the proxy
 * relays them, so all it can show is when a frame last came back through the broker. Every read is a cheap
 * volatile-field read, never a blocking connect, so the doctor may call it on the run thread. A disabled backend
 * reports {@code enabled() == false} and the doctor reads nothing else.
 */
@NullMarked
public interface BusHealth {

    /** True when this backend participates in network sync ({@code network.enabled = true}). */
    boolean enabled();

    /** The configured transport carrying the bus, lower-cased: {@code velocity}, {@code redis}, or {@code both}. */
    String transport();

    /** The proxy carrier's state, empty when {@code network.transport} does not use the proxy. */
    Optional<ProxyLink> proxy();

    /** Whether the Redis subscribe connection is up, empty when {@code network.transport} does not use Redis. */
    Optional<Boolean> redisConnected();

    /** How long the proxy may stay silent before it no longer counts as answering: three heartbeats. */
    Duration answerWindow();

    /**
     * The proxy carrier at one moment.
     *
     * @param registered whether the plugin-messaging channel is registered, false until the bus starts
     * @param lastAnswerMillis when a frame last arrived through the proxy broker, in epoch milliseconds, empty
     *     when none has since the bus started
     */
    record ProxyLink(boolean registered, OptionalLong lastAnswerMillis) {

        public ProxyLink {
            Objects.requireNonNull(lastAnswerMillis, "lastAnswerMillis");
        }

        /** Whether the channel is up and the proxy answered no longer than {@code window} before {@code now}. */
        public boolean answeredWithin(long now, Duration window) {
            return registered
                    && lastAnswerMillis.isPresent()
                    && now - lastAnswerMillis.getAsLong() <= window.toMillis();
        }
    }
}
