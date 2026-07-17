package com.uxplima.uxmessentials.security.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.IpAssociation;

/**
 * Outbound port for the durable record of which accounts have connected from which addresses, so the alt guard can
 * spot accounts that share an IP and survive a restart or a world rollback (never PDC): a same-IP link a reboot
 * forgot would silently hide an alt.
 *
 * <p>An address is only ever seen as a <b>hashed IP token</b>, never the raw address — the adapter hashes the
 * connecting IP before it reaches this port, so the store holds no personally identifying network data. Each
 * {@link #record} upserts the {@code (account, ipToken)} link and stamps when it was last seen; the two read
 * methods back the join-time cap and the {@code /alts} lookup.
 */
public interface IpGuardStore {

    /** Record (or refresh) that {@code account} connected from {@code ipToken}, last seen at {@code seenAt}. */
    void record(UUID account, String ipToken, Instant seenAt);

    /** The distinct accounts ever seen from {@code ipToken} (including any account currently on it). */
    Set<UUID> accountsOnIp(String ipToken);

    /**
     * Every association on any IP token {@code account} itself appears on — the account's own rows plus every
     * other account seen on a shared token. Bounded by the accounts on the account's addresses, so the grouping
     * ({@link com.uxplima.uxmessentials.security.domain.AltGroup#of}) stays a small in-memory fold.
     */
    List<IpAssociation> sharingIpWith(UUID account);
}
