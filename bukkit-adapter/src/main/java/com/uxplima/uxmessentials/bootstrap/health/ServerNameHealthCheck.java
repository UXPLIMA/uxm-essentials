package com.uxplima.uxmessentials.bootstrap.health;

import java.util.Objects;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.DuplicateServerNames;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.NetworkConfig;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * Reports whether this backend's {@code network.server-name} is its own, for {@code /uxmess doctor}. The bus drops
 * every frame stamped with its own name, so a name two backends share stops sync between them without any other
 * symptom. A clash actually heard on the bus is a failure; the shipped default on an enabled network is a warning,
 * because every backend starts with it and the second one to keep it causes that clash.
 *
 * <p>Registered only for an enabled backend; it reads in-memory state only, so it is safe on the doctor thread.
 */
@NullMarked
public final class ServerNameHealthCheck implements HealthCheck {

    private final DuplicateServerNames duplicates;
    private final LongSupplier clock;

    public ServerNameHealthCheck(DuplicateServerNames duplicates) {
        this(duplicates, System::currentTimeMillis);
    }

    ServerNameHealthCheck(DuplicateServerNames duplicates, LongSupplier clock) {
        this.duplicates = Objects.requireNonNull(duplicates, "duplicates");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "server-name";
    }

    @Override
    public HealthResult check() {
        String name = duplicates.serverName();
        if (duplicates.seen(clock.getAsLong())) {
            return HealthResult.fail("another backend also uses the name " + name
                    + ", so neither receives the other's sync. Set a unique network.server-name in config.conf"
                    + " on one of them and restart it");
        }
        if (NetworkConfig.isDefaultServerName(name)) {
            return HealthResult.warn(
                    "still the default " + name + ". Set a unique network.server-name in config.conf on every backend");
        }
        return HealthResult.ok(name);
    }
}
