package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyItemDeliveries.ECONOMY_ITEM_DELIVERIES;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.ItemDeliveryQueue;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link ItemDeliveryQueue} over {@code economy_item_deliveries}, the rows a sweep over an item currency
 * queued.
 *
 * <p>A row is claimed with a conditional update only one delivery can win, and it is removed or shrunk only under that
 * claim after the items are in an inventory, so two servers delivering at once, or a quick rejoin, hand it over once
 * on any backend's isolation level, and a stop between the claim and the hand-over loses nothing.
 *
 * <p>A claim older than {@link #STALE_AFTER} can be taken again. If a hand-over really took that long the row could be
 * handed over twice; if a claim never went stale, a stop in that gap would strand the items for good. A hand-over is
 * a hop to the player's thread and one inventory change, well under a second, so minutes make the first practically
 * impossible while still bringing a stranded row back.
 */
@NullMarked
public final class JooqItemDeliveries extends JooqRepository implements ItemDeliveryQueue {

    /** How long a claim holds before it counts as abandoned. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final Clock clock;
    private final Duration staleAfter;

    public JooqItemDeliveries(DSLContext dsl, Clock clock) {
        this(dsl, clock, STALE_AFTER);
    }

    JooqItemDeliveries(DSLContext dsl, Clock clock, Duration staleAfter) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
    }

    @Override
    public List<ItemDelivery> claimable(UUID owner, boolean withHeldBack) {
        Objects.requireNonNull(owner, "owner");
        Condition shown = withHeldBack ? org.jooq.impl.DSL.trueCondition() : ECONOMY_ITEM_DELIVERIES.HELD_BACK.eq(0);
        return read(dsl -> dsl.selectFrom(ECONOMY_ITEM_DELIVERIES)
                .where(ECONOMY_ITEM_DELIVERIES.OWNER.eq(owner.toString()))
                .and(open())
                .and(shown)
                .orderBy(ECONOMY_ITEM_DELIVERIES.CREATED_AT, ECONOMY_ITEM_DELIVERIES.ID)
                .fetch(row -> new ItemDelivery(
                        row.getId(),
                        UUID.fromString(row.getOwner()),
                        row.getCurrency(),
                        Kind.valueOf(row.getKind()),
                        row.getAmount())));
    }

    @Override
    public List<UUID> waiting(List<UUID> owners) {
        Objects.requireNonNull(owners, "owners");
        if (owners.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(owners.size());
        for (UUID owner : owners) {
            ids.add(owner.toString());
        }
        return read(dsl -> dsl.selectDistinct(ECONOMY_ITEM_DELIVERIES.OWNER)
                        .from(ECONOMY_ITEM_DELIVERIES)
                        .where(ECONOMY_ITEM_DELIVERIES.OWNER.in(ids))
                        .and(ECONOMY_ITEM_DELIVERIES.HELD_BACK.eq(0))
                        .and(open())
                        .fetch(ECONOMY_ITEM_DELIVERIES.OWNER))
                .stream()
                .map(UUID::fromString)
                .toList();
    }

    @Override
    public boolean claim(String id, String claimant) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimant, "claimant");
        long now = clock.millis();
        return write(dsl -> dsl.update(ECONOMY_ITEM_DELIVERIES)
                        .set(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY, claimant)
                        .set(ECONOMY_ITEM_DELIVERIES.CLAIMED_AT, now)
                        .where(ECONOMY_ITEM_DELIVERIES.ID.eq(id))
                        .and(open())
                        .execute()
                == 1);
    }

    @Override
    public boolean finish(String id, String claimant, BigDecimal rest) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimant, "claimant");
        Objects.requireNonNull(rest, "rest");
        Condition held = ECONOMY_ITEM_DELIVERIES.ID.eq(id).and(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY.eq(claimant));
        if (rest.signum() <= 0) {
            return write(
                    dsl -> dsl.deleteFrom(ECONOMY_ITEM_DELIVERIES).where(held).execute() == 1);
        }
        return write(dsl -> dsl.update(ECONOMY_ITEM_DELIVERIES)
                        .set(ECONOMY_ITEM_DELIVERIES.KIND, Kind.GIVE.name())
                        .set(ECONOMY_ITEM_DELIVERIES.AMOUNT, rest)
                        .set(ECONOMY_ITEM_DELIVERIES.HELD_BACK, 1)
                        .setNull(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY)
                        .setNull(ECONOMY_ITEM_DELIVERIES.CLAIMED_AT)
                        .where(held)
                        .execute()
                == 1);
    }

    @Override
    public void release(String id, String claimant) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimant, "claimant");
        write(dsl -> dsl.update(ECONOMY_ITEM_DELIVERIES)
                .setNull(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY)
                .setNull(ECONOMY_ITEM_DELIVERIES.CLAIMED_AT)
                .where(ECONOMY_ITEM_DELIVERIES.ID.eq(id))
                .and(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY.eq(claimant))
                .execute());
    }

    /** Unclaimed, or claimed so long ago that the claim counts as abandoned. */
    private Condition open() {
        long staleBefore = clock.millis() - staleAfter.toMillis();
        return ECONOMY_ITEM_DELIVERIES.CLAIMED_BY.isNull().or(ECONOMY_ITEM_DELIVERIES.CLAIMED_AT.lt(staleBefore));
    }
}
