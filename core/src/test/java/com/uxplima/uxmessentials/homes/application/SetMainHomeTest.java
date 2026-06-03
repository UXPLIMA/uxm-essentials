package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.MainHomePreference;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code /setmainhome} use case: choosing a name the owner has a home under records the choice and
 * tells them so; choosing a name they have no home under is rejected with {@link HomeError#NOT_FOUND} and
 * never writes a preference. The repository and the preference store are in-memory fakes and the notifier
 * captures the last key, so the rule is asserted without any adapter.
 */
class SetMainHomeTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHomeRepository repository;
    private FakeMainHomePreference preferences;
    private CapturingNotifier notifier;
    private SetMainHome setMainHome;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        preferences = new FakeMainHomePreference();
        notifier = new CapturingNotifier();
        setMainHome = new SetMainHome(repository, preferences, notifier.notifier());
    }

    @Test
    void settingAnExistingHomeRecordsTheChoiceAndNotifies() {
        repository.save(home("base"));

        Result<Unit, HomeError> result = setMainHome.setMain(OWNER, HomeName.of("base"));

        assertThat(result.isOk()).isTrue();
        assertThat(preferences.mainHomeOf(OWNER)).contains(HomeName.of("base"));
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_MAIN_SET);
    }

    @Test
    void settingAMissingHomeIsRejectedAndPersistsNothing() {
        repository.save(home("base"));

        Result<Unit, HomeError> result = setMainHome.setMain(OWNER, HomeName.of("ghost"));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
        assertThat(preferences.mainHomeOf(OWNER)).isEmpty();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_NOT_FOUND);
    }

    private Home home(String name) {
        return new Home(OWNER, HomeName.of(name), Position.of(WORLD, 0, 64, 0), Instant.ofEpochMilli(1_000));
    }

    /** An in-memory {@link HomeRepository} keyed by the single owner under test. */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<HomeName, Home> homes = new HashMap<>();

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, List.copyOf(homes.values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return homes.size();
        }

        @Override
        public void save(Home home) {
            homes.put(home.name(), home);
        }

        @Override
        public void rename(PlayerRef owner, HomeName from, HomeName to) {
            Home moved = homes.remove(from);
            if (moved != null) {
                homes.put(to, moved.renamedTo(to));
            }
        }

        @Override
        public void delete(PlayerRef owner, HomeName name) {
            homes.remove(name);
        }
    }

    /** An in-memory {@link MainHomePreference} for one owner. */
    private static final class FakeMainHomePreference implements MainHomePreference {
        private final Map<UUID, HomeName> choices = new HashMap<>();

        @Override
        public Optional<HomeName> mainHomeOf(PlayerRef owner) {
            return Optional.ofNullable(choices.get(owner.uuid()));
        }

        @Override
        public void setMainHome(PlayerRef owner, HomeName name) {
            choices.put(owner.uuid(), name);
        }

        @Override
        public void clear(PlayerRef owner) {
            choices.remove(owner.uuid());
        }
    }

    /** Wraps a {@link HomeNotifier} over fakes and records the last key sent. */
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
