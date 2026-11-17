package com.uxplima.uxmessentials.moderation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * A sanction names the account online on any backend before an offline owner of the same name, takes a uuid wherever
 * it takes a name, and names an account that never joined by its uuid or, on an offline-mode server, by its name.
 */
class PlayerLookupTargetResolverTest {

    private static final PlayerRef ONLINE_ELSEWHERE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef CACHED_OWNER = new PlayerRef(UUID.randomUUID(), "steve");
    private static final UUID NEVER_SEEN = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private NameCase nameCase = NameCase.INSENSITIVE;

    private final PlayerLookup offlineCache = new PlayerLookup() {
        @Override
        public NameCase nameCase() {
            return nameCase;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return name.equalsIgnoreCase("steve") ? Optional.of(CACHED_OWNER) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return uuid.equals(CACHED_OWNER.uuid()) ? Optional.of(CACHED_OWNER) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    };

    @Test
    void aPlayerOnAnotherBackendIsResolvedAheadOfTheOfflineCache() {
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(
                presence(List.of(new OnlinePlayer(ONLINE_ELSEWHERE, "lobby-1"))), offlineCache);

        assertThat(resolver.resolve("STEVE").player().map(PlayerRef::uuid)).contains(ONLINE_ELSEWHERE.uuid());
    }

    @Test
    void aPlayerOnlineNowhereFallsBackToTheOfflineCache() {
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(presence(List.of()), offlineCache);

        assertThat(resolver.resolve("steve").player().map(PlayerRef::uuid)).contains(CACHED_OWNER.uuid());
    }

    @Test
    void offlineModePresenceSettlesOnlyTheSpellingThatWasTyped() {
        nameCase = NameCase.SENSITIVE;
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(
                presence(List.of(new OnlinePlayer(ONLINE_ELSEWHERE, "lobby-1"))), offlineCache);

        assertThat(resolver.resolve("Steve").player().map(PlayerRef::uuid)).contains(ONLINE_ELSEWHERE.uuid());
        // "steve" is another offline-mode account than the "Steve" online elsewhere.
        assertThat(resolver.resolve("steve").player().map(PlayerRef::uuid)).contains(CACHED_OWNER.uuid());
    }

    @Test
    void aUuidNamesTheAccountOnlineElsewhereOrKnownOfflineWithOrWithoutDashes() {
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(
                presence(List.of(new OnlinePlayer(ONLINE_ELSEWHERE, "lobby-1"))), offlineCache);

        assertThat(resolver.resolve(ONLINE_ELSEWHERE.uuid().toString()).player())
                .contains(ONLINE_ELSEWHERE);
        assertThat(resolver.resolve(CACHED_OWNER.uuid().toString().replace("-", ""))
                        .player())
                .hasValueSatisfying(found -> assertThat(found.name()).isEqualTo("steve"));
    }

    @Test
    void aUuidNobodyHasSeenNamesTheAccountItWillJoinAs() {
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(presence(List.of()), offlineCache);
        String typed = NEVER_SEEN.toString().toUpperCase(java.util.Locale.ROOT);

        assertThat(resolver.resolve(typed).player()).isEmpty();
        assertThat(resolver.neverJoined(typed)).contains(new PlayerRef(NEVER_SEEN, NEVER_SEEN.toString()));
        assertThat(resolver.resolveOrNeverJoined(typed).player().map(PlayerRef::uuid))
                .contains(NEVER_SEEN);
    }

    @Test
    void offlineModeANameNobodyHasUsedNamesTheAccountItsSpellingLogsInAs() {
        nameCase = NameCase.SENSITIVE;
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(presence(List.of()), offlineCache);

        assertThat(resolver.neverJoined("Griefer"))
                .contains(new PlayerRef(
                        UUID.nameUUIDFromBytes("OfflinePlayer:Griefer".getBytes(StandardCharsets.UTF_8)), "Griefer"));
        assertThat(resolver.neverJoined(".BedrockGriefer"))
                .as("Floodgate makes that account")
                .isEmpty();
    }

    @Test
    void withAuthenticatedLoginsANameNobodyHasUsedNamesNoAccount() {
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(presence(List.of()), offlineCache);

        assertThat(resolver.neverJoined("Griefer")).isEmpty();
        assertThat(resolver.resolveOrNeverJoined("Griefer").player()).isEmpty();
    }

    private static NetworkPresence presence(List<OnlinePlayer> online) {
        return new NetworkPresence() {
            @Override
            public String localServer() {
                return "survival-1";
            }

            @Override
            public Optional<OnlinePlayer> find(UUID player) {
                return online.stream()
                        .filter(presence -> presence.player().uuid().equals(player))
                        .findFirst();
            }

            @Override
            public Optional<OnlinePlayer> findByName(String name) {
                return online.stream()
                        .filter(presence -> presence.player().name().equalsIgnoreCase(name))
                        .findFirst();
            }

            @Override
            public List<OnlinePlayer> onServer(String server) {
                return online.stream()
                        .filter(presence -> presence.server().equals(server))
                        .toList();
            }

            @Override
            public List<OnlinePlayer> online() {
                return online;
            }
        };
    }
}
