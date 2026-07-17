package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import com.uxplima.uxmessentials.security.domain.AltGroup;
import com.uxplima.uxmessentials.security.domain.IpAssociation;
import org.junit.jupiter.api.Test;

/** {@link FindAlts} folds the store's shared-IP associations into an {@link AltGroup} keyed on the target. */
class FindAltsTest {

    @Test
    void groupsTheAccountsSharingAnIpWithTheTarget() {
        UUID target = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        FakeIpGuardStore store = new FakeIpGuardStore();
        store.record(target, "ip-a", Instant.EPOCH);
        store.record(alt, "ip-a", Instant.EPOCH);
        store.record(far, "ip-z", Instant.EPOCH);

        AltGroup group = new FindAlts(store).find(target);

        assertThat(group.alts()).containsExactly(alt);
    }

    /** An in-memory {@link IpGuardStore} mirroring the jOOQ store's contract for the use-case tests. */
    private static final class FakeIpGuardStore implements IpGuardStore {
        private final List<IpAssociation> rows = new ArrayList<>();

        @Override
        public void record(UUID account, String ipToken, Instant seenAt) {
            IpAssociation link = new IpAssociation(account, ipToken);
            if (!rows.contains(link)) {
                rows.add(link);
            }
        }

        @Override
        public Set<UUID> accountsOnIp(String ipToken) {
            return rows.stream()
                    .filter(link -> link.ipToken().equals(ipToken))
                    .map(IpAssociation::account)
                    .collect(Collectors.toSet());
        }

        @Override
        public List<IpAssociation> sharingIpWith(UUID account) {
            Set<String> tokens = rows.stream()
                    .filter(link -> link.account().equals(account))
                    .map(IpAssociation::ipToken)
                    .collect(Collectors.toSet());
            return rows.stream().filter(link -> tokens.contains(link.ipToken())).toList();
        }
    }
}
