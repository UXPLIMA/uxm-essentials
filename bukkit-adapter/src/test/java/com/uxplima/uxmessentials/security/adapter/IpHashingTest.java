package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Pins the IP tokeniser: the same address under the same key always gives the same token (that collision is what
 * device trust and the alt lookup are built on), the token never carries the address, and the same address under a
 * different key gives a different token. That last one is the property that makes a stolen database useless on its
 * own: without the server's key-file, an attacker cannot hash the four billion IPv4 addresses and read the tokens
 * back, which an unkeyed digest would have let them do in minutes.
 */
class IpHashingTest {

    private static final String ADDRESS = "203.0.113.7";

    private final IpHashing hashing = keyed("server-key");

    @Test
    void theSameAddressAlwaysGivesTheSameHexToken() {
        String token = hashing.hash(ADDRESS);

        assertThat(token).isEqualTo(hashing.hash(ADDRESS)).hasSize(64).matches("[0-9a-f]+");
        assertThat(hashing.hash("203.0.113.8")).isNotEqualTo(token);
    }

    @Test
    void theTokenNeverCarriesTheAddress() {
        assertThat(hashing.hash(ADDRESS)).doesNotContain(ADDRESS).doesNotContain("203");
    }

    @Test
    void aDifferentKeyGivesADifferentTokenForTheSameAddress() {
        assertThat(keyed("other-key").hash(ADDRESS)).isNotEqualTo(hashing.hash(ADDRESS));
    }

    @Test
    void anEmptyKeyIsRefused() {
        assertThatThrownBy(() -> new IpHashing(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }

    private static IpHashing keyed(String key) {
        return new IpHashing(key.getBytes(StandardCharsets.UTF_8));
    }
}
