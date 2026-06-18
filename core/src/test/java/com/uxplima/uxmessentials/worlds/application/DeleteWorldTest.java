package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.PendingDeletionRegistry;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.PendingDeletion;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldDeleted;
import org.junit.jupiter.api.Test;

class DeleteWorldTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final List<DomainEvent> events = new ArrayList<>();
    private final FakePending pending = new FakePending();
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");
    private final DeleteWorld delete = new DeleteWorld(
            repo,
            engine,
            pending,
            TestSupport.notifier(),
            events::add,
            TestSupport.inlineScheduler(),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            () -> true);

    private void register(String name) {
        repo.save(ManagedWorld.created(WorldName.of(name), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.onDisk.add(name);
    }

    @Test
    void requestStagesButDoesNotDelete() {
        register("creative");
        var result = delete.request(who, WorldName.of("creative"));
        assertThat(result.isOk()).isTrue();
        assertThat(pending.peek(who.uuid())).isPresent();
        assertThat(engine.exists(WorldName.of("creative"))).isTrue();
        assertThat(events).isEmpty();
    }

    @Test
    void confirmUnloadsDeletesAndDropsTheRow() {
        register("creative");
        engine.loaded.add("creative");
        delete.request(who, WorldName.of("creative"));
        var result = delete.confirm(who, WorldName.of("creative"));
        assertThat(result.isOk()).isTrue();
        assertThat(engine.isLoaded(WorldName.of("creative"))).isFalse();
        assertThat(engine.exists(WorldName.of("creative"))).isFalse();
        assertThat(repo.exists(WorldName.of("creative"))).isFalse();
        assertThat(events).anyMatch(WorldDeleted.class::isInstance);
    }

    @Test
    void requestRejectsTheProtectedDefaultWorld() {
        register("world");
        engine.defaultWorld = WorldName.of("world");
        assertThat(delete.request(who, WorldName.of("world")).errorOrThrow().name())
                .isEqualTo("IS_PROTECTED");
    }

    @Test
    void confirmWithoutAStageReportsNothingStaged() {
        register("creative");
        assertThat(delete.confirm(who, WorldName.of("creative")).errorOrThrow().name())
                .isEqualTo("NOT_FOUND");
    }

    private static final class FakePending implements PendingDeletionRegistry {
        private final Map<UUID, PendingDeletion> map = new LinkedHashMap<>();

        @Override
        public void stage(PendingDeletion pending) {
            map.put(pending.requester(), pending);
        }

        @Override
        public Optional<PendingDeletion> take(WorldName name, UUID requester) {
            PendingDeletion p = map.get(requester);
            if (p != null && p.name().equals(name)) {
                map.remove(requester);
                return Optional.of(p);
            }
            return Optional.empty();
        }

        @Override
        public Optional<PendingDeletion> peek(UUID requester) {
            return Optional.ofNullable(map.get(requester));
        }

        @Override
        public void clear(WorldName name) {
            map.values().removeIf(p -> p.name().equals(name));
        }
    }
}
