package com.uxplima.uxmessentials.shared.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import org.jspecify.annotations.Nullable;

/**
 * The one durable record of which accounts have connected from which addresses. It is DB-backed and never PDC: a
 * same-address link a restart or a world rollback forgot would silently hide an alt.
 *
 * <p>Every association is stored as a keyed {@code ipToken}, and every read that answers "who else uses this
 * address" ({@link #accountsOnToken}, {@link #sharingTokenWith}) answers from tokens alone, so alt detection never
 * touches a raw address. The raw address is a separate, optional column: it is written only while the moderation
 * module is enabled, because only moderation consumes it ({@code /seenip} renders it, and a STRICT ban IP-bans
 * every address a target is known to have used). A server without moderation therefore keeps no raw addresses at
 * all, and {@link #addressesOf} comes back empty.
 *
 * <p>{@link #record} upserts the {@code (account, ipToken)} link, sliding its last-seen stamp forward and filling
 * in the raw address when one is passed. Callers hash the address into a token through {@link IpTokens} first.
 */
public interface IpHistoryStore {

    /**
     * Record (or refresh) that {@code account} connected from {@code ipToken} at {@code seenAt}, retaining
     * {@code address} alongside it when it is non-null.
     */
    void record(UUID account, String ipToken, @Nullable String address, Instant seenAt);

    /** The distinct accounts ever seen on {@code ipToken} (including any account currently on it). */
    Set<UUID> accountsOnToken(String ipToken);

    /**
     * Every association on any token {@code account} itself appears on: the account's own rows plus every other
     * account seen on a shared token. Bounded by the accounts on the account's own addresses, so the grouping
     * stays a small in-memory fold.
     */
    List<IpAssociation> sharingTokenWith(UUID account);

    /**
     * The raw addresses retained for {@code account}, empty when nothing retains them (moderation disabled, or an
     * account seen only while it was). The STRICT ban fan-out and {@code /seenip} read this; no alt lookup does.
     */
    Set<String> addressesOf(UUID account);
}
