package com.uxplima.uxmessentials.persistence.ip;

import static com.uxplima.uxmessentials.persistence.jooq.tables.IpHistory.IP_HISTORY;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The jOOQ-backed {@link IpHistoryStore} over the generated {@code IP_HISTORY} table. One row per
 * account-and-token carries the epoch-millis instants the link was first and last seen, plus the raw address when
 * something retains it. Every statement is typed jOOQ DSL: no SQL is string-concatenated.
 *
 * <p>{@link #record} upserts on the {@code (uuid, ip_token)} key, sliding {@code last_seen} forward when the same
 * account reconnects from the same address and leaving {@code first_seen} where it is. A null address leaves the
 * stored one alone rather than clearing it, so turning moderation off stops new retention without erasing the
 * history staff already had.
 */
public final class JooqIpHistoryStore extends JooqRepository implements IpHistoryStore {

    public JooqIpHistoryStore(org.jooq.DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void record(UUID account, String ipToken, @Nullable String address, Instant seenAt) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(ipToken, "ipToken");
        Objects.requireNonNull(seenAt, "seenAt");
        long at = seenAt.toEpochMilli();
        write(dsl -> {
            InsertOnDuplicateSetMoreStep<? extends Record> upsert = dsl.insertInto(IP_HISTORY)
                    .set(IP_HISTORY.UUID, account.toString())
                    .set(IP_HISTORY.IP_TOKEN, ipToken)
                    .set(IP_HISTORY.IP, address)
                    .set(IP_HISTORY.FIRST_SEEN, at)
                    .set(IP_HISTORY.LAST_SEEN, at)
                    .onConflict(IP_HISTORY.UUID, IP_HISTORY.IP_TOKEN)
                    .doUpdate()
                    .set(IP_HISTORY.LAST_SEEN, at);
            if (address != null) {
                upsert = upsert.set(IP_HISTORY.IP, address);
            }
            upsert.execute();
            return null;
        });
    }

    @Override
    public Set<UUID> accountsOnToken(String ipToken) {
        Objects.requireNonNull(ipToken, "ipToken");
        return read(dsl -> dsl.selectDistinct(IP_HISTORY.UUID)
                        .from(IP_HISTORY)
                        .where(IP_HISTORY.IP_TOKEN.eq(ipToken))
                        .fetch(IP_HISTORY.UUID))
                .stream()
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<IpAssociation> sharingTokenWith(UUID account) {
        Objects.requireNonNull(account, "account");
        return read(dsl -> dsl.select(IP_HISTORY.UUID, IP_HISTORY.IP_TOKEN)
                .from(IP_HISTORY)
                .where(IP_HISTORY.IP_TOKEN.in(
                        dsl.select(IP_HISTORY.IP_TOKEN).from(IP_HISTORY).where(IP_HISTORY.UUID.eq(account.toString()))))
                .fetch(row ->
                        new IpAssociation(UUID.fromString(row.get(IP_HISTORY.UUID)), row.get(IP_HISTORY.IP_TOKEN))));
    }

    @Override
    public Set<String> addressesOf(UUID account) {
        Objects.requireNonNull(account, "account");
        return read(dsl -> dsl.select(IP_HISTORY.IP)
                        .from(IP_HISTORY)
                        .where(IP_HISTORY.UUID.eq(account.toString()).and(IP_HISTORY.IP.isNotNull()))
                        .fetch(IP_HISTORY.IP))
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }
}
