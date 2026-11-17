package com.uxplima.uxmessentials.moderation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/** A sanction names the account online on any backend before an offline owner of the same name. */
class PlayerLookupTargetResolverTest {

    private static final PlayerRef ONLINE_ELSEWHERE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef CACHED_OWNER = new PlayerRef(UUID.randomUUID(), "steve");

    private final PlayerLookup offlineCache = new PlayerLookup() {
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
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    };

    @Test
    void aPlayerOnAnotherBackendIsResolvedAheadOfTheOfflineCache() {
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(
                presence(List.of(new OnlinePlayer(ONLINE_ELSEWHERE, "lobby-1"))), offlineCache, NameCase.INSENSITIVE);

        assertThat(resolver.resolve("STEVE").player().map(PlayerRef::uuid)).contains(ONLINE_ELSEWHERE.uuid());
    }

    @Test
    void aPlayerOnlineNowhereFallsBackToTheOfflineCache() {
        PlayerLookupTargetResolver resolver =
                new PlayerLookupTargetResolver(presence(List.of()), offlineCache, NameCase.INSENSITIVE);

        assertThat(resolver.resolve("steve").player().map(PlayerRef::uuid)).contains(CACHED_OWNER.uuid());
    }

    @Test
    void offlineModePresenceSettlesOnlyTheSpellingThatWasTyped() {
        PlayerLookupTargetResolver resolver = new PlayerLookupTargetResolver(
                presence(List.of(new OnlinePlayer(ONLINE_ELSEWHERE, "lobby-1"))), offlineCache, NameCase.SENSITIVE);

        assertThat(resolver.resolve("Steve").player().map(PlayerRef::uuid)).contains(ONLINE_ELSEWHERE.uuid());
        // "steve" is another offline-mode account than the "Steve" online elsewhere.
        assertThat(resolver.resolve("steve").player().map(PlayerRef::uuid)).contains(CACHED_OWNER.uuid());
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
