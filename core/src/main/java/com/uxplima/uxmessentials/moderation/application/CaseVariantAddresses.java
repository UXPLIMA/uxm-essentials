package com.uxplima.uxmessentials.moderation.application;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;

/**
 * The accounts that shared an address with an account, for a sanction to follow its player into another spelling of
 * the name however old that account is. Only an address that tells people apart counts: not one the
 * {@link AddressFilter} ignores, and not one more than {@code busyAfter} accounts have used, which is a household at
 * most and otherwise a relay, a proxy without forwarding or a shared connection. Every read is bounded.
 */
public final class CaseVariantAddresses implements CaseVariantSanctions.AddressLinks {

    /** The most addresses of one account looked at, its most recent first. */
    static final int MOST_ADDRESSES = 64;

    private final IpHistoryStore store;
    private final IpTokens tokens;
    private final AddressFilter filter;
    private final int busyAfter;

    /** @param busyAfter the most accounts an address may have been used by and still count */
    public CaseVariantAddresses(IpHistoryStore store, IpTokens tokens, AddressFilter filter, int busyAfter) {
        this.store = Objects.requireNonNull(store, "store");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.filter = Objects.requireNonNull(filter, "filter");
        if (busyAfter < 1) {
            throw new IllegalArgumentException("busyAfter must be at least 1: " + busyAfter);
        }
        this.busyAfter = busyAfter;
    }

    @Override
    public Set<UUID> sharing(UUID account, Optional<String> address) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(address, "address");
        Set<String> telling = new HashSet<>();
        for (IpHistoryStore.UsedAddress used : store.usedBy(account, MOST_ADDRESSES)) {
            // A token whose address was not kept cannot be checked, so it is not trusted.
            String raw = used.address();
            if (raw != null && filter.counts(raw)) {
                telling.add(used.token());
            }
        }
        address.filter(filter::counts).ifPresent(current -> telling.add(tokens.tokenFor(current)));
        if (telling.isEmpty()) {
            return Set.of();
        }
        Set<String> quiet = store.quietTokens(telling, busyAfter);
        if (quiet.isEmpty()) {
            return Set.of();
        }
        Set<UUID> sharing = new HashSet<>(store.accountsOnTokens(quiet, quiet.size() * busyAfter));
        sharing.remove(account);
        return Set.copyOf(sharing);
    }
}
