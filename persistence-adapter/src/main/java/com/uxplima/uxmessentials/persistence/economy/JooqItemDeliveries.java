package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyItemDeliveries.ECONOMY_ITEM_DELIVERIES;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.ItemDeliveryQueue;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.EconomyItemDeliveriesRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link ItemDeliveryQueue} over {@code economy_item_deliveries}, the deliveries queued for balances that
 * change only while their player is online.
 *
 * <p>A row is claimed with a conditional update only one delivery can win, and it is removed or shrunk only under that
 * claim after the balance changed, so two servers delivering at once, or a quick rejoin, hand it over once on any
 * backend's isolation level, and a stop between the claim and the hand-over loses nothing.
 *
 * <p>A claim older than {@link #STALE_AFTER} can be taken again. If a hand-over really took that long the row could be
 * handed over twice; if a claim never went stale, a stop in that gap would strand it for good. A hand-over is a hop to
 * the player's thread and one change, well under a second, and a claimant still waiting to record one refreshes its
 * claim, so minutes make the first practically impossible while still bringing a stranded row back.
 *
 * <p>A give is added to an unclaimed give of the same owner, currency and reason when there is one, and a held-back
 * rest to one the same way, so a player away for many sweeps has one waiting give per currency, not one per sweep. The
 * add is a conditional update on an unclaimed row: a claim that won first makes it insert instead, and a claim that
 * comes after it reads the added amount back.
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
        Condition shown = withHeldBack ? DSL.trueCondition() : ECONOMY_ITEM_DELIVERIES.HELD_BACK.eq(0);
        return read(dsl -> dsl.selectFrom(ECONOMY_ITEM_DELIVERIES)
                .where(ECONOMY_ITEM_DELIVERIES.OWNER.eq(owner.toString()))
                .and(open())
                .and(shown)
                .orderBy(ECONOMY_ITEM_DELIVERIES.CREATED_AT, ECONOMY_ITEM_DELIVERIES.ID)
                .fetch(JooqItemDeliveries::delivery));
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
    public Set<String> resetsWaiting(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        // Claimed ones count too: a reset being applied right now still decides what the balance becomes.
        List<String> waiting = read(dsl -> dsl.selectDistinct(ECONOMY_ITEM_DELIVERIES.CURRENCY)
                .from(ECONOMY_ITEM_DELIVERIES)
                .where(ECONOMY_ITEM_DELIVERIES.OWNER.eq(owner.toString()))
                .and(ECONOMY_ITEM_DELIVERIES.KIND.eq(Kind.RESET.name()))
                .fetch(ECONOMY_ITEM_DELIVERIES.CURRENCY));
        Set<String> currencies = new HashSet<>();
        for (String currency : waiting) {
            currencies.add(currency.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(currencies);
    }

    @Override
    public void queueGive(String sweepId, List<UUID> owners, String currency, BigDecimal amount, EconomyReason reason) {
        Objects.requireNonNull(sweepId, "sweepId");
        Objects.requireNonNull(owners, "owners");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        List<String> ids = new ArrayList<>(owners.size());
        for (UUID owner : owners) {
            ids.add(owner.toString());
        }
        write(dsl -> {
            queue(dsl, clock.millis(), sweepId, ids, currency, Kind.GIVE, amount, reason);
            return null;
        });
    }

    @Override
    public Optional<ItemDelivery> claim(String id, String claimant) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimant, "claimant");
        long now = clock.millis();
        return write(dsl -> {
            int won = dsl.update(ECONOMY_ITEM_DELIVERIES)
                    .set(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY, claimant)
                    .set(ECONOMY_ITEM_DELIVERIES.CLAIMED_AT, now)
                    .where(ECONOMY_ITEM_DELIVERIES.ID.eq(id))
                    .and(open())
                    .execute();
            if (won != 1) {
                return Optional.empty();
            }
            return dsl.selectFrom(ECONOMY_ITEM_DELIVERIES)
                    .where(ECONOMY_ITEM_DELIVERIES.ID.eq(id))
                    .and(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY.eq(claimant))
                    .fetchOptional(JooqItemDeliveries::delivery);
        });
    }

    @Override
    public boolean touch(String id, String claimant) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimant, "claimant");
        long now = clock.millis();
        return write(dsl -> dsl.update(ECONOMY_ITEM_DELIVERIES)
                        .set(ECONOMY_ITEM_DELIVERIES.CLAIMED_AT, now)
                        .where(ECONOMY_ITEM_DELIVERIES.ID.eq(id))
                        .and(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY.eq(claimant))
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
        return write(dsl -> {
            EconomyItemDeliveriesRecord row =
                    dsl.selectFrom(ECONOMY_ITEM_DELIVERIES).where(held).fetchOne();
            if (row == null) {
                return false;
            }
            Optional<String> into = mergeTarget(dsl, row.getOwner(), row.getCurrency(), row.getReason(), id);
            if (into.isPresent() && add(dsl, into.get(), rest, false)) {
                return dsl.deleteFrom(ECONOMY_ITEM_DELIVERIES).where(held).execute() == 1;
            }
            return dsl.update(ECONOMY_ITEM_DELIVERIES)
                            .set(ECONOMY_ITEM_DELIVERIES.KIND, Kind.GIVE.name())
                            .set(ECONOMY_ITEM_DELIVERIES.AMOUNT, rest)
                            .set(ECONOMY_ITEM_DELIVERIES.HELD_BACK, 1)
                            .setNull(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY)
                            .setNull(ECONOMY_ITEM_DELIVERIES.CLAIMED_AT)
                            .where(held)
                            .execute()
                    == 1;
        });
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

    /**
     * Queue a delivery of {@code amount} for each of {@code owners} inside {@code tx}. A give is added to the owner's
     * unclaimed give of the same currency and reason when there is one; everything else becomes a row of its own.
     */
    static void queue(
            DSLContext tx,
            long now,
            String sweepId,
            List<String> owners,
            String currency,
            Kind kind,
            BigDecimal amount,
            EconomyReason reason) {
        if (owners.isEmpty()) {
            return;
        }
        List<String> fresh = new ArrayList<>(owners.size());
        if (kind == Kind.GIVE) {
            Map<String, String> targets = new HashMap<>();
            tx.select(ECONOMY_ITEM_DELIVERIES.OWNER, ECONOMY_ITEM_DELIVERIES.ID)
                    .from(ECONOMY_ITEM_DELIVERIES)
                    .where(ECONOMY_ITEM_DELIVERIES.OWNER.in(owners))
                    .and(mergeable(currency, reason.name()))
                    .orderBy(ECONOMY_ITEM_DELIVERIES.ID)
                    .forEach(row -> targets.putIfAbsent(row.value1(), row.value2()));
            for (String owner : owners) {
                String target = targets.get(owner);
                if (target == null || !add(tx, target, amount, true)) {
                    fresh.add(owner);
                }
            }
        } else {
            fresh.addAll(owners);
        }
        if (fresh.isEmpty()) {
            return;
        }
        var insert = tx.insertInto(
                ECONOMY_ITEM_DELIVERIES,
                ECONOMY_ITEM_DELIVERIES.ID,
                ECONOMY_ITEM_DELIVERIES.SWEEP_ID,
                ECONOMY_ITEM_DELIVERIES.OWNER,
                ECONOMY_ITEM_DELIVERIES.CURRENCY,
                ECONOMY_ITEM_DELIVERIES.KIND,
                ECONOMY_ITEM_DELIVERIES.AMOUNT,
                ECONOMY_ITEM_DELIVERIES.REASON,
                ECONOMY_ITEM_DELIVERIES.CREATED_AT,
                ECONOMY_ITEM_DELIVERIES.HELD_BACK);
        for (String owner : fresh) {
            insert = insert.values(
                    UUID.randomUUID().toString(), sweepId, owner, currency, kind.name(), amount, reason.name(), now, 0);
        }
        insert.execute();
    }

    private static Optional<String> mergeTarget(
            DSLContext tx, String owner, String currency, String reason, String except) {
        return tx.select(ECONOMY_ITEM_DELIVERIES.ID)
                .from(ECONOMY_ITEM_DELIVERIES)
                .where(ECONOMY_ITEM_DELIVERIES.OWNER.eq(owner))
                .and(ECONOMY_ITEM_DELIVERIES.ID.ne(except))
                .and(mergeable(currency, reason))
                .orderBy(ECONOMY_ITEM_DELIVERIES.ID)
                .limit(1)
                .fetchOptional(ECONOMY_ITEM_DELIVERIES.ID);
    }

    /** An unclaimed give a new give or a held-back rest can be added to. */
    private static Condition mergeable(String currency, String reason) {
        return ECONOMY_ITEM_DELIVERIES
                .CURRENCY
                .eq(currency)
                .and(ECONOMY_ITEM_DELIVERIES.KIND.eq(Kind.GIVE.name()))
                .and(ECONOMY_ITEM_DELIVERIES.REASON.eq(reason))
                .and(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY.isNull());
    }

    /**
     * Add {@code amount} to the row if it is still unclaimed. A new give makes the row due on the next pass again; a
     * held-back rest leaves it as it was.
     */
    private static boolean add(DSLContext tx, String id, BigDecimal amount, boolean due) {
        var update = tx.update(ECONOMY_ITEM_DELIVERIES)
                .set(ECONOMY_ITEM_DELIVERIES.AMOUNT, ECONOMY_ITEM_DELIVERIES.AMOUNT.plus(amount));
        var withDue = due ? update.set(ECONOMY_ITEM_DELIVERIES.HELD_BACK, 0) : update;
        return withDue.where(ECONOMY_ITEM_DELIVERIES.ID.eq(id))
                        .and(ECONOMY_ITEM_DELIVERIES.CLAIMED_BY.isNull())
                        .execute()
                == 1;
    }

    private static ItemDelivery delivery(EconomyItemDeliveriesRecord row) {
        return new ItemDelivery(
                row.getId(),
                UUID.fromString(row.getOwner()),
                row.getCurrency(),
                Kind.valueOf(row.getKind()),
                row.getAmount(),
                reason(row.getReason()));
    }

    private static EconomyReason reason(String stored) {
        try {
            return EconomyReason.valueOf(stored);
        } catch (IllegalArgumentException unknown) {
            // A reason a newer version wrote: the hand-over still happens, its ledger row says unspecified.
            return EconomyReason.UNSPECIFIED;
        }
    }

    /** Unclaimed, or claimed so long ago that the claim counts as abandoned. */
    private Condition open() {
        long staleBefore = clock.millis() - staleAfter.toMillis();
        return ECONOMY_ITEM_DELIVERIES.CLAIMED_BY.isNull().or(ECONOMY_ITEM_DELIVERIES.CLAIMED_AT.lt(staleBefore));
    }
}
