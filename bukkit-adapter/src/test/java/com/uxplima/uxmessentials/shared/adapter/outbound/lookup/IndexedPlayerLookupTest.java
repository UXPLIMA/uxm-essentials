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
    private static final Object FIRST = new Object();
    private static final Object SECOND = new Object();
    private static final Object OTHER = new Object();

    private final OnlinePlayerIndex online = new OnlinePlayerIndex();
    private final ServerProfiles server = new ServerProfiles();
    private final PlayerLookup lookup = new IndexedPlayerLookup(server, new NoNames(), online);

    @Test
    void aPlayerIsOnlineFromJoinUntilQuit() {
        assertThat(lookup.isOnline(ALEX.uuid())).isFalse();

        online.joined(ALEX, FIRST);
        assertThat(lookup.isOnline(ALEX.uuid())).isTrue();

        online.left(ALEX.uuid(), FIRST);
        assertThat(lookup.isOnline(ALEX.uuid())).isFalse();
    }

    @Test
    void aConnectedPlayerIsFoundByNameInAnyCaseAndByIdWithoutAskingTheServer() {
        online.joined(ALEX, FIRST);

        assertThat(lookup.findOnlineByName("aLEX")).contains(ALEX);
        assertThat(lookup.findByName("alex")).contains(ALEX);
        assertThat(lookup.findByUuid(ALEX.uuid())).contains(ALEX);
        assertThat(server.asked).isZero();
    }

    @Test
    void aPlayerWhoLeftIsNoLongerFoundOnlineButTheServerStillKnowsTheirProfile() {
        online.joined(ALEX, FIRST);
        online.left(ALEX.uuid(), FIRST);
        server.profiles.put(ALEX.uuid(), ALEX);

        assertThat(lookup.findOnlineByName("Alex")).isEmpty();
        assertThat(lookup.findByUuid(ALEX.uuid())).contains(ALEX);
    }

    @Test
    void aPlayerWhoRejoinsUnderANewNameIsFoundOnlyByTheNewOne() {
        online.joined(ALEX, FIRST);
        PlayerRef renamed = new PlayerRef(ALEX.uuid(), "Alexandra");

        online.joined(renamed, SECOND);

        assertThat(lookup.findOnlineByName("Alex")).isEmpty();
        assertThat(lookup.findOnlineByName("Alexandra")).contains(renamed);
    }

    @Test
    void aQuitDoesNotTakeTheNameFromAnotherAccountNowOnlineUnderIt() {
        online.joined(ALEX, FIRST);
        PlayerRef other = new PlayerRef(UUID.randomUUID(), "Alex");
        online.joined(other, OTHER);

        online.left(ALEX.uuid(), FIRST);

        assertThat(lookup.findOnlineByName("Alex")).contains(other);
        assertThat(lookup.isOnline(other.uuid())).isTrue();
    }

    @Test
    void aLateQuitOfAnOldConnectionLeavesTheNewOneOnline() {
        online.joined(ALEX, FIRST);
        online.joined(ALEX, SECOND);

        online.left(ALEX.uuid(), FIRST);

        assertThat(lookup.isOnline(ALEX.uuid())).isTrue();
        assertThat(lookup.findOnlineByName("Alex")).contains(ALEX);

        online.left(ALEX.uuid(), SECOND);
        assertThat(lookup.isOnline(ALEX.uuid())).isFalse();
        assertThat(lookup.findOnlineByName("Alex")).isEmpty();
    }

    @Test
    void anOfflineNameNobodyHereUsedResolvesToEmpty() {
        assertThat(lookup.findByName("Nobody")).isEmpty();
    }

    /** The server's offline profiles: counts every question, since the online answers must not need it. */
    private static final class ServerProfiles implements OfflineProfiles {

        final Map<UUID, PlayerRef> profiles = new HashMap<>();
        int asked;

        @Override
        public Optional<PlayerRef> byName(String name) {
            asked++;
            return profiles.values().stream()
                    .filter(ref -> ref.name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public Optional<PlayerRef> byUuid(UUID uuid) {
            asked++;
            return Optional.ofNullable(profiles.get(uuid));
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
