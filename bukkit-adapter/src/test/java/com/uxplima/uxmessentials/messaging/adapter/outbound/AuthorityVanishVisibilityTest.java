package com.uxplima.uxmessentials.messaging.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.Test;

/**
 * The messaging vanish gate judges a player on this backend by the local store alone, so an entry the network view
 * kept does not hide them, and a player on another backend by the network view.
 */
class AuthorityVanishVisibilityTest {

    private static final PlayerRef VIEWER = new PlayerRef(UUID.randomUUID(), "Viewer");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Target");

    private final InMemoryVanishStore local = new InMemoryVanishStore();
    private final View network = new View();
    private final Set<UUID> here = new HashSet<>();
    private final AuthorityVanishVisibility visibility =
            new AuthorityVanishVisibility(local, network, new NoSeeLevel(), here::contains);

    @Test
    void aPlayerHereWhomOnlyAStaleNetworkEntryCallsVanishedIsVisible() {
        here.add(TARGET.uuid());
        network.levels.put(TARGET.uuid(), VanishLevel.DEFAULT);

        assertThat(visibility.isHiddenFrom(VIEWER, TARGET)).isFalse();
    }

    @Test
    void aPlayerVanishedHereIsHidden() {
        here.add(TARGET.uuid());
        local.vanish(TARGET.uuid(), VanishLevel.DEFAULT);

        assertThat(visibility.isHiddenFrom(VIEWER, TARGET)).isTrue();
    }

    @Test
    void aPlayerElsewhereIsJudgedByTheNetworkView() {
        network.levels.put(TARGET.uuid(), VanishLevel.DEFAULT);

        assertThat(visibility.isHiddenFrom(VIEWER, TARGET)).isTrue();
    }

    /** A viewer without any vanish see permission. */
    private static final class NoSeeLevel implements VanishLevelResolver {

        @Override
        public VanishLevel useLevel(PlayerRef who) {
            return VanishLevel.DEFAULT;
        }

        @Override
        public int seeLevel(PlayerRef who) {
            return 0;
        }
    }

    /** A network view the test fills by hand. */
    private static final class View implements NetworkVanishStore {

        final Map<UUID, VanishLevel> levels = new HashMap<>();

        @Override
        public boolean apply(String origin, long run, long sequence, VanishSync change) {
            return true;
        }

        @Override
        public void depart(String origin, long run, long sequence, UUID player) {}

        @Override
        public void heartbeat(String origin, long run, boolean newRun) {}

        @Override
        public Optional<VanishLevel> levelOf(UUID who) {
            return Optional.ofNullable(levels.get(who));
        }

        @Override
        public Optional<String> nameOf(UUID who) {
            return Optional.empty();
        }

        @Override
        public Map<UUID, VanishLevel> levels() {
            return Map.copyOf(levels);
        }

        @Override
        public void clear() {
            levels.clear();
        }
    }
}
