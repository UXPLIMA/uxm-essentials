package com.uxplima.uxmessentials.bootstrap.health;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusHealth;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusSecret;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * Reports whether the cross-server bus actually reaches the network, for {@code /uxmess doctor}, one carrier at a
 * time. A disabled backend ({@code network.enabled = false}, the default) reports {@code OK} as "disabled".
 *
 * <p>The proxy carrier needs {@code network.bus.secret}: without it the carrier is off, which is a {@code WARN} that
 * names the key. It is connected only once a signed frame came back through the {@code uxmEssentials-velocity} broker
 * within the answer window. A registered channel is not enough: frames leave a backend whether or not anything on
 * the proxy relays them, so a proxy without the broker would otherwise read connected while nothing syncs. The
 * broker returns each heartbeat to its sender, so a backend alone behind the proxy still sees the answer. The Redis
 * carrier is connected while its subscribe connection is up. With {@code transport = both} each carrier gets its
 * own verdict, and either one silent is a {@code WARN}: the backends reached only through it are cut off. The one
 * exception is a proxy that is quiet only because no player is online here to carry a frame: that reason is given
 * first, and it is a {@code WARN} on the proxy alone but not while Redis carries the bus. Frames refused for a wrong
 * signature or clock within the answer window are a {@code WARN} too: a backend with another secret is cut off, and a
 * client sending forged frames shows the same way.
 *
 * <p>Every read is an in-memory volatile read, never a blocking connect, so the check is safe on the doctor thread.
 */
@NullMarked
public final class BusTransportHealthCheck implements HealthCheck {

    private final BusHealth bus;
    private final LongSupplier clock;

    public BusTransportHealthCheck(BusHealth bus) {
        this(bus, System::currentTimeMillis);
    }

    BusTransportHealthCheck(BusHealth bus, LongSupplier clock) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "cross-server-bus";
    }

    @Override
    public HealthResult check() {
        if (!bus.enabled()) {
            return HealthResult.ok("disabled (network.enabled = false); running local-only");
        }
        long now = clock.getAsLong();
        List<String> parts = new ArrayList<>();
        boolean proxyWorking = true;
        boolean proxyIdle = false;
        boolean proxyRefusing = false;
        if (bus.proxy().isPresent()) {
            BusHealth.ProxyLink link = bus.proxy().get();
            proxyWorking = link.answeredWithin(now, bus.answerWindow());
            proxyIdle = !proxyWorking && link.registered() && link.waitingForPlayer();
            proxyRefusing = link.refusedWithin(now, bus.answerWindow());
            parts.add(proxyLine(link, now));
            if (proxyRefusing) {
                parts.add(refusedLine(link, now));
            }
        }
        boolean redisWorking = true;
        if (bus.redisConnected().isPresent()) {
            redisWorking = bus.redisConnected().get();
            parts.add(
                    redisWorking
                            ? "redis connected"
                            : "redis DISCONNECTED. Check network.redis and that uxmEssentials-redis is in plugins/");
        }
        // A proxy that is quiet only because nobody here can carry a frame is not a fault while Redis carries the bus;
        // on the proxy alone nothing travels, so it stays a warning.
        boolean redisCarries = bus.redisConnected().isPresent() && redisWorking;
        boolean working = (proxyWorking || (proxyIdle && redisCarries)) && redisWorking && !proxyRefusing;
        String message = "enabled, transport=" + bus.transport() + ", " + String.join("; ", parts);
        return working ? HealthResult.ok(message) : HealthResult.warn(message);
    }

    private String proxyLine(BusHealth.ProxyLink link, long now) {
        if (!link.signing()) {
            return "proxy OFF: network.bus.secret is not set, shorter than " + BusSecret.MIN_LENGTH
                    + " characters, the documentation's example or too easy to guess, so no frame is sent or accepted"
                    + " through the proxy. Put the same random secret on every backend and restart";
        }
        if (!link.registered()) {
            return "proxy channel not registered yet (the bus starts on the first tick)";
        }
        if (link.answeredWithin(now, bus.answerWindow())) {
            return "proxy connected, last answer " + secondsSince(link, now) + "s ago";
        }
        if (link.waitingForPlayer()) {
            return "proxy idle: no player online on this backend to carry frames, so the proxy cannot answer until one"
                    + " joins";
        }
        if (link.lastAnswerMillis().isEmpty()) {
            return "proxy NOT ANSWERING: no frame has come back through the proxy. Install uxmEssentials-velocity on"
                    + " the Velocity proxy, or update it to the same release as this plugin (an older proxy jar does"
                    + " not answer). BungeeCord does not relay the bus; use Redis there";
        }
        return "proxy NOT ANSWERING for " + secondsSince(link, now) + "s. Check that uxmEssentials-velocity is on the"
                + " proxy and on the same release as this plugin";
    }

    private static String refusedLine(BusHealth.ProxyLink link, long now) {
        long ago = Duration.ofMillis(Math.max(0, now - link.lastRefusedMillis().orElse(now)))
                .toSeconds();
        return "proxy REFUSED " + link.refusedFrames() + " frame(s), last " + ago + "s ago: not signed with this"
                + " server's network.bus.secret, or signed too far from its clock. Give every backend the same secret"
                + " and release and keep their clocks in time; a player's client sending forged frames shows the same"
                + " way";
    }

    private static long secondsSince(BusHealth.ProxyLink link, long now) {
        return Duration.ofMillis(Math.max(0, now - link.lastAnswerMillis().orElse(now)))
                .toSeconds();
    }
}
