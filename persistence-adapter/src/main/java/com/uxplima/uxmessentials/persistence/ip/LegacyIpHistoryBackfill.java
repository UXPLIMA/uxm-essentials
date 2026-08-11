package com.uxplima.uxmessentials.persistence.ip;

import static com.uxplima.uxmessentials.persistence.jooq.tables.IpHistory.IP_HISTORY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationIpHistory.MODERATION_IP_HISTORY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSeen.MODERATION_SEEN;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.runtime.Transactions;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.Result;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Folds the moderation context's own address history into the consolidated {@code ip_history} table, once.
 *
 * <p>V83 could carry the security tokens across in SQL because they were already tokens. The moderation rows are
 * raw addresses, and turning one into a token needs the server's key, which a migration has no access to, so the
 * move happens here on the first enable after the upgrade instead. Each address is tokenised, upserted as an
 * association (keeping the raw address only when moderation still retains it), and the source row is deleted, so
 * the alt history staff already had survives the move and the second raw copy does not outlive it.
 *
 * <p>Idempotent by construction: it works off the rows still in the legacy table, so a run with nothing left
 * touches nothing and a run interrupted halfway resumes from where it stopped.
 */
@NullMarked
public final class LegacyIpHistoryBackfill {

    // A bounded slice per pass, so a long-lived server with a large history never builds one huge statement batch.
    private static final int BATCH = 500;

    private LegacyIpHistoryBackfill() {}

    /**
     * Move every legacy moderation address row into {@code ip_history}, returning how many were folded in.
     * {@code retainAddress} mirrors the recorder's own retention: with moderation enabled the address is kept
     * alongside the token, otherwise only the token survives the move.
     */
    public static int run(Persistence persistence, IpTokens tokens, boolean retainAddress) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(tokens, "tokens");
        DSLContext dsl = persistence.dsl();
        int moved = 0;
        // Each batch is its own transaction: the rows it moved are committed before the next one is read, so an
        // interrupted run leaves the legacy table holding exactly what is still to do.
        int batched;
        do {
            batched = Transactions.inTransaction(dsl, tx -> moveBatch(tx, tokens, retainAddress));
            moved += batched;
        } while (batched > 0);
        return moved + Transactions.inTransaction(dsl, tx -> carryLastAddresses(tx, tokens, retainAddress));
    }

    private static int moveBatch(DSLContext dsl, IpTokens tokens, boolean retainAddress) {
        Result<Record4<String, String, Long, Long>> batch = fetch(dsl);
        for (Record4<String, String, Long, Long> row : batch) {
            String uuid = row.value1();
            String address = row.value2();
            upsert(dsl, uuid, tokens.tokenFor(address), retainAddress ? address : null, row.value3(), row.value4());
            dsl.deleteFrom(MODERATION_IP_HISTORY)
                    .where(MODERATION_IP_HISTORY.UUID.eq(uuid).and(MODERATION_IP_HISTORY.IP.eq(address)))
                    .execute();
        }
        return batch.size();
    }

    private static Result<Record4<String, String, Long, Long>> fetch(DSLContext dsl) {
        return dsl.select(
                        MODERATION_IP_HISTORY.UUID,
                        MODERATION_IP_HISTORY.IP,
                        MODERATION_IP_HISTORY.FIRST_SEEN,
                        MODERATION_IP_HISTORY.LAST_SEEN)
                .from(MODERATION_IP_HISTORY)
                .limit(BATCH)
                .fetch();
    }

    /**
     * The last-seen address of a player who predates the legacy history table (or whose history rows were already
     * folded in) is an association too, and {@code /alts} used to read it, so it comes across as well. The seen row
     * itself stays where it is: it is what {@code /seen} and {@code /seenip} render.
     */
    private static int carryLastAddresses(DSLContext dsl, IpTokens tokens, boolean retainAddress) {
        int moved = 0;
        Result<org.jooq.Record3<String, String, Long>> rows = dsl.select(
                        MODERATION_SEEN.UUID, MODERATION_SEEN.LAST_IP, MODERATION_SEEN.LAST_SEEN)
                .from(MODERATION_SEEN)
                .where(MODERATION_SEEN.LAST_IP.isNotNull())
                .fetch();
        for (org.jooq.Record3<String, String, Long> row : rows) {
            String address = row.value2();
            if (address == null) {
                continue;
            }
            String token = tokens.tokenFor(address);
            if (dsl.fetchExists(IP_HISTORY, IP_HISTORY.UUID.eq(row.value1()).and(IP_HISTORY.IP_TOKEN.eq(token)))) {
                continue;
            }
            long seen = row.value3();
            upsert(dsl, row.value1(), token, retainAddress ? address : null, seen, seen);
            moved++;
        }
        return moved;
    }

    private static void upsert(
            DSLContext dsl, String uuid, String token, @Nullable String address, long firstSeen, long lastSeen) {
        dsl.insertInto(IP_HISTORY)
                .set(IP_HISTORY.UUID, uuid)
                .set(IP_HISTORY.IP_TOKEN, token)
                .set(IP_HISTORY.IP, address)
                .set(IP_HISTORY.FIRST_SEEN, firstSeen)
                .set(IP_HISTORY.LAST_SEEN, lastSeen)
                .onConflict(IP_HISTORY.UUID, IP_HISTORY.IP_TOKEN)
                .doUpdate()
                .set(IP_HISTORY.LAST_SEEN, lastSeen)
                .execute();
    }
}
