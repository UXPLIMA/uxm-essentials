package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.WorldSettingApplier;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

class ApplyWorldSettingsOnLoadTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final List<WorldName> applied = new ArrayList<>();
    private final WorldSettingApplier applier = (name, settings) -> applied.add(name);
    private final ApplyWorldSettingsOnLoad onLoad = new ApplyWorldSettingsOnLoad(repo, applier);

    @Test
    void appliesSettingsForARegisteredWorld() {
        repo.save(ManagedWorld.created(WorldName.of("w"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH)
                .withSettings(WorldSettings.defaults().with(WorldProperties.PVP, false)));
        onLoad.apply(WorldName.of("w"));
        assertThat(applied).containsExactly(WorldName.of("w"));
    }

    @Test
    void noOpsForUnregisteredWorld() {
        onLoad.apply(WorldName.of("ghost"));
        assertThat(applied).isEmpty();
    }
}
