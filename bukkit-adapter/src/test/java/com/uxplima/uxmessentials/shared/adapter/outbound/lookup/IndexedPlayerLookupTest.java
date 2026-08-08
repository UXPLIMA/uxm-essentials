package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Resolution order of the decorated lookup: a connected player first, then the plugin's own name index, then
 * whatever the server can still resolve. The middle step is what an offline-mode server has instead of Paper's
 * name cache, which it never consults there.
 */
class IndexedPlayerLookupTest {

    private static final PlayerRef ONLINE_REF = new PlayerRef(UUID.randomUUID(), "Online");
    private static final PlayerRef DELEGATE_REF = new PlayerRef(UUID.randomUUID(), "ExactCaseOnly");
    private static final UUID OFFLINE_ID = UUID.randomUUID();

    private RecordingIndex index;
    private IndexedPlayerLookup lookup;

    @BeforeEach
    void setUp() {
        index = new RecordingIndex();
        lookup = new IndexedPlayerLookup(new ServerOnlyLookup(), index);
    }

    @Test
    void anOnlinePlayerStillComesFromTheServer() {
        assertThat(lookup.findByName("Online")).contains(ONLINE_REF);
    }

    @Test
    void anOfflinePlayerResolvesFromTheIndexWhateverTheCase() {
        index.record(OFFLINE_ID, "Cofteey");

        assertThat(lookup.findByName("cofteey")).contains(new PlayerRef(OFFLINE_ID, "Cofteey"));
        assertThat(lookup.findByName("COFTEEY")).contains(new PlayerRef(OFFLINE_ID, "Cofteey"));
    }

    @Test
    void anIndexMissFallsBackToTheDelegate() {
        assertThat(lookup.findByName("ExactCaseOnly")).contains(DELEGATE_REF);
    }

    @Test
    void aNameNeitherKnowsResolvesToEmpty() {
        assertThat(lookup.findByName("nobody")).isEmpty();
    }

    @Test
    void findOnlineByNameStaysOnlineOnly() {
        index.record(OFFLINE_ID, "Cofteey");

        assertThat(lookup.findOnlineByName("Cofteey")).isEmpty();
        assertThat(lookup.findOnlineByName("Online")).contains(ONLINE_REF);
    }

    @Test
    void uuidResolutionAndPresenceAreLeftToTheDelegate() {
        assertThat(lookup.findByUuid(ONLINE_REF.uuid())).contains(ONLINE_REF);
        assertThat(lookup.isOnline(ONLINE_REF.uuid())).isTrue();
        assertThat(lookup.isOnline(OFFLINE_ID)).isFalse();
    }

    /** Stands in for the Bukkit lookup: one connected player, and one offline name it can only match exactly. */
    private static final class ServerOnlyLookup implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return ONLINE_REF.name().equals(name) ? Optional.of(ONLINE_REF) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            if (ONLINE_REF.name().equals(name)) {
                return Optional.of(ONLINE_REF);
            }
            return DELEGATE_REF.name().equals(name) ? Optional.of(DELEGATE_REF) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return ONLINE_REF.uuid().equals(uuid) ? Optional.of(ONLINE_REF) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return ONLINE_REF.uuid().equals(uuid);
        }
    }

    private static final class RecordingIndex implements PlayerNameIndex {

        private final ConcurrentHashMap<String, PlayerRef> recorded = new ConcurrentHashMap<>();

        @Override
        public Optional<PlayerRef> byName(String name) {
            return Optional.ofNullable(recorded.get(name.toLowerCase(Locale.ROOT)));
        }

        @Override
        public void record(UUID uuid, String name) {
            recorded.put(name.toLowerCase(Locale.ROOT), new PlayerRef(uuid, name));
        }
    }
}
