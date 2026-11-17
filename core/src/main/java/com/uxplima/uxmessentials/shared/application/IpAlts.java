package com.uxplima.uxmessentials.shared.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.domain.AltGroup;

/**
 * The address-keyed half of alt detection: "which accounts have connected from this address". It tokenises the
 * address first and matches by token, so the lookup goes through the same rows, and the same keyed tokens, as the
 * account-keyed {@link AltGroup} lookups behind {@code /alts} and {@code /ipalts}.
 *
 * <p>Shared because three moderation flows ask the same question of a single address: a login (alt-detection
 * audit), {@code /banip} and {@code /tempbanip} (the accounts the banned address carries), and {@code /seenip}
 * (the alts on a target's last address).
 */
public final class IpAlts {

    private final IpHistoryStore store;
    private final IpTokens tokens;

    public IpAlts(IpHistoryStore store, IpTokens tokens) {
        this.store = Objects.requireNonNull(store, "store");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    /** The accounts ever seen on {@code address}, in a stable order, excluding {@code exclude}. */
    public List<UUID> onAddress(String address, UUID exclude) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(exclude, "exclude");
        return store.accountsOnToken(tokens.tokenFor(address)).stream()
                .filter(account -> !account.equals(exclude))
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }
}
