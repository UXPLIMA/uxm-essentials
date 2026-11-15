package com.uxplima.uxmessentials.moderation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.Test;

/**
 * {@code /seen} hides a vanished player from a viewer whose see level falls short, here by the local store and
 * elsewhere by the shared vanish row, and the console sees everyone.
 */
class VanishSeenVisibilityTest {

    private static final Instant AT = Instant.parse("2026-11-15T12:00:00Z");
    private static final PlayerRef VIEWER = new PlayerRef(UUID.randomUUID(), "Viewer");
    private static final PlayerRef SEER = new PlayerRef(UUID.randomUUID(), "Seer");
    private static final PlayerRef HIDDEN = new PlayerRef(UUID.randomUUID(), "Hidden");
    private static final PlayerRef CONSOLE = PlayerRef.system("Console");

    private final InMemoryVanishStore local = new InMemoryVanishStore();
    private final Rows rows = new Rows();
    private final VanishSeenVisibility visibility =
            new VanishSeenVisibility(local, rows, new Levels(Map.of(SEER.uuid(), 1)));

    @Test
    void aPlayerVanishedHereIsHiddenFromAViewerWithoutSeeLevelButNotFromASeer() {
        local.vanish(HIDDEN.uuid(), VanishLevel.DEFAULT);

        assertThat(visibility.isHiddenHere(VIEWER, visibility.seeLevel(VIEWER), HIDDEN))
                .isTrue();
        assertThat(visibility.isHiddenHere(SEER, visibility.seeLevel(SEER), HIDDEN))
                .isFalse();
    }

    @Test
    void aPlayerVanishedElsewhereIsHiddenByTheirRowUpToTheirLevel() {
        rows.kept.put(HIDDEN.uuid(), StoredVanish.vanished(VanishLevel.of(2), "lobby", AT));

        assertThat(visibility.isHiddenElsewhere(visibility.seeLevel(SEER), HIDDEN))
                .isTrue();
        assertThat(visibility.isHiddenElsewhere(2, HIDDEN)).isFalse();
    }

    @Test
    void aVanishedRowWithoutALevelCountsAsTheDefaultLevel() {
        rows.kept.put(
                HIDDEN.uuid(),
                new StoredVanish(true, Optional.empty(), Optional.empty(), AT, Optional.empty(), Optional.empty()));

        assertThat(visibility.isHiddenElsewhere(visibility.seeLevel(VIEWER), HIDDEN))
                .isTrue();
        assertThat(visibility.isHiddenElsewhere(visibility.seeLevel(SEER), HIDDEN))
                .isFalse();
    }

    @Test
    void theConsoleSeesEveryVanishedPlayer() {
        local.vanish(HIDDEN.uuid(), VanishLevel.of(5));
        rows.kept.put(HIDDEN.uuid(), StoredVanish.vanished(VanishLevel.of(5), "lobby", AT));
        int console = visibility.seeLevel(CONSOLE);

        assertThat(visibility.isHiddenHere(CONSOLE, console, HIDDEN)).isFalse();
        assertThat(visibility.isHiddenElsewhere(console, HIDDEN)).isFalse();
    }

    /** See levels from a fixed table; anyone missing holds none. */
    private record Levels(Map<UUID, Integer> see) implements VanishLevelResolver {

        @Override
        public VanishLevel useLevel(PlayerRef who) {
            return VanishLevel.DEFAULT;
        }

        @Override
        public int seeLevel(PlayerRef who) {
            return see.getOrDefault(who.uuid(), 0);
        }
    }

    /** The shared vanish rows, of which /seen only reads. */
    private static final class Rows implements VanishRecords {

        final Map<UUID, StoredVanish> kept = new HashMap<>();

        @Override
        public Optional<StoredVanish> find(UUID player) {
            return Optional.ofNullable(kept.get(player));
        }

        @Override
        public void save(UUID player, StoredVanish state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markDeparted(UUID player, String server, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markArrived(UUID player, String server, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void touch(Collection<UUID> players, String server, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseHeld(String server, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseStale(String server, Instant startedAt) {
            throw new UnsupportedOperationException();
        }
    }
}
