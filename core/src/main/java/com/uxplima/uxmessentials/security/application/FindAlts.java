package com.uxplima.uxmessentials.security.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.AltGroup;

/**
 * The read behind {@code /ipalts <player>}: resolve the accounts that share an IP token with a target into an
 * {@link AltGroup}. It reads the target's shared-token associations through the kernel {@link IpHistoryStore} (the
 * one record of who connected from where, shared with moderation's own {@code /alts}) and folds them with the pure
 * {@link AltGroup#of} grouping rule, so the SQL stays a bounded slice and the grouping stays domain logic. The
 * command runs it off the tick thread; this class holds no Bukkit type.
 */
public final class FindAlts {

    private final IpHistoryStore store;

    public FindAlts(IpHistoryStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** The accounts sharing an IP token with {@code account}, grouped and with {@code account} itself excluded. */
    public AltGroup find(UUID account) {
        Objects.requireNonNull(account, "account");
        return AltGroup.of(account, store.sharingTokenWith(account));
    }
}
