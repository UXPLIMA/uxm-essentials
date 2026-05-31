package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link HomesPlaceholders} over the homes context's read ports: the {@link HomeRepository} count and the
 * {@link HomeQuota} reducer. Built during homes wiring from the same repository and quota the {@code /home}
 * use cases hold, so the placeholder count and limit match what {@code /sethome} enforces. The limit is
 * resolved unscoped (no world in hand on the placeholder surface) and an unlimited quota maps to a negative
 * value the resolver renders as the infinity marker.
 */
@NullMarked
public final class RepositoryHomesPlaceholders implements HomesPlaceholders {

    private final HomeRepository repository;
    private final HomeQuota quota;

    public RepositoryHomesPlaceholders(HomeRepository repository, HomeQuota quota) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.quota = Objects.requireNonNull(quota, "quota");
    }

    @Override
    public int count(PlayerRef who) {
        return repository.count(Objects.requireNonNull(who, "who"));
    }

    @Override
    public int limit(PlayerRef who) {
        HomeLimit limit = quota.resolve(Objects.requireNonNull(who, "who"), null);
        return limit.unlimited() ? -1 : limit.cap();
    }
}
