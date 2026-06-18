package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldSettingChanged;
import org.junit.jupiter.api.Test;

class SetWorldPropertyTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final List<DomainEvent> events = new ArrayList<>();
    private final SetWorldProperty setProperty =
            new SetWorldProperty(repo, TestSupport.notifier(), events::add, TestSupport.inlineScheduler());
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");

    private void register(String name) {
        repo.save(ManagedWorld.created(WorldName.of(name), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
    }

    @Test
    void setsAValidProperty() {
        register("w");
        var result = setProperty.set(who, WorldName.of("w"), "pvp", "false");
        assertThat(result.isOk()).isTrue();
        assertThat(repo.find(WorldName.of("w")).orElseThrow().settings().get(WorldProperties.PVP))
                .isFalse();
        assertThat(events).anyMatch(WorldSettingChanged.class::isInstance);
    }

    @Test
    void rejectsUnknownProperty() {
        register("w");
        assertThat(setProperty
                        .set(who, WorldName.of("w"), "nope", "x")
                        .errorOrThrow()
                        .name())
                .isEqualTo("SETTING_UNKNOWN");
    }

    @Test
    void rejectsInvalidValue() {
        register("w");
        assertThat(setProperty
                        .set(who, WorldName.of("w"), "pvp", "maybe")
                        .errorOrThrow()
                        .name())
                .isEqualTo("SETTING_INVALID_VALUE");
    }

    @Test
    void rejectsUnknownWorld() {
        assertThat(setProperty
                        .set(who, WorldName.of("ghost"), "pvp", "true")
                        .errorOrThrow()
                        .name())
                .isEqualTo("NOT_FOUND");
    }
}
