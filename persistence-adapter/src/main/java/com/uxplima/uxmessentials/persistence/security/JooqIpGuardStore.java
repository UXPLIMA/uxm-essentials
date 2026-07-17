package com.uxplima.uxmessentials.persistence.security;

import static com.uxplima.uxmessentials.persistence.jooq.tables.SecurityIp.SECURITY_IP;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import com.uxplima.uxmessentials.security.domain.IpAssociation;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link IpGuardStore} over the generated {@code SECURITY_IP} table. One row per
 * account-and-address carries the epoch-millis instant the link was last seen; an address is the one-way
 * {@code ip_token} the adapter computes from the connecting IP, so no raw address ever reaches the DB. Every
 * statement is typed jOOQ DSL — no SQL is string-concatenated.
 *
 * <p>{@link #record} upserts on the {@code (uuid, ip_token)} key, sliding {@code last_seen} forward when the same
 * account reconnects from the same address; {@link #accountsOnIp} backs the join-time cap; {@link #sharingIpWith}
 * returns the bounded slice of associations on the account's own addresses, which the domain groups into alts.
 */
public final class JooqIpGuardStore extends JooqRepository implements IpGuardStore {

    public JooqIpGuardStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void record(UUID account, String ipToken, Instant seenAt) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(ipToken, "ipToken");
        Objects.requireNonNull(seenAt, "seenAt");
        long lastSeen = seenAt.toEpochMilli();
        write(dsl -> {
            dsl.insertInto(SECURITY_IP)
                    .set(SECURITY_IP.UUID, account.toString())
                    .set(SECURITY_IP.IP_TOKEN, ipToken)
                    .set(SECURITY_IP.LAST_SEEN, lastSeen)
                    .onConflict(SECURITY_IP.UUID, SECURITY_IP.IP_TOKEN)
                    .doUpdate()
                    .set(SECURITY_IP.LAST_SEEN, lastSeen)
                    .execute();
            return null;
        });
    }

    @Override
    public Set<UUID> accountsOnIp(String ipToken) {
        Objects.requireNonNull(ipToken, "ipToken");
        return read(dsl -> dsl.selectDistinct(SECURITY_IP.UUID)
                        .from(SECURITY_IP)
                        .where(SECURITY_IP.IP_TOKEN.eq(ipToken))
                        .fetch(SECURITY_IP.UUID))
                .stream()
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<IpAssociation> sharingIpWith(UUID account) {
        Objects.requireNonNull(account, "account");
        return read(dsl -> dsl.select(SECURITY_IP.UUID, SECURITY_IP.IP_TOKEN)
                .from(SECURITY_IP)
                .where(SECURITY_IP.IP_TOKEN.in(dsl.select(SECURITY_IP.IP_TOKEN)
                        .from(SECURITY_IP)
                        .where(SECURITY_IP.UUID.eq(account.toString()))))
                .fetch(row ->
                        new IpAssociation(UUID.fromString(row.get(SECURITY_IP.UUID)), row.get(SECURITY_IP.IP_TOKEN))));
    }
}
