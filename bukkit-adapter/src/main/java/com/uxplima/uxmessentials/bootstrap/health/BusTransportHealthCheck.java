package com.uxplima.uxmessentials.bootstrap.health;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusHealth;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * Reports whether the cross-server bus actually reaches the network, for {@code /uxmess doctor}, one carrier at a
 * time. A disabled backend ({@code network.enabled = false}, the default) reports {@code OK} as "disabled".
 *
 * <p>The proxy carrier is connected only once a frame came back through the {@code uxmEssentials-velocity} broker
 * within the answer window. A registered channel is not enough: frames leave a backend whether or not anything on
 * the proxy relays them, so a proxy without the broker would otherwise read connected while nothing syncs. The
 * broker returns each heartbeat to its sender, so a backend alone behind the proxy still sees the answer. The Redis
 * carrier is connected while its subscribe connection is up. With {@code transport = both} each carrier gets its
 * own verdict, and either one silent is a {@code WARN}: the backends reached only through it are cut off.
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
        List<String> parts = new ArrayList<>();
        boolean working = true;
        if (bus.proxy().isPresent()) {
            BusHealth.ProxyLink link = bus.proxy().get();
            parts.add(proxyLine(link));
            working = link.answeredWithin(clock.getAsLong(), bus.answerWindow());
        }
        if (bus.redisConnected().isPresent()) {
            boolean up = bus.redisConnected().get();
            parts.add(
                    up
                            ? "redis connected"
                            : "redis DISCONNECTED. Check network.redis and that uxmEssentials-redis is in plugins/");
            working &= up;
        }
        String message = "enabled, transport=" + bus.transport() + ", " + String.join("; ", parts);
        return working ? HealthResult.ok(message) : HealthResult.warn(message);
    }

    private String proxyLine(BusHealth.ProxyLink link) {
        if (!link.registered()) {
            return "proxy channel not registered yet (the bus starts on the first tick)";
        }
        if (link.lastAnswerMillis().isEmpty()) {
            return "proxy NOT ANSWERING: no frame has come back through the proxy. Install uxmEssentials-velocity on"
                    + " the Velocity proxy (BungeeCord does not relay the bus; use Redis there). Frames also need a"
                    + " player online on this backend to travel";
        }
        long now = clock.getAsLong();
        long ago = Duration.ofMillis(Math.max(0, now - link.lastAnswerMillis().getAsLong()))
                .toSeconds();
        if (link.answeredWithin(now, bus.answerWindow())) {
            return "proxy connected, last answer " + ago + "s ago";
        }
        return "proxy NOT ANSWERING for " + ago + "s. Frames need a player online on this backend to travel; with"
                + " players on, check uxmEssentials-velocity on the proxy";
    }
}
