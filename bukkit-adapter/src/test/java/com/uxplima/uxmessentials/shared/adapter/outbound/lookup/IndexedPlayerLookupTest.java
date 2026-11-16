package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Whether a player is connected is answered from the joins and quits the server reported, so a caller off the tick
 * gets the same answer the server would give without asking it.
 */
class IndexedPlayerLookupTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");

    private final OnlinePlayerIndex online = new OnlinePlayerIndex();
    private final ServerProfiles server = new ServerProfiles();
    private final PlayerLookup lookup = new IndexedPlayerLookup(server, new NoNames(), online);

    @Test
    void aPlayerIsOnlineFromJoinUntilQuit() {
        assertThat(lookup.isOnline(ALEX.uuid())).isFalse();

        online.joined(ALEX);
        assertThat(lookup.isOnline(ALEX.uuid())).isTrue();

        online.left(ALEX.uuid());
        assertThat(lookup.isOnline(ALEX.uuid())).isFalse();
    }

    @Test
    void aConnectedPlayerIsFoundByNameInAnyCaseAndByIdWithoutAskingTheServer() {
        online.joined(ALEX);

        assertThat(lookup.findOnlineByName("aLEX")).contains(ALEX);
        assertThat(lookup.findByName("alex")).contains(ALEX);
        assertThat(lookup.findByUuid(ALEX.uuid())).contains(ALEX);
        assertThat(server.asked).isZero();
    }

    @Test
    void aPlayerWhoLeftIsNoLongerFoundOnlineButTheServerStillKnowsTheirProfile() {
        online.joined(ALEX);
        online.left(ALEX.uuid());
        server.profiles.put(ALEX.uuid(), ALEX);

        assertThat(lookup.findOnlineByName("Alex")).isEmpty();
        assertThat(lookup.findByUuid(ALEX.uuid())).contains(ALEX);
    }

    @Test
    void aPlayerWhoRejoinsUnderANewNameIsFoundOnlyByTheNewOne() {
        online.joined(ALEX);
        PlayerRef renamed = new PlayerRef(ALEX.uuid(), "Alexandra");

        online.joined(renamed);

        assertThat(lookup.findOnlineByName("Alex")).isEmpty();
        assertThat(lookup.findOnlineByName("Alexandra")).contains(renamed);
    }

    @Test
    void aQuitDoesNotTakeTheNameFromAnotherAccountNowOnlineUnderIt() {
        online.joined(ALEX);
        PlayerRef other = new PlayerRef(UUID.randomUUID(), "Alex");
        online.joined(other);

        online.left(ALEX.uuid());

        assertThat(lookup.findOnlineByName("Alex")).contains(other);
        assertThat(lookup.isOnline(other.uuid())).isTrue();
    }

    /** The server's offline profiles: counts every question, since the online answers must not need it. */
    private static final class ServerProfiles implements PlayerLookup {

        final Map<UUID, PlayerRef> profiles = new HashMap<>();
        int asked;

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            asked++;
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            asked++;
            return profiles.values().stream()
                    .filter(ref -> ref.name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            asked++;
            return Optional.ofNullable(profiles.get(uuid));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            asked++;
            return false;
        }
    }

    private static final class NoNames implements PlayerNameIndex {

        @Override
        public Optional<PlayerRef> byName(String name) {
            return Optional.empty();
        }

        @Override
        public void record(UUID uuid, String name) {}

        @Override
        public void learn(UUID uuid, String name) {}
    }
}
