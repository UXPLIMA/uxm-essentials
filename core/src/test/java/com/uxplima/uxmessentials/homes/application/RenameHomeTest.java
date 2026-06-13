package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeRenamed;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code /homename} rename path: setting a label on an existing home succeeds and publishes
 * {@link HomeRenamed}; renaming an empty slot returns {@link HomeError#NOT_FOUND} and publishes nothing.
 */
class RenameHomeTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-30T12:00:00Z"), ZoneOffset.UTC);

    private FakeHomeRepository repository;
    private CapturingNotifier notifier;
    private RecordingPublisher events;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        notifier = new CapturingNotifier();
        events = new RecordingPublisher();
    }

    @Test
    void settingALabelOnAnExistingHomeSucceedsAndPublishesHomeRenamed() {
        repository.save(Home.create(OWNER, HomeSlot.of(0), at(1, 64, 1), CLOCK.instant()));

        Result<Unit, HomeError> result = useCase().rename(OWNER, HomeSlot.of(0), Optional.of(HomeLabel.of("Base")));

        assertThat(result.isOk()).isTrue();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_RENAMED);
        assertThat(events.published).hasSize(1);
        assertThat(events.published.get(0)).isInstanceOf(HomeRenamed.class);
        HomeRenamed event = (HomeRenamed) events.published.get(0);
        assertThat(event.owner()).isEqualTo(OWNER);
        assertThat(event.slot()).isEqualTo(HomeSlot.of(0));
    }

    @Test
    void clearingTheLabelOnAnExistingHomeSucceedsAndPublishesHomeRenamed() {
        repository.save(Home.create(OWNER, HomeSlot.of(1), at(1, 64, 1), CLOCK.instant()));

        Result<Unit, HomeError> result = useCase().rename(OWNER, HomeSlot.of(1), Optional.empty());

        assertThat(result.isOk()).isTrue();
        assertThat(events.published).hasSize(1);
        assertThat(events.published.get(0)).isInstanceOf(HomeRenamed.class);
    }

    @Test
    void renamingAnEmptySlotRejectsAndPublishesNothing() {
        Result<Unit, HomeError> result = useCase().rename(OWNER, HomeSlot.of(0), Optional.of(HomeLabel.of("Base")));

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
        assertThat(events.published).isEmpty();
    }

    private RenameHome useCase() {
        return new RenameHome(repository, notifier.notifier(), events, CLOCK);
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }

    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<HomeSlot, Home> homes = new TreeMap<>(java.util.Comparator.comparingInt(HomeSlot::index));

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, List.copyOf(homes.values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return homes.size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(homes.get(slot));
        }

        @Override
        public void save(Home home) {
            homes.put(home.slot(), home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            homes.remove(slot);
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            homes.clear();
        }
    }

    private static final class RecordingPublisher implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    private static final class CapturingNotifier {
        private @Nullable MessageKey lastKey;

        HomeNotifier notifier() {
            Messages messages = (viewer, key, placeholders) -> {
                lastKey = key;
                return key.key();
            };
            MessageSink sink = (viewer, renderedText) -> {};
            return new HomeNotifier(messages, sink);
        }
    }
}
