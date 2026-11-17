package com.uxplima.uxmessentials.persistence.ip;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bounded reads that decide whether an address links two accounts, through the real migrations: an account's
 * addresses newest first, the addresses few enough accounts used, and the accounts on them.
 */
@EveryBackend
class JooqIpHistoryStoreTest {

    private static final UUID STEVE = new UUID(0L, 11L);
    private static final UUID UPPER_STEVE = new UUID(0L, 12L);
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private IpHistoryStore store;

    @BeforeEach
    void open(TestDatabase database) {
        store = new JooqIpHistoryStore(database.dsl());
    }

    @Test
    void anAccountsAddressesAreReadNewestFirstUpToTheLimitWithTheAddressWhenKept() {
        store.record(STEVE, "home", "203.0.113.9", NOW.minusSeconds(60));
        store.record(STEVE, "phone", null, NOW);

        assertThat(store.usedBy(STEVE, 5))
                .containsExactly(
                        new IpHistoryStore.UsedAddress("phone", null),
                        new IpHistoryStore.UsedAddress("home", "203.0.113.9"));
        assertThat(store.usedBy(STEVE, 1)).hasSize(1);
    }

    @Test
    void onlyAnAddressFewAccountsUsedIsQuietAndItsAccountsAreReadUpToTheLimit() {
        store.record(STEVE, "home", "203.0.113.9", NOW);
        store.record(UPPER_STEVE, "home", "203.0.113.9", NOW);
        for (int i = 0; i < 4; i++) {
            store.record(new UUID(1L, i), "cafe", "203.0.113.50", NOW);
        }

        assertThat(store.quietTokens(List.of("home", "cafe", "nowhere"), 3)).containsExactly("home");
        assertThat(store.accountsOnTokens(List.of("home"), 10)).containsExactlyInAnyOrder(STEVE, UPPER_STEVE);
        assertThat(store.accountsOnTokens(List.of("cafe"), 2)).hasSize(2);
    }
}
