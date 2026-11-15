package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import org.jspecify.annotations.Nullable;

/** An IP history that has seen nobody, so no address has alts. */
final class NoIpHistory implements IpHistoryStore {

    @Override
    public void record(UUID account, String ipToken, @Nullable String address, Instant seenAt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<UUID> accountsOnToken(String ipToken) {
        return Set.of();
    }

    @Override
    public List<IpAssociation> sharingTokenWith(UUID account) {
        return List.of();
    }

    @Override
    public Set<String> addressesOf(UUID account) {
        return Set.of();
    }
}
