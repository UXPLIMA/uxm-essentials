package com.uxplima.uxmessentials.persistence.ledger;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerDailyRewards.PLAYER_DAILY_REWARDS;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.DailyRewardLedger;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ {@link DailyRewardLedger} over {@code player_daily_rewards}.
 *
 * <p>Taking the reward is an update conditioned on the stored stamp being old enough, then, for a player with no row
 * yet, an insert that does nothing on conflict. Each statement reports whether it changed a row, and a row is changed
 * by one writer only: a second server's update finds the fresh stamp and matches nothing, and its insert meets the
 * primary key.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. Every method borrows a pooled connection and must run off the tick thread.
 */
@NullMarked
public final class JooqDailyRewardLedger extends JooqRepository implements DailyRewardLedger {

    public JooqDailyRewardLedger(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public boolean claimIfDue(UUID player, long now, long windowMillis) {
        Objects.requireNonNull(player, "player");
        String id = player.toString();
        return write(dsl -> {
            int updated = dsl.update(PLAYER_DAILY_REWARDS)
                    .set(PLAYER_DAILY_REWARDS.CLAIMED_AT, now)
                    .where(PLAYER_DAILY_REWARDS.PLAYER.eq(id))
                    .and(PLAYER_DAILY_REWARDS.CLAIMED_AT.le(now - windowMillis))
                    .execute();
            if (updated == 1) {
                return true;
            }
            return dsl.insertInto(PLAYER_DAILY_REWARDS)
                            .set(PLAYER_DAILY_REWARDS.PLAYER, id)
                            .set(PLAYER_DAILY_REWARDS.CLAIMED_AT, now)
                            .onConflictDoNothing()
                            .execute()
                    == 1;
        });
    }

    @Override
    public void keepLatest(UUID player, long claimedAt) {
        Objects.requireNonNull(player, "player");
        String id = player.toString();
        write(dsl -> {
            int updated = dsl.update(PLAYER_DAILY_REWARDS)
                    .set(PLAYER_DAILY_REWARDS.CLAIMED_AT, claimedAt)
                    .where(PLAYER_DAILY_REWARDS.PLAYER.eq(id))
                    .and(PLAYER_DAILY_REWARDS.CLAIMED_AT.lt(claimedAt))
                    .execute();
            if (updated == 0) {
                dsl.insertInto(PLAYER_DAILY_REWARDS)
                        .set(PLAYER_DAILY_REWARDS.PLAYER, id)
                        .set(PLAYER_DAILY_REWARDS.CLAIMED_AT, claimedAt)
                        .onConflictDoNothing()
                        .execute();
            }
            return null;
        });
    }
}
