package com.uxplima.uxmessentials.shared.application.port;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import org.jspecify.annotations.Nullable;

/**
 * An in-memory {@link IpHistoryStore} mirroring the jOOQ store's contract: one row per {@code (account, token)},
 * a raw address kept only when one is passed (and never cleared by a later null), and reads that return the same
 * bounded slices the SQL does. Shared by the moderation and security use-case tests so both exercise the same
 * store behaviour the production adapter has.
 */
public final class FakeIpHistoryStore implements IpHistoryStore {

    private final Map<Key, Row> rows = new ConcurrentHashMap<>();

    /** Record an association the way a join would, in tests that do not care about the instant. */
    public void seen(UUID account, String ipToken, @Nullable String address) {
        record(account, ipToken, address, Instant.EPOCH);
    }

    @Override
    public void record(UUID account, String ipToken, @Nullable String address, Instant seenAt) {
        rows.compute(new Key(account, ipToken), (key, existing) -> {
            String kept = address != null ? address : existing == null ? null : existing.address();
            return new Row(kept, seenAt);
        });
    }

    @Override
    public Set<UUID> accountsOnToken(String ipToken) {
        return rows.keySet().stream()
                .filter(key -> key.ipToken().equals(ipToken))
                .map(Key::account)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<IpAssociation> sharingTokenWith(UUID account) {
        Set<String> own = rows.keySet().stream()
                .filter(key -> key.account().equals(account))
                .map(Key::ipToken)
                .collect(Collectors.toUnmodifiableSet());
        Set<IpAssociation> slice = new LinkedHashSet<>();
        rows.keySet().stream()
                .filter(key -> own.contains(key.ipToken()))
                .forEach(key -> slice.add(new IpAssociation(key.account(), key.ipToken())));
        return List.copyOf(slice);
    }

    @Override
    public Set<String> addressesOf(UUID account) {
        return rows.entrySet().stream()
                .filter(entry -> entry.getKey().account().equals(account))
                .map(entry -> entry.getValue().address())
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record Key(UUID account, String ipToken) {}

    private record Row(@Nullable String address, Instant lastSeen) {}
}
