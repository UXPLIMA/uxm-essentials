package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpPendingTeleports.PLAYER_WARP_PENDING_TELEPORTS;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * jOOQ-backed {@link PendingHopStore} over the generated {@code PLAYER_WARP_PENDING_TELEPORTS} table, one row per
 * player, keyed by the {@code player_uuid} primary key so a player can only ever have one hop in flight.
 * {@link #record} upserts on that key, so a fresh hop overwrites a stale one in place rather than leaving orphaned
 * rows. The uuid is canonical 36-char text, the target the warp's surrogate {@code long}, the request time
 * epoch-millis, and the charge a {@code DECIMAL(20,4)} paired with its currency id, both NULL when nothing was
 * charged. {@code accrue_on_arrival} 1 is the settlement to the warp itself; NULL is a row an earlier release wrote,
 * whose origin already paid the owner. {@link #clear} is one conditional {@code DELETE}, so the database decides
 * which of two racing callers removed the row.
 *
 * <p>The table holds player-warp hops only, so the rows every backend already reads and writes stay as they are.
 * A hop of another kind, or a settlement to anything but the hop's own warp, has no column to go in and is refused
 * at {@link #record}, which the caller turns into a refund.
 */
@NullMarked
public final class JooqPendingHopStore extends JooqRepository implements PendingHopStore {

    public JooqPendingHopStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void record(PendingHop pending) {
        Objects.requireNonNull(pending, "pending");
        if (pending.kind() != HopKind.PLAYER_WARP) {
            throw new IllegalArgumentException("no table holds a pending hop of kind " + pending.kind());
        }
        if (pending.settlement()
                .filter(to -> !to.payee().equals(pending.target()))
                .isPresent()) {
            throw new IllegalArgumentException("a player-warp hop settles only to its own warp");
        }
        String player = pending.player().toString();
        long warp = Long.parseLong(pending.target());
        long requestedAt = pending.requestedAt().toEpochMilli();
        @Nullable BigDecimal amount = pending.charge().map(HopCharge::amount).orElse(null);
        @Nullable String currency = pending.charge().map(HopCharge::currencyId).orElse(null);
        short accrueOnArrival = pending.settlement().isPresent() ? (short) 1 : (short) 0;
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_PENDING_TELEPORTS)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID, player)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, warp)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER, pending.targetServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ORIGIN_SERVER, pending.originServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT, requestedAt)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_AMOUNT, amount)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_CURRENCY, currency)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ACCRUE_ON_ARRIVAL, accrueOnArrival)
                    .onConflict(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID)
                    .doUpdate()
                    .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, warp)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER, pending.targetServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ORIGIN_SERVER, pending.originServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT, requestedAt)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_AMOUNT, amount)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_CURRENCY, currency)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ACCRUE_ON_ARRIVAL, accrueOnArrival)
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
        if (pending.kind() != HopKind.PLAYER_WARP) {
            // Nothing of another kind was ever written here, so there is nothing to claim.
            return 0;
        }
        long warp = Long.parseLong(pending.target());
        return write(dsl -> dsl.deleteFrom(PLAYER_WARP_PENDING_TELEPORTS)
                .where(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID.eq(
                        pending.player().toString()))
                .and(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID.eq(warp))
                .and(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER.eq(pending.targetServer()))
                .and(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT.eq(
                        pending.requestedAt().toEpochMilli()))
                .execute());
    }

    /** Rebuild the hop from a row; the charge is present only when both the amount and currency survived. */
    private static PendingHop toPending(PlayerWarpPendingTeleportsRecord row) {
        String warp = Long.toString(row.getWarpId());
        return new PendingHop(
                UUID.fromString(row.getPlayerUuid()),
                HopKind.PLAYER_WARP,
                warp,
                row.getTargetServer(),
                row.getOriginServer(),
                Instant.ofEpochMilli(row.getRequestedAt()),
                chargeOf(row.getPaidAmount(), row.getPaidCurrency()),
                isSet(row.getAccrueOnArrival()) ? Optional.of(new HopSettlement(warp)) : Optional.empty());
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
}
