package com.uxplima.uxmessentials.shared.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import org.jspecify.annotations.Nullable;

/**
 * The IP history as every reader sees it: with the relay addresses left out, so {@code /alts}, {@code /ipalts}, the
 * account cap and a STRICT ban never treat the players a relay carries as one person.
 *
 * <p>Nothing new is recorded on a relay. The rows written before an address was listed stay in the table and are left
 * out on the way back: by the address where it was kept, and by its token for loopback and every single address
 * listed. A row on a listed range whose address was not kept cannot be told from any other, so it stays until it ages
 * out.
 */
public final class RelayFilteredIpHistory implements IpHistoryStore {

    private final IpHistoryStore store;
    private final RelayAddresses relays;
    private final Set<String> relayTokens;

    public RelayFilteredIpHistory(IpHistoryStore store, IpTokens tokens, RelayAddresses relays) {
        this.store = Objects.requireNonNull(store, "store");
        Objects.requireNonNull(tokens, "tokens");
        this.relays = Objects.requireNonNull(relays, "relays");
        this.relayTokens =
                relays.singleAddresses().stream().map(tokens::tokenFor).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void record(UUID account, String ipToken, @Nullable String address, Instant seenAt) {
        if (relayTokens.contains(ipToken) || (address != null && relays.isRelay(address))) {
            return;
        }
        store.record(account, ipToken, address, seenAt);
    }

    @Override
    public Set<UUID> accountsOnToken(String ipToken) {
        return relayTokens.contains(ipToken) ? Set.of() : store.accountsOnToken(ipToken);
    }

    @Override
    public List<IpAssociation> sharingTokenWith(UUID account) {
        return store.sharingTokenWith(account).stream()
                .filter(link -> !relayTokens.contains(link.ipToken()))
                .toList();
    }

    @Override
    public Set<String> addressesOf(UUID account) {
        return store.addressesOf(account).stream()
                .filter(address -> !relays.isRelay(address))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<UsedAddress> usedBy(UUID account, int limit) {
        return store.usedBy(account, limit).stream().filter(this::tellsApart).toList();
    }

    @Override
    public Set<String> quietTokens(Collection<String> ipTokens, int most) {
        return store.quietTokens(withoutRelays(ipTokens), most);
    }

    @Override
    public Set<UUID> accountsOnTokens(Collection<String> ipTokens, int limit) {
        return store.accountsOnTokens(withoutRelays(ipTokens), limit);
    }

    private boolean tellsApart(UsedAddress used) {
        String address = used.address();
        return !relayTokens.contains(used.token()) && (address == null || !relays.isRelay(address));
    }

    private Set<String> withoutRelays(Collection<String> ipTokens) {
        return ipTokens.stream().filter(token -> !relayTokens.contains(token)).collect(Collectors.toUnmodifiableSet());
    }
}
