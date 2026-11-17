package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Two spellings of a name are linked by an address only when the address tells people apart: a relay, a private or
 * carrier-grade NAT address, a busy one and one the operator ignores never link an innocent account to a sanction.
 */
class CaseVariantAddressesTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID UPPER_STEVE = UUID.randomUUID();
    private static final IpTokens TOKENS = address -> "token:" + address;

    private final History history = new History();
    private final CaseVariantAddresses links =
            new CaseVariantAddresses(history, TOKENS, AddressFilter.of(List.of("198.51.100.0/24"), new Quiet()), 3);

    @Test
    void aQuietPublicAddressBothUsedLinksThem() {
        history.used(STEVE, "203.0.113.9");
        history.used(UPPER_STEVE, "203.0.113.9");

        assertThat(links.sharing(UPPER_STEVE, Optional.empty())).containsExactly(STEVE);
        assertThat(links.sharing(STEVE, Optional.empty())).containsExactly(UPPER_STEVE);
    }

    @Test
    void theAddressALoginComesFromNowLinksItToo() {
        history.used(STEVE, "203.0.113.9");

        assertThat(links.sharing(UPPER_STEVE, Optional.of("203.0.113.9"))).containsExactly(STEVE);
    }

    @Test
    void aGeyserRelayLinksNobody() {
        history.used(STEVE, "127.0.0.1");
        history.used(UPPER_STEVE, "127.0.0.1");

        assertThat(links.sharing(UPPER_STEVE, Optional.of("127.0.0.1"))).isEmpty();
        assertThat(links.sharing(UPPER_STEVE, Optional.of("::1"))).isEmpty();
    }

    @Test
    void privateLinkLocalAndCarrierNatAddressesLinkNobody() {
        for (String shared :
                List.of("192.168.1.20", "10.0.0.4", "172.16.5.5", "169.254.1.1", "100.64.3.4", "fd00::7")) {
            history.used(STEVE, shared);
            history.used(UPPER_STEVE, shared);
        }

        assertThat(links.sharing(UPPER_STEVE, Optional.of("100.127.255.1"))).isEmpty();
    }

    @Test
    void aBusyAddressLinksNobody() {
        history.used(STEVE, "203.0.113.50");
        history.used(UPPER_STEVE, "203.0.113.50");
        history.used(UUID.randomUUID(), "203.0.113.50");
        history.used(UUID.randomUUID(), "203.0.113.50");

        assertThat(links.sharing(UPPER_STEVE, Optional.of("203.0.113.50"))).isEmpty();
    }

    @Test
    void anAddressTheOperatorIgnoresLinksNobody() {
        history.used(STEVE, "198.51.100.7");
        history.used(UPPER_STEVE, "198.51.100.7");

        assertThat(links.sharing(UPPER_STEVE, Optional.empty())).isEmpty();
    }

    @Test
    void anAddressThatWasNotKeptIsNotTrusted() {
        history.usedUnkept(STEVE, "203.0.113.9");
        history.usedUnkept(UPPER_STEVE, "203.0.113.9");

        assertThat(links.sharing(UPPER_STEVE, Optional.empty())).isEmpty();
    }

    /** The address history in memory: one row per account and address, the address kept or not. */
    private static final class History implements IpHistoryStore {

        private record Row(
                UUID account, String token, @Nullable String address) {}

        private final List<Row> rows = new ArrayList<>();

        void used(UUID account, String address) {
            rows.add(new Row(account, TOKENS.tokenFor(address), address));
        }

        void usedUnkept(UUID account, String address) {
            rows.add(new Row(account, TOKENS.tokenFor(address), null));
        }

        @Override
        public void record(UUID account, String ipToken, @Nullable String address, Instant seenAt) {
            rows.add(new Row(account, ipToken, address));
        }

        @Override
        public Set<UUID> accountsOnToken(String ipToken) {
            return accountsOnTokens(List.of(ipToken), Integer.MAX_VALUE);
        }

        @Override
        public List<IpAssociation> sharingTokenWith(UUID account) {
            return List.of();
        }

        @Override
        public Set<String> addressesOf(UUID account) {
            return Set.of();
        }

        @Override
        public List<UsedAddress> usedBy(UUID account, int limit) {
            return rows.stream()
                    .filter(row -> row.account().equals(account))
                    .limit(limit)
                    .map(row -> new UsedAddress(row.token(), row.address()))
                    .toList();
        }

        @Override
        public Set<String> quietTokens(Collection<String> ipTokens, int most) {
            Set<String> quiet = new HashSet<>();
            for (String token : ipTokens) {
                if (accountsOnToken(token).size() <= most) {
                    quiet.add(token);
                }
            }
            return quiet;
        }

        @Override
        public Set<UUID> accountsOnTokens(Collection<String> ipTokens, int limit) {
            Set<UUID> accounts = new LinkedHashSet<>();
            for (Row row : rows) {
                if (ipTokens.contains(row.token()) && accounts.size() < limit) {
                    accounts.add(row.account());
                }
            }
            return accounts;
        }
    }

    private static final class Quiet implements Logger {

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
