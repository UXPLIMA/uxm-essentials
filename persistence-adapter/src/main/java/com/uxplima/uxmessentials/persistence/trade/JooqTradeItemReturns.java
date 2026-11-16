package com.uxplima.uxmessentials.persistence.trade;

import static com.uxplima.uxmessentials.persistence.jooq.tables.TradeItemReturns.TRADE_ITEM_RETURNS;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.trade.application.port.TradeItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link TradeItemReturns} over the {@code trade_item_returns} table. The items are stored as base64 of
 * their serialized bytes, the form the vault tables use.
 *
 * <p>A batch is claimed with a conditional update that only one delivery can win, and it is removed or shrunk only
 * under that claim after the items are in an inventory, so two servers delivering at once, or a quick rejoin, hand
 * it out once on any backend's isolation level, and a crash between the claim and the hand-over loses nothing.
 *
 * <p>A claim older than {@code staleAfter} can be taken again. That is the one trade-off: if a hand-over really takes
 * longer than the stale window, the batch could be handed out twice; if a claim never went stale, a crash in that gap
 * would strand the items for good. A hand-over is a hop to the player's thread and one inventory add, which finishes
 * in well under a second, so a window of minutes makes the duplicate practically impossible while still bringing a
 * stranded batch back.
 */
@NullMarked
public final class JooqTradeItemReturns extends JooqRepository implements TradeItemReturns {

    /** How long a claim holds before it counts as abandoned. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final Clock clock;
    private final Duration staleAfter;
    private final Logger log;

    public JooqTradeItemReturns(DSLContext dsl, Clock clock, Logger log) {
        this(dsl, clock, STALE_AFTER, log);
    }

    JooqTradeItemReturns(DSLContext dsl, Clock clock, Duration staleAfter, Logger log) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void keep(UUID owner, VaultContents items) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return;
        }
        String encoded = encode(items);
        long now = clock.millis();
        write(dsl -> dsl.insertInto(TRADE_ITEM_RETURNS)
                .set(TRADE_ITEM_RETURNS.ID, UUID.randomUUID().toString())
                .set(TRADE_ITEM_RETURNS.OWNER, owner.toString())
                .set(TRADE_ITEM_RETURNS.CONTENTS, encoded)
                .set(TRADE_ITEM_RETURNS.CREATED_AT, now)
                .execute());
    }

    @Override
    public List<Kept> claimable(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        List<Record2<String, String>> rows = read(dsl -> dsl.select(TRADE_ITEM_RETURNS.ID, TRADE_ITEM_RETURNS.CONTENTS)
                .from(TRADE_ITEM_RETURNS)
                .where(TRADE_ITEM_RETURNS.OWNER.eq(owner.toString()))
                .and(open())
                .orderBy(TRADE_ITEM_RETURNS.CREATED_AT, TRADE_ITEM_RETURNS.ID)
                .fetch());
        List<Kept> kept = new ArrayList<>(rows.size());
        for (Record2<String, String> row : rows) {
            try {
                kept.add(new Kept(
                        row.value1(), VaultContents.of(Base64.getDecoder().decode(row.value2()))));
            } catch (IllegalArgumentException unreadable) {
                // One bad row must not hold back the rest; it stays in the table for an operator to look at.
                log.error("event=trade_return_row_unreadable player=" + owner + " id=" + row.value1(), unreadable);
            }
        }
        return kept;
    }

    @Override
    public boolean claim(String id, String claimant) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimant, "claimant");
        long now = clock.millis();
        return write(dsl -> dsl.update(TRADE_ITEM_RETURNS)
                        .set(TRADE_ITEM_RETURNS.CLAIMED_BY, claimant)
                        .set(TRADE_ITEM_RETURNS.CLAIMED_AT, now)
                        .where(TRADE_ITEM_RETURNS.ID.eq(id))
                        .and(open())
                        .execute()
                == 1);
    }

    @Override
    public boolean finish(String id, String claimant, VaultContents rest) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimant, "claimant");
        Objects.requireNonNull(rest, "rest");
        Condition held = TRADE_ITEM_RETURNS.ID.eq(id).and(TRADE_ITEM_RETURNS.CLAIMED_BY.eq(claimant));
        if (rest.isEmpty()) {
            return write(dsl -> dsl.deleteFrom(TRADE_ITEM_RETURNS).where(held).execute() == 1);
        }
        String encoded = encode(rest);
        return write(dsl -> dsl.update(TRADE_ITEM_RETURNS)
                        .set(TRADE_ITEM_RETURNS.CONTENTS, encoded)
                        .setNull(TRADE_ITEM_RETURNS.CLAIMED_BY)
                        .setNull(TRADE_ITEM_RETURNS.CLAIMED_AT)
                        .where(held)
                        .execute()
                == 1);
    }

    @Override
    public void release(String id, String claimant) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimant, "claimant");
        write(dsl -> dsl.update(TRADE_ITEM_RETURNS)
                .setNull(TRADE_ITEM_RETURNS.CLAIMED_BY)
                .setNull(TRADE_ITEM_RETURNS.CLAIMED_AT)
                .where(TRADE_ITEM_RETURNS.ID.eq(id))
                .and(TRADE_ITEM_RETURNS.CLAIMED_BY.eq(claimant))
                .execute());
    }

    /** Unclaimed, or claimed so long ago that the claim counts as abandoned. */
    private Condition open() {
        long staleBefore = clock.millis() - staleAfter.toMillis();
        return TRADE_ITEM_RETURNS.CLAIMED_BY.isNull().or(TRADE_ITEM_RETURNS.CLAIMED_AT.lt(staleBefore));
    }

    private static String encode(VaultContents items) {
        return Base64.getEncoder().encodeToString(items.payload().orElseThrow());
    }
}
