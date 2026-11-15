package com.uxplima.uxmessentials.persistence.hop;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpPendingTeleports.PLAYER_WARP_PENDING_TELEPORTS;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpPendingTeleportsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * jOOQ-backed {@link PendingHopStore} over the network-shared {@code player_warp_pending_teleports} table, one row
 * per player on the {@code player_uuid} primary key, so a player only ever has one hop in flight whatever its kind.
 * {@link #record} upserts on that key and {@link #clear} is one conditional {@code DELETE}, so the database decides
 * which of two racing callers removed the row.
 *
 * <p>The table began as the player-warp hop's, and backends of the previous release still read and write it during
 * a rolling upgrade, so a player-warp hop keeps that exact shape: {@code kind} NULL, the warp's surrogate id in
 * {@code warp_id}, and {@code accrue_on_arrival} 1 for the settlement to the warp itself (NULL is a row an older
 * origin wrote, which already paid the owner). Every other kind names itself in {@code kind}, keeps its target in
 * {@code target_ref} and any payee in {@code settle_to}, and writes {@code warp_id} 0, an id no player warp has, so
 * an older backend fails to read the row instead of taking it for a player warp. The warp id decides on the way
 * back too: an older backend's upsert rewrites only its own columns, so a row with a positive warp id is a player
 * warp whatever stale kind it carries, and a kind name this release does not know reads as
 * {@link HopKind#UNKNOWN}, which no resolver serves. The charge is a
 * {@code DECIMAL(20,4)} paired with its currency id for every kind, both NULL when nothing was charged.
 */
@NullMarked
public final class JooqPendingHopStore extends JooqRepository implements PendingHopStore {

    private static final long NO_PLAYER_WARP = 0L;

    public JooqPendingHopStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void record(PendingHop pending) {
        Objects.requireNonNull(pending, "pending");
        Row row = Row.of(pending);
        String player = pending.player().toString();
        long requestedAt = pending.requestedAt().toEpochMilli();
        @Nullable BigDecimal amount = pending.charge().map(HopCharge::amount).orElse(null);
        @Nullable String currency = pending.charge().map(HopCharge::currencyId).orElse(null);
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_PENDING_TELEPORTS)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID, player)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.KIND, row.kind())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, row.warpId())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_REF, row.targetRef())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.SETTLE_TO, row.settleTo())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER, pending.targetServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ORIGIN_SERVER, pending.originServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT, requestedAt)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_AMOUNT, amount)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_CURRENCY, currency)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ACCRUE_ON_ARRIVAL, row.accrueOnArrival())
                    .onConflict(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID)
                    .doUpdate()
                    .set(PLAYER_WARP_PENDING_TELEPORTS.KIND, row.kind())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, row.warpId())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_REF, row.targetRef())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.SETTLE_TO, row.settleTo())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER, pending.targetServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ORIGIN_SERVER, pending.originServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT, requestedAt)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_AMOUNT, amount)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_CURRENCY, currency)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ACCRUE_ON_ARRIVAL, row.accrueOnArrival())
                    .execute();
            return null;
        });
    }

    @Override
    public Optional<PendingHop> find(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(PLAYER_WARP_PENDING_TELEPORTS)
                .where(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID.eq(player.toString()))
                .fetchOptional()
                .map(JooqPendingHopStore::toPending));
    }

    @Override
    public int clear(PendingHop pending) {
        Objects.requireNonNull(pending, "pending");
        Condition sameTarget = pending.kind() == HopKind.PLAYER_WARP
                ? PLAYER_WARP_PENDING_TELEPORTS.WARP_ID.eq(warpIdOf(pending.target()))
                : PLAYER_WARP_PENDING_TELEPORTS
                        .WARP_ID
                        .eq(NO_PLAYER_WARP)
                        .and(kindIs(pending.kind()))
                        .and(targetIs(pending.target()));
        return write(dsl -> dsl.deleteFrom(PLAYER_WARP_PENDING_TELEPORTS)
                .where(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID.eq(
                        pending.player().toString()))
                .and(sameTarget)
                .and(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER.eq(pending.targetServer()))
                .and(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT.eq(
                        pending.requestedAt().toEpochMilli()))
                .execute());
    }

    /** The stored kind of a row this backend reads as {@code kind}; an unknown one is any name it does not know. */
    private static Condition kindIs(HopKind kind) {
        if (kind != HopKind.UNKNOWN) {
            return PLAYER_WARP_PENDING_TELEPORTS.KIND.eq(kind.name());
        }
        List<String> known = Arrays.stream(HopKind.values())
                .filter(each -> each != HopKind.UNKNOWN)
                .map(HopKind::name)
                .toList();
        // A NULL kind with no player warp is no row this release writes, so it reads as unknown too.
        return PLAYER_WARP_PENDING_TELEPORTS.KIND.notIn(known).or(PLAYER_WARP_PENDING_TELEPORTS.KIND.isNull());
    }

    /** The stored target of a row read back as {@code target}; an empty one is how a missing target_ref reads. */
    private static Condition targetIs(String target) {
        if (target.isEmpty()) {
            return PLAYER_WARP_PENDING_TELEPORTS
                    .TARGET_REF
                    .isNull()
                    .or(PLAYER_WARP_PENDING_TELEPORTS.TARGET_REF.eq(target));
        }
        return PLAYER_WARP_PENDING_TELEPORTS.TARGET_REF.eq(target);
    }

    /** Rebuild the hop from a row; the charge is present only when both the amount and currency survived. */
    private static PendingHop toPending(PlayerWarpPendingTeleportsRecord row) {
        String target;
        Optional<HopSettlement> settlement;
        HopKind kind;
        // A positive warp id is a player-warp hop whatever the kind column says: an older backend's upsert rewrites
        // warp_id and leaves kind, target_ref and settle_to as an earlier row of another kind left them.
        if (row.getWarpId() > NO_PLAYER_WARP) {
            kind = HopKind.PLAYER_WARP;
            target = Long.toString(row.getWarpId());
            settlement = isSet(row.getAccrueOnArrival()) ? Optional.of(new HopSettlement(target)) : Optional.empty();
        } else {
            kind = kindOf(row.getKind());
            target = Objects.requireNonNullElse(row.getTargetRef(), "");
            settlement = Optional.ofNullable(row.getSettleTo()).map(HopSettlement::new);
        }
        return new PendingHop(
                UUID.fromString(row.getPlayerUuid()),
                kind,
                target,
                row.getTargetServer(),
                row.getOriginServer(),
                Instant.ofEpochMilli(row.getRequestedAt()),
                chargeOf(row.getPaidAmount(), row.getPaidCurrency()),
                settlement);
    }

    /** A kind this release knows, or the unknown sentinel for one a newer release added. */
    private static HopKind kindOf(@Nullable String stored) {
        if (stored == null) {
            return HopKind.UNKNOWN;
        }
        for (HopKind kind : HopKind.values()) {
            if (kind != HopKind.UNKNOWN && kind.name().equals(stored)) {
                return kind;
            }
        }
        return HopKind.UNKNOWN;
    }

    /** NULL is a row an earlier origin wrote, which already paid the owner, so only a stored 1 sets the flag. */
    private static boolean isSet(@Nullable Short flag) {
        return flag != null && flag != 0;
    }

    /** A charge is only present when both columns are non-NULL; a half-written pair reads as no charge. */
    private static Optional<HopCharge> chargeOf(@Nullable BigDecimal amount, @Nullable String currency) {
        if (amount == null || currency == null) {
            return Optional.empty();
        }
        return Optional.of(new HopCharge(amount, currency));
    }

    private static long warpIdOf(String target) {
        long id = Long.parseLong(target);
        if (id <= 0) {
            throw new IllegalArgumentException("a player-warp hop needs a positive warp id: " + target);
        }
        return id;
    }

    /** How one hop is laid out in the columns, by kind. */
    private record Row(
            @Nullable String kind,
            long warpId,
            @Nullable String targetRef,
            @Nullable String settleTo,
            short accrueOnArrival) {

        static Row of(PendingHop pending) {
            if (pending.kind() == HopKind.UNKNOWN) {
                throw new IllegalArgumentException("a hop of an unknown kind is only ever read, never recorded");
            }
            Optional<String> payee = pending.settlement().map(HopSettlement::payee);
            if (pending.kind() == HopKind.PLAYER_WARP) {
                if (payee.filter(to -> !to.equals(pending.target())).isPresent()) {
                    // accrue_on_arrival can only say "pay this warp", the one column older backends understand.
                    throw new IllegalArgumentException("a player-warp hop settles only to its own warp");
                }
                return new Row(null, warpIdOf(pending.target()), null, null, payee.isPresent() ? (short) 1 : 0);
            }
            return new Row(pending.kind().name(), NO_PLAYER_WARP, pending.target(), payee.orElse(null), (short) 0);
        }
    }
}
