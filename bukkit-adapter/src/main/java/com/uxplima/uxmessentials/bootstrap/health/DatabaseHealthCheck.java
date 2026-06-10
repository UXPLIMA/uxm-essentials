package com.uxplima.uxmessentials.bootstrap.health;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * Probes the persistence layer for {@code /uxmess doctor}: can a connection be acquired and answer a trivial
 * query, and what is the applied Flyway schema version. A reachable database reports {@code OK} with the backend
 * and version; an unreachable pool reports {@code FAIL} so an operator sees the outage immediately. The probe
 * runs through {@link Persistence#probe()}, which already converts a dead pool into a {@code reachable = false}
 * result rather than throwing, so this check stays within the resilience invariant.
 */
@NullMarked
public final class DatabaseHealthCheck implements HealthCheck {

    private final Persistence persistence;

    public DatabaseHealthCheck(Persistence persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public HealthResult check() {
        Persistence.Probe probe = persistence.probe();
        if (!probe.reachable()) {
            return HealthResult.fail("cannot reach the " + probe.backend() + " database");
        }
        String version = probe.schemaVersion().map(v -> "schema v" + v).orElse("schema version unknown");
        return HealthResult.ok(probe.backend() + " reachable, " + version);
    }
}
