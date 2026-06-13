package com.uxplima.uxmessentials.vote.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The ordered set of vote sites the operator has configured. Immutable once built; the adapter
 * reads config once at enable and swaps the catalog atomically on reload.
 *
 * @param sites the configured sites in declaration order
 */
public record VoteSiteCatalog(List<VoteSiteSpec> sites) {

    public VoteSiteCatalog {
        Objects.requireNonNull(sites, "sites");
        sites = List.copyOf(sites);
    }

    /** An empty catalog when no sites are configured. */
    public static VoteSiteCatalog empty() {
        return new VoteSiteCatalog(List.of());
    }

    /**
     * Look up a site by its display name, case-insensitively. Operators may not preserve the case
     * they used in config, so matching is always normalised.
     */
    public Optional<VoteSiteSpec> forName(String name) {
        Objects.requireNonNull(name, "name");
        String lower = name.toLowerCase(Locale.ROOT);
        return sites.stream()
                .filter(s -> s.name().toLowerCase(Locale.ROOT).equals(lower))
                .findFirst();
    }

    /**
     * Look up a site by its Votifier service key, case-insensitively. A Votifier event carries the
     * raw service string, which may not match the case the operator configured, so matching is
     * always normalised. This is the key the per-site cooldown is tracked under.
     */
    public Optional<VoteSiteSpec> forService(String service) {
        Objects.requireNonNull(service, "service");
        String lower = service.toLowerCase(Locale.ROOT);
        return sites.stream()
                .filter(s -> s.service().toLowerCase(Locale.ROOT).equals(lower))
                .findFirst();
    }
}
