package com.uxplima.uxmessentials.security.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import com.uxplima.uxmessentials.security.domain.AltGroup;

/**
 * The read behind {@code /alts <player>}: resolve the accounts that share an IP token with a target into an
 * {@link AltGroup}. It reads the target's shared-IP associations through the {@link IpGuardStore} and folds them
 * with the pure {@link AltGroup#of} grouping rule, so the SQL stays a bounded slice and the grouping stays domain
 * logic. The command runs it off the tick thread; this class holds no Bukkit type.
 */
public final class FindAlts {

    private final IpGuardStore store;

    public FindAlts(IpGuardStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** The accounts sharing an IP token with {@code account}, grouped and with {@code account} itself excluded. */
    public AltGroup find(UUID account) {
        Objects.requireNonNull(account, "account");
        return AltGroup.of(account, store.sharingIpWith(account));
    }
}
