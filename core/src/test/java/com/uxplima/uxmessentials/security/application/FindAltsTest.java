package com.uxplima.uxmessentials.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.FakeIpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.AltGroup;
import org.junit.jupiter.api.Test;

/**
 * {@link FindAlts} folds the kernel IP-history store's shared-token associations into an {@link AltGroup} keyed on
 * the target. The rows are the same ones moderation's {@code /alts} reads, so both commands answer from one history.
 */
class FindAltsTest {

    @Test
    void groupsTheAccountsSharingAnIpWithTheTarget() {
        UUID target = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        FakeIpHistoryStore store = new FakeIpHistoryStore();
        store.seen(target, "ip-a", null);
        store.seen(alt, "ip-a", null);
        store.seen(far, "ip-z", null);

        AltGroup group = new FindAlts(store).find(target);

        assertThat(group.alts()).containsExactly(alt);
    }

    @Test
    void anAccountWithNoSharedTokenHasNoAlts() {
        UUID target = UUID.randomUUID();
        FakeIpHistoryStore store = new FakeIpHistoryStore();
        store.seen(target, "ip-a", null);
        store.seen(UUID.randomUUID(), "ip-b", null);

        assertThat(new FindAlts(store).find(target).alts()).isEmpty();
    }
}
