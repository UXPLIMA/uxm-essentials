package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.junit.jupiter.api.Test;

/**
 * Whether a player is connected is answered from the joins and quits the server reported, so a caller off the tick
 * gets the same answer the server would give without asking it. Which account a typed name means follows the
 * server's {@link NameCase}: on offline mode {@code Steve} and {@code steve} are two players and a name is never
 * resolved to a guess between them.
 */
class IndexedPlayerLookupTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final Object FIRST = new Object();
    private static final Object SECOND = new Object();
    private static final Object OTHER = new Object();

    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef LOWER_STEVE = new PlayerRef(UUID.randomUUID(), "steve");
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half, the name prefixed with a dot. */
    private static final PlayerRef BEDROCK =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"), ".BedrockGamer123");

    private final OnlinePlayerIndex online = new OnlinePlayerIndex();
    private final ServerProfiles server = new ServerProfiles();
    private final CachingPlayerNameIndex names = new CachingPlayerNameIndex(new NoScheduler(), new QuietLog());
    /** Whether the lookup is asked from a tick thread, where it may not read the name store. */
    private boolean onTick;

    private final PlayerLookup lookup =
            new IndexedPlayerLookup(server, names, online, () -> NameCase.SENSITIVE, () -> onTick);
    private final PlayerLookup onlineMode =
            new IndexedPlayerLookup(server, names, online, () -> NameCase.INSENSITIVE, () -> onTick);

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

    @Test
    void offlineModeCaseVariantsOnlineAtOnceAreEachFoundByTheirOwnSpelling() {
        joinBoth();

        assertThat(lookup.findOnlineByName("Steve").map(PlayerRef::uuid)).contains(STEVE.uuid());
        assertThat(lookup.findOnlineByName("steve").map(PlayerRef::uuid)).contains(LOWER_STEVE.uuid());
        assertThat(lookup.findByName("Steve").map(PlayerRef::uuid)).contains(STEVE.uuid());
        assertThat(lookup.findByName("steve").map(PlayerRef::uuid)).contains(LOWER_STEVE.uuid());
    }

    @Test
    void offlineModeANameNeitherVariantSpellsIsAmbiguousAndNamesBoth() {
        joinBoth();

        NameMatch match = lookup.matchByName("STEVE");

        assertThat(match).isInstanceOf(NameMatch.Ambiguous.class);
        assertThat(lookup.findByName("STEVE")).isEmpty();
        assertThat(lookup.findOnlineByName("STEVE")).isEmpty();
        assertThat(match.miss(SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", "STEVE")))
                .isEqualTo(new MessageLine(
                        SharedMessageKey.COMMAND_AMBIGUOUS_PLAYER,
                        Map.of("player", "STEVE", "matches", "Steve, steve")));
        assertThat(server.asked).isZero();
    }

    @Test
    void offlineModeAnOfflineAccountSpelledAsTypedWinsOverAVariantOnline() {
        names.record(STEVE.uuid(), STEVE.name());
        joinAndRecord(LOWER_STEVE, FIRST);

        assertThat(lookup.findByName("Steve").map(PlayerRef::uuid)).contains(STEVE.uuid());
        // The account spelled as typed is not online, and the variant that is online is another player.
        assertThat(lookup.matchOnlineByName("Steve")).isEqualTo(NameMatch.unknown());
        assertThat(lookup.matchOnlineByName("STEVE")).isInstanceOf(NameMatch.Ambiguous.class);
    }

    @Test
    void offlineModeAccountsKnownOnlyFromTheIndexAreToldApartTheSameWay() {
        names.record(STEVE.uuid(), STEVE.name());
        names.record(LOWER_STEVE.uuid(), LOWER_STEVE.name());

        assertThat(lookup.findByName("Steve").map(PlayerRef::uuid)).contains(STEVE.uuid());
        assertThat(lookup.findByName("steve").map(PlayerRef::uuid)).contains(LOWER_STEVE.uuid());
        assertThat(lookup.matchByName("sTEVE")).isInstanceOf(NameMatch.Ambiguous.class);
        assertThat(server.asked).isZero();
    }

    @Test
    void offlineModeANameOnlyOneAccountHoldsIsFoundInAnyCase() {
        names.record(STEVE.uuid(), STEVE.name());

        assertThat(lookup.findByName("STEVE").map(PlayerRef::uuid)).contains(STEVE.uuid());
    }

    @Test
    void offlineModeOnTheTickALoneAccountUnderANameNobodyElseHoldsIsFoundInAnyCase() {
        names.backWith(new StoredNames(), 10);
        names.record(STEVE.uuid(), STEVE.name());
        onTick = true;

        assertThat(lookup.matchByName("STEVE")).isEqualTo(NameMatch.found(STEVE));
        assertThat(lookup.matchByName("Steve")).isEqualTo(NameMatch.found(STEVE));
        assertThat(lookup.matchByNameFromMemory("steve")).isEqualTo(NameMatch.found(STEVE));
    }

    @Test
    void offlineModeBeforeTheStoreHasSaidWhichNamesAreSharedALoneAccountIsNotTakenOnTrust() {
        names.record(STEVE.uuid(), STEVE.name());
        onTick = true;

        assertThat(lookup.matchByName("STEVE")).isEqualTo(NameMatch.unknown());
        assertThat(lookup.matchByName("Steve")).isEqualTo(NameMatch.found(STEVE));
    }

    @Test
    void offlineModeOnTheTickALoneAccountUnderANameOthersHoldIsNotTakenOnTrust() {
        StoredNames store = new StoredNames();
        store.rows.add(new PlayerName(STEVE.uuid(), STEVE.name(), 100L));
        store.rows.add(new PlayerName(LOWER_STEVE.uuid(), LOWER_STEVE.name(), 200L));
        // A window of one: only steve is held in memory, but the store says Steve holds the name too.
        names.backWith(store, 1);
        onTick = true;

        assertThat(lookup.matchByName("STEVE")).isEqualTo(NameMatch.unknown());
        assertThat(lookup.matchByNameFromMemory("STEVE")).isEqualTo(NameMatch.unknown());
        assertThat(lookup.matchByName("steve")).isEqualTo(NameMatch.found(LOWER_STEVE));
    }

    @Test
    void offlineModeTheStoreFindsAnAccountSpelledAsTypedThatTheMemoryLetGo() {
        StoredNames store = new StoredNames();
        store.rows.add(new PlayerName(STEVE.uuid(), STEVE.name(), 100L));
        store.rows.add(new PlayerName(LOWER_STEVE.uuid(), LOWER_STEVE.name(), 200L));
        // A window of one: only the more recently seen steve is held in memory.
        names.backWith(store, 1);

        assertThat(lookup.findByName("Steve").map(PlayerRef::uuid)).contains(STEVE.uuid());
        assertThat(lookup.matchByName("STEVE")).isInstanceOf(NameMatch.Ambiguous.class);

        onTick = true;
        assertThat(lookup.matchByName("Steve")).isEqualTo(NameMatch.unknown());
    }

    @Test
    void onTheTickTheServersPlayerDataIsNeverReadForATypedName() {
        server.profiles.put(STEVE.uuid(), STEVE);
        onTick = true;

        assertThat(lookup.matchByName("Steve")).isEqualTo(NameMatch.unknown());
        assertThat(onlineMode.matchByName("steve")).isEqualTo(NameMatch.unknown());
        assertThat(server.asked).isZero();

        onTick = false;
        assertThat(lookup.matchByNameFromMemory("Steve")).isEqualTo(NameMatch.unknown());
        assertThat(onlineMode.matchByNameFromMemory("steve")).isEqualTo(NameMatch.unknown());
        assertThat(server.asked).isZero();
        assertThat(lookup.matchByName("Steve")).isEqualTo(NameMatch.found(STEVE));
    }

    @Test
    void anAccountSeenOnlyOnAnotherServerIsNamedFromTheStoreOffTheTickAndFromMemoryAfter() {
        StoredNames store = new StoredNames();
        store.rows.add(new PlayerName(BEDROCK.uuid(), BEDROCK.name(), 100L));
        store.rows.add(new PlayerName(ALEX.uuid(), ALEX.name(), 200L));
        // A window of one: only Alex is held in memory; the Bedrock player joined another backend only.
        names.backWith(store, 1);

        onTick = true;
        assertThat(lookup.findByUuid(BEDROCK.uuid())).isEmpty();

        onTick = false;
        assertThat(lookup.findByUuid(BEDROCK.uuid())).contains(BEDROCK);

        onTick = true;
        assertThat(lookup.findByUuid(BEDROCK.uuid())).contains(BEDROCK);
        assertThat(lookup.findByUuid(BEDROCK.uuid()).map(PlayerRef::name)).contains(BEDROCK.name());
    }

    @Test
    void anAccountIsNamedAsItLastJoinedNotAsThisServersCacheRemembersIt() {
        server.profiles.put(ALEX.uuid(), new PlayerRef(ALEX.uuid(), "OldAlex"));
        names.record(ALEX.uuid(), ALEX.name());

        assertThat(lookup.findByUuid(ALEX.uuid()).map(PlayerRef::name)).contains("Alex");
        assertThat(server.asked).isZero();
    }

    @Test
    void anAccountNoServerHasSeenFallsBackToThisServersCache() {
        server.profiles.put(ALEX.uuid(), ALEX);
        names.backWith(new StoredNames(), 10);

        assertThat(lookup.findByUuid(ALEX.uuid())).contains(ALEX);
        assertThat(lookup.findByUuid(UUID.randomUUID())).isEmpty();
    }

    @Test
    void onlineModeAnAccountTheMemoryLetGoIsFoundInTheStoreOffTheTick() {
        StoredNames store = new StoredNames();
        store.rows.add(new PlayerName(BEDROCK.uuid(), BEDROCK.name(), 100L));
        store.rows.add(new PlayerName(ALEX.uuid(), ALEX.name(), 200L));
        names.backWith(store, 1);

        assertThat(onlineMode.matchByName(".bedrockgamer123")).isEqualTo(NameMatch.found(BEDROCK));

        onTick = true;
        assertThat(onlineMode.matchByName(".bedrockgamer123")).isEqualTo(NameMatch.unknown());
        // On the tick neither the store nor the server's player data is read for a typed name.
        assertThat(server.asked).isZero();
    }

    @Test
    void onlineModeANameThatChangedHandsOnAnotherBackendResolvesToItsNewHolderOffTheTick() {
        PlayerRef oldHolder = new PlayerRef(UUID.randomUUID(), "Bob");
        PlayerRef newHolder = new PlayerRef(UUID.randomUUID(), "bob");
        StoredNames store = new StoredNames();
        store.upsert(new PlayerName(oldHolder.uuid(), oldHolder.name(), 100L));
        names.backWith(store, 10);
        // The new holder joined only on another backend, which this one has no word from: the store has it.
        store.upsert(new PlayerName(newHolder.uuid(), newHolder.name(), 200L));

        onTick = true;
        int readsBefore = store.reads;
        assertThat(onlineMode.matchByName("Bob")).isEqualTo(NameMatch.found(oldHolder));
        assertThat(store.reads).as("the tick answers from memory alone").isEqualTo(readsBefore);

        onTick = false;
        assertThat(onlineMode.matchByName("Bob")).isEqualTo(NameMatch.found(newHolder));
        // What the store said is remembered, so the tick answers the same from then on.
        onTick = true;
        assertThat(onlineMode.matchByName("BOB")).isEqualTo(NameMatch.found(newHolder));
    }

    @Test
    void onlineModeAHolderTheStoreHasSeenUnderAnotherNameSinceIsLetGo() {
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        StoredNames store = new StoredNames();
        store.upsert(new PlayerName(bob.uuid(), bob.name(), 100L));
        names.backWith(store, 10);
        // Bob renamed and joined another backend under the new name.
        store.upsert(new PlayerName(bob.uuid(), "Robert", 300L));

        assertThat(onlineMode.matchByName("bob")).isEqualTo(NameMatch.unknown());
        assertThat(names.byName("bob")).isEmpty();
        assertThat(names.byName("robert")).containsExactly(new PlayerRef(bob.uuid(), "Robert"));
    }

    @Test
    void onlineModeAJoinHereNewerThanTheStoreWinsWhileItsRowIsBeingWritten() {
        PlayerRef earlier = new PlayerRef(UUID.randomUUID(), "Bob");
        StoredNames store = new StoredNames();
        store.upsert(new PlayerName(earlier.uuid(), earlier.name(), 100L));
        names.backWith(store, 10);
        PlayerRef joined = new PlayerRef(UUID.randomUUID(), "bob");

        // The join is remembered at once; its row is written later, off the tick.
        names.record(joined.uuid(), joined.name());

        assertThat(onlineMode.matchByName("Bob")).isEqualTo(NameMatch.found(joined));
    }

    @Test
    void offlineModeARenamedBedrockAccountIsNotAnsweredForItsOldGamertagOffTheTick() {
        PlayerRef taker = new PlayerRef(UUID.fromString("00000000-0000-0000-0009-0000000000aa"), BEDROCK.name());
        StoredNames store = new StoredNames();
        store.upsert(new PlayerName(BEDROCK.uuid(), BEDROCK.name(), 100L));
        names.backWith(store, 10);
        // The gamertag changed hands: its first holder renamed, another account took it, both on another backend.
        store.upsert(new PlayerName(BEDROCK.uuid(), ".Renamed", 300L));
        store.upsert(new PlayerName(taker.uuid(), taker.name(), 250L));

        onTick = true;
        int readsBefore = store.reads;
        assertThat(lookup.matchByName(BEDROCK.name())).isEqualTo(NameMatch.found(BEDROCK));
        assertThat(store.reads).isEqualTo(readsBefore);

        onTick = false;
        assertThat(lookup.matchByName(BEDROCK.name())).isEqualTo(NameMatch.found(taker));
        onTick = true;
        assertThat(lookup.matchByName(BEDROCK.name())).isEqualTo(NameMatch.found(taker));
    }

    @Test
    void offlineModeAnExactSpellingTheStoreConfirmsStillWinsOverAVariant() {
        StoredNames store = new StoredNames();
        store.upsert(new PlayerName(STEVE.uuid(), STEVE.name(), 100L));
        store.upsert(new PlayerName(LOWER_STEVE.uuid(), LOWER_STEVE.name(), 200L));
        names.backWith(store, 10);

        assertThat(lookup.matchByName("Steve")).isEqualTo(NameMatch.found(STEVE));
        assertThat(lookup.matchByName("steve")).isEqualTo(NameMatch.found(LOWER_STEVE));
        assertThat(lookup.matchByName("STEVE")).isInstanceOf(NameMatch.Ambiguous.class);
    }

    @Test
    void theMemoryAloneAnswersForANameItHoldsWhenTheStoreIsNotRead() {
        StoredNames store = new StoredNames();
        store.upsert(new PlayerName(ALEX.uuid(), ALEX.name(), 100L));
        names.backWith(store, 10);
        int readsBefore = store.reads;

        assertThat(onlineMode.matchByNameFromMemory("alex")).isEqualTo(NameMatch.found(ALEX));
        assertThat(lookup.matchByNameFromMemory("Alex")).isEqualTo(NameMatch.found(ALEX));

        assertThat(store.reads).isEqualTo(readsBefore);
    }

    @Test
    void offlineModeTheServerCacheAnswersOnlyForTheSpellingTyped() {
        server.profiles.put(STEVE.uuid(), STEVE);

        assertThat(lookup.matchByName("steve")).isEqualTo(NameMatch.unknown());
        assertThat(lookup.matchByName("Steve")).isEqualTo(NameMatch.found(STEVE));
        assertThat(onlineMode.matchByName("steve")).isEqualTo(NameMatch.found(STEVE));
    }

    @Test
    void onlineModeCaseIsIgnoredAndTheAccountLastSeenUnderTheNameWins() {
        // An online-mode account that held the name before a rename, and the one that holds it now.
        names.record(LOWER_STEVE.uuid(), LOWER_STEVE.name());
        names.record(STEVE.uuid(), STEVE.name());

        assertThat(onlineMode.matchByName("steve")).isEqualTo(NameMatch.found(STEVE));
        assertThat(onlineMode.matchByName("STEVE")).isEqualTo(NameMatch.found(STEVE));

        joinAndRecord(LOWER_STEVE, FIRST);
        assertThat(onlineMode.findByName("Steve").map(PlayerRef::uuid)).contains(LOWER_STEVE.uuid());
        assertThat(onlineMode.findOnlineByName("STEVE").map(PlayerRef::uuid)).contains(LOWER_STEVE.uuid());
    }

    @Test
    void aRenamedAccountIsNoLongerKnownUnderItsOldName() {
        names.record(ALEX.uuid(), "Alex");
        names.record(ALEX.uuid(), "Alexandra");

        assertThat(names.byName("alex")).isEmpty();
        assertThat(names.byName("ALEXANDRA")).containsExactly(new PlayerRef(ALEX.uuid(), "Alexandra"));
    }

    @Test
    void aQuitLeavesTheOtherCaseVariantOnline() {
        joinBoth();

        online.left(STEVE.uuid(), FIRST);

        assertThat(lookup.findOnlineByName("Steve")).isEmpty();
        assertThat(lookup.findOnlineByName("steve").map(PlayerRef::uuid)).contains(LOWER_STEVE.uuid());
    }

    private void joinBoth() {
        joinAndRecord(STEVE, FIRST);
        joinAndRecord(LOWER_STEVE, OTHER);
    }

    /** What the join listeners do: the connection is noted and the name recorded. */
    private void joinAndRecord(PlayerRef player, Object session) {
        online.joined(player, session);
        names.record(player.uuid(), player.name());
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

    /** The durable name store: every account ever seen, newest first. */
    private static final class StoredNames implements PlayerNameRepository {

        final List<PlayerName> rows = new ArrayList<>();
        /** How many times a typed name or an account was read from the store. */
        int reads;

        @Override
        public List<PlayerName> loadRecent(int limit) {
            return newestFirst().limit(Math.max(0, limit)).toList();
        }

        @Override
        public List<PlayerName> underName(String name, int limit) {
            reads++;
            return newestFirst()
                    .filter(row -> row.name().equalsIgnoreCase(name.strip()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public java.util.Set<String> sharedNames() {
            return rows.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            row -> row.name().toLowerCase(java.util.Locale.ROOT),
                            java.util.stream.Collectors.counting()))
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(java.util.Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        @Override
        public java.util.Optional<PlayerName> byUuid(java.util.UUID uuid) {
            reads++;
            return rows.stream().filter(row -> row.uuid().equals(uuid)).reduce((first, second) -> second);
        }

        @Override
        public void upsert(PlayerName record) {
            // One row per account, as the table keeps it.
            rows.removeIf(row -> row.uuid().equals(record.uuid()));
            rows.add(record);
        }

        @Override
        public int count() {
            return rows.size();
        }

        private java.util.stream.Stream<PlayerName> newestFirst() {
            return rows.stream()
                    .sorted(java.util.Comparator.comparingLong(PlayerName::lastSeen)
                            .reversed());
        }
    }

    /** The index runs without a database here, so it never schedules a write. */
    private static final class NoScheduler implements Scheduler {

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }

    private static final class QuietLog implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
