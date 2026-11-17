package com.uxplima.uxmessentials.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Every Bedrock player behind a Geyser without Floodgate, and every player behind a proxy without forwarding, arrives
 * from one address. Such a relay links nobody: it is not recorded, and rows written before it was listed are not read
 * back.
 */
class RelayAddressesTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d7");
    private static final String GEYSER = "203.0.113.50";
    private static final String HOME = "198.51.100.4";
    private static final IpTokens TOKENS = address -> "token:" + address;

    private final RecordingLogger log = new RecordingLogger();
    private final RelayAddresses relays = RelayAddresses.of(List.of(GEYSER, "192.0.2.0/24", "geyser.example"), log);

    @Test
    void loopbackAndEveryListedAddressOrRangeIsARelayAndAnythingElseIsNot() {
        assertThat(relays.isRelay("127.0.0.1")).isTrue();
        assertThat(relays.isRelay("::1")).isTrue();
        assertThat(relays.isRelay(GEYSER)).isTrue();
        assertThat(relays.isRelay("192.0.2.77")).isTrue();
        assertThat(relays.isRelay(HOME)).isFalse();
        assertThat(relays.isRelay("not an address")).isFalse();
        assertThat(RelayAddresses.LOOPBACK.isRelay(GEYSER)).isFalse();
        assertThat(RelayAddresses.LOOPBACK.isRelay("127.0.0.1")).isTrue();
    }

    @Test
    void anEntryThatIsNoAddressIsNamedInTheLogAndSkipped() {
        assertThat(log.warnings()).singleElement().asString().contains("geyser.example");
        assertThat(relays.listed()).isTrue();
        assertThat(RelayAddresses.LOOPBACK.listed()).isFalse();
    }

    @Test
    void aJoinFromARelayIsNotRecordedAndOneFromHomeIs() {
        Rows rows = new Rows();
        IpHistoryStore history = new RelayFilteredIpHistory(rows, TOKENS, relays);

        history.record(ALEX, TOKENS.tokenFor(GEYSER), GEYSER, Instant.EPOCH);
        history.record(SAM, TOKENS.tokenFor(GEYSER), null, Instant.EPOCH);
        history.record(ALEX, TOKENS.tokenFor(HOME), HOME, Instant.EPOCH);

        assertThat(rows.links).containsExactly(new IpAssociation(ALEX, TOKENS.tokenFor(HOME)));
    }

    @Test
    void twoPlayersWhoOnlySharedARelayBeforeItWasListedAreNotAltsOfEachOther() {
        Rows rows = new Rows();
        rows.record(ALEX, TOKENS.tokenFor(GEYSER), GEYSER, Instant.EPOCH);
        rows.record(SAM, TOKENS.tokenFor(GEYSER), GEYSER, Instant.EPOCH);
        rows.record(ALEX, TOKENS.tokenFor("127.0.0.1"), null, Instant.EPOCH);
        rows.record(SAM, TOKENS.tokenFor("127.0.0.1"), null, Instant.EPOCH);
        rows.record(ALEX, TOKENS.tokenFor(HOME), HOME, Instant.EPOCH);
        IpHistoryStore history = new RelayFilteredIpHistory(rows, TOKENS, relays);

        assertThat(history.sharingTokenWith(ALEX)).containsExactly(new IpAssociation(ALEX, TOKENS.tokenFor(HOME)));
        assertThat(history.accountsOnToken(TOKENS.tokenFor(GEYSER))).isEmpty();
        assertThat(history.accountsOnTokens(List.of(TOKENS.tokenFor("127.0.0.1")), 10))
                .isEmpty();
        assertThat(history.addressesOf(ALEX)).containsExactly(HOME);
        assertThat(history.usedBy(ALEX, 10))
                .extracting(IpHistoryStore.UsedAddress::address)
                .containsExactly(HOME);
    }

    @Test
    void aRelayHasNoAltsWhateverTheHistoryHolds() {
        Rows rows = new Rows();
        rows.record(SAM, TOKENS.tokenFor("192.0.2.9"), "192.0.2.9", Instant.EPOCH);
        rows.record(SAM, TOKENS.tokenFor(HOME), HOME, Instant.EPOCH);
        IpAlts alts = new IpAlts(rows, TOKENS, relays);

        assertThat(alts.onAddress("192.0.2.9", ALEX)).isEmpty();
        assertThat(alts.onAddress(HOME, ALEX)).containsExactly(SAM);
    }

    /** The history table: every link, with the address beside it when one was kept. */
    private static final class Rows implements IpHistoryStore {

        private final List<IpAssociation> links = new ArrayList<>();
        private final Map<IpAssociation, String> addresses = new HashMap<>();

        @Override
        public void record(UUID account, String ipToken, @Nullable String address, Instant seenAt) {
            IpAssociation link = new IpAssociation(account, ipToken);
            links.add(link);
            if (address != null) {
                addresses.put(link, address);
            }
        }

        @Override
        public Set<UUID> accountsOnToken(String ipToken) {
            Set<UUID> accounts = new HashSet<>();
            links.stream().filter(link -> link.ipToken().equals(ipToken)).forEach(link -> accounts.add(link.account()));
            return accounts;
        }

        @Override
        public List<IpAssociation> sharingTokenWith(UUID account) {
            Set<String> own = new HashSet<>();
            links.stream().filter(link -> link.account().equals(account)).forEach(link -> own.add(link.ipToken()));
            return links.stream().filter(link -> own.contains(link.ipToken())).toList();
        }

        @Override
        public Set<String> addressesOf(UUID account) {
            Set<String> found = new HashSet<>();
            addresses.forEach((link, address) -> {
                if (link.account().equals(account)) {
                    found.add(address);
                }
            });
            return found;
        }

        @Override
        public List<UsedAddress> usedBy(UUID account, int limit) {
            return links.stream()
                    .filter(link -> link.account().equals(account))
                    .map(link -> new UsedAddress(link.ipToken(), addresses.get(link)))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Set<UUID> accountsOnTokens(java.util.Collection<String> ipTokens, int limit) {
            Set<UUID> accounts = new HashSet<>();
            links.stream()
                    .filter(link -> ipTokens.contains(link.ipToken()))
                    .forEach(link -> accounts.add(link.account()));
            return accounts;
        }
    }
}
