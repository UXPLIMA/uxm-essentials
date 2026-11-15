package com.uxplima.uxmessentials.worlds.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import org.junit.jupiter.api.Test;

/** Only a backend on a network under a name of its own records itself on its worlds. */
class WorldScopeTest {

    @Test
    void aServerOffTheNetworkRecordsNoServerWhateverItIsCalled() {
        assertThat(WorldScope.of(false, "lobby")).isEqualTo(NetworkWorld.UNSCOPED);
    }

    @Test
    void aServerUnderTheShippedDefaultNameRecordsNoServerAndSoClaimsNothing() {
        assertThat(WorldScope.of(true, "server-1")).isEqualTo(NetworkWorld.UNSCOPED);
    }

    @Test
    void aNameNoRowCouldHoldRecordsNoServer() {
        assertThat(WorldScope.of(true, "unscoped")).isEqualTo(NetworkWorld.UNSCOPED);
        assertThat(WorldScope.of(true, "x".repeat(65))).isEqualTo(NetworkWorld.UNSCOPED);
        assertThat(WorldScope.of(true, "has space")).isEqualTo(NetworkWorld.UNSCOPED);
    }

    @Test
    void aNamedServerOnTheNetworkRecordsItsName() {
        assertThat(WorldScope.of(true, "lobby")).isEqualTo("lobby");
    }
}
