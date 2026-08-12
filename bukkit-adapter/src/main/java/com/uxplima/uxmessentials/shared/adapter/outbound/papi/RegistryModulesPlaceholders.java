package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.NullMarked;

/**
 * The {@link ModulesPlaceholders} seam over the set of modules that actually wired on this enable. The set is
 * captured once, after the wiring loop, because that is the moment the answer becomes true: a module can be
 * registered and still be off, and a module whose capability check failed never wired at all.
 */
@NullMarked
public final class RegistryModulesPlaceholders implements ModulesPlaceholders {

    private final Set<String> enabled;

    public RegistryModulesPlaceholders(Set<String> enabled) {
        this.enabled = Set.copyOf(Objects.requireNonNull(enabled, "enabled"));
    }

    @Override
    public boolean enabled(String moduleId) {
        return enabled.contains(Objects.requireNonNull(moduleId, "moduleId"));
    }
}
