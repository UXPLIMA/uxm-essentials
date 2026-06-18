package com.uxplima.uxmessentials.worlds.application;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldSettingApplier;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Re-applies a world's stored settings to the live world (on load, or after a setting change). */
public final class ApplyWorldSettingsOnLoad {

    private final WorldRepository repository;
    private final WorldSettingApplier applier;

    public ApplyWorldSettingsOnLoad(WorldRepository repository, WorldSettingApplier applier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.applier = Objects.requireNonNull(applier, "applier");
    }

    public void apply(WorldName name) {
        Objects.requireNonNull(name, "name");
        repository.find(name).ifPresent(world -> applier.apply(name, world.settings()));
    }
}
