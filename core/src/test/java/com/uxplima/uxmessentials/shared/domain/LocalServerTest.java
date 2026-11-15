package com.uxplima.uxmessentials.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Which scopes apply on a server, and how a typed scope word reads. */
class LocalServerTest {

    private static final LocalServer LOBBY_1 = new LocalServer("lobby-1", List.of("lobby", "hubs"));

    @Test
    void noScopeAppliesOnEveryServer() {
        assertThat(LOBBY_1.covers(null)).isTrue();
        assertThat(new LocalServer("survival", List.of()).covers(null)).isTrue();
    }

    @Test
    void aServerScopeAppliesOnlyOnThatServer() {
        assertThat(LOBBY_1.covers(ServerScope.server("lobby-1"))).isTrue();
        assertThat(LOBBY_1.covers(ServerScope.server("lobby-2"))).isFalse();
    }

    @Test
    void aSetScopeAppliesOnEveryServerThatListsTheSet() {
        assertThat(LOBBY_1.covers(ServerScope.set("lobby"))).isTrue();
        assertThat(LOBBY_1.covers(ServerScope.set("hubs"))).isTrue();
        assertThat(LOBBY_1.covers(ServerScope.set("minigames"))).isFalse();
    }

    @Test
    void aServerNamedLikeASetIsNotInThatSet() {
        LocalServer named = new LocalServer("lobby", List.of());

        assertThat(named.covers(ServerScope.set("lobby"))).isFalse();
        assertThat(named.covers(ServerScope.server("lobby"))).isTrue();
    }

    @Test
    void theServersOwnScopeComesBeforeItsSets() {
        assertThat(LOBBY_1.loadedScopes())
                .containsExactly(ServerScope.server("lobby-1"), ServerScope.set("lobby"), ServerScope.set("hubs"));
    }

    @Test
    void aTypedScopeReadsTheWordsGuardTakes() {
        assertThat(LOBBY_1.parse("server")).contains(ServerScope.server("lobby-1"));
        assertThat(LOBBY_1.parse("server:survival")).contains(ServerScope.server("survival"));
        assertThat(LOBBY_1.parse("set:minigames")).contains(ServerScope.set("minigames"));
        assertThat(LOBBY_1.parse("lobby")).contains(ServerScope.set("lobby"));
        assertThat(LOBBY_1.parse("minigames")).isEmpty();
        assertThat(LOBBY_1.parse("server:")).isEmpty();
    }
}
