package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

class SetGameruleTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final List<DomainEvent> events = new ArrayList<>();
    private final GameRuleCatalog catalog = new GameRuleCatalog() {
        @Override
        public Optional<GameRuleType> typeOf(String name) {
            return switch (name) {
                case "keepInventory" -> Optional.of(GameRuleType.BOOLEAN);
                case "randomTickSpeed" -> Optional.of(GameRuleType.INTEGER);
                default -> Optional.empty();
            };
        }

        @Override
        public List<String> names() {
            return List.of("keepInventory", "randomTickSpeed");
        }
    };
    private final SetGamerule setGamerule =
            new SetGamerule(repo, catalog, TestSupport.notifier(), events::add, TestSupport.inlineScheduler());
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");

    private void register(String name) {
        repo.save(ManagedWorld.created(WorldName.of(name), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
    }

    @Test
    void setsAValidBooleanRule() {
        register("w");
        var result = setGamerule.set(who, WorldName.of("w"), "keepInventory", "true");
        assertThat(result.isOk()).isTrue();
        assertThat(repo.find(WorldName.of("w")).orElseThrow().settings().gamerules())
                .containsEntry("keepInventory", "true");
    }

    @Test
    void rejectsUnknownRule() {
        register("w");
        assertThat(setGamerule
                        .set(who, WorldName.of("w"), "nope", "true")
                        .errorOrThrow()
                        .name())
                .isEqualTo("GAMERULE_UNKNOWN");
    }

    @Test
    void rejectsInvalidValueForType() {
        register("w");
        assertThat(setGamerule
                        .set(who, WorldName.of("w"), "keepInventory", "7")
                        .errorOrThrow()
                        .name())
                .isEqualTo("GAMERULE_INVALID_VALUE");
        assertThat(setGamerule
                        .set(who, WorldName.of("w"), "randomTickSpeed", "fast")
                        .errorOrThrow()
                        .name())
                .isEqualTo("GAMERULE_INVALID_VALUE");
    }
}
