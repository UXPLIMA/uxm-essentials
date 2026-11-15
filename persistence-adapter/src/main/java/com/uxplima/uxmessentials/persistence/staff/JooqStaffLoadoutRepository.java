package com.uxplima.uxmessentials.persistence.staff;

import static com.uxplima.uxmessentials.persistence.jooq.tables.StaffLoadout.STAFF_LOADOUT;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.StaffLoadoutRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.jooq.Condition;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link StaffLoadoutRepository} over the generated {@code STAFF_LOADOUT} table, one row per
 * {@code (player, server_id)}, since the captured pre-mode loadout is inherently per-server state (the inventory
 * to restore is the one the player had on THIS backend). The repository is bound to a single {@code serverId} at
 * construction (this backend's {@code network.server-id}) and scopes every statement to it, so two backends
 * sharing one DB cannot clobber each other's row. A {@link #save} upserts on the composite {@code (player,
 * server_id)} key: entering staff mode again (or re-entering after a row was left behind) overwrites this
 * backend's captured loadout in place rather than inserting a duplicate. A {@link #load} resolves this backend's
 * one owner row and rebuilds the {@link SavedLoadout}, returning empty when no row is held; a {@link #delete}
 * removes this backend's one owner row and is a silent no-op when none exists. The four item/effect regions are
 * stored as base64 TEXT (mirroring the vaults context); the scalars are first-class columns. The {@code
 * entered_at} column records the capture instant read from the injected {@link Clock} so the time source is
 * testable. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqStaffLoadoutRepository extends JooqRepository implements StaffLoadoutRepository {

    /**
     * The server id of a row whose backend is unknown. Each backend adopts these rows on start, and none saves
     * under it, so an adopted row is never one a running backend still holds.
     */
    public static final String UNSCOPED_SERVER_ID = "unscoped";

    private final Clock clock;
    private final String serverId;

    public JooqStaffLoadoutRepository(DSLContext dsl, Clock clock, String serverId) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must not be blank");
        }
        if (serverId.equals(UNSCOPED_SERVER_ID)) {
            // Saving under the sentinel would hand this backend's live rows to whichever backend starts next.
            throw new IllegalArgumentException("server id '" + UNSCOPED_SERVER_ID + "' is reserved");
        }
    }

    @Override
    public void save(UUID owner, SavedLoadout loadout) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(loadout, "loadout");
        long enteredAt = clock.millis();
        write(dsl -> {
            upsert(dsl, owner, loadout, enteredAt);
            return null;
        });
    }

    @Override
    public Optional<SavedLoadout> load(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(STAFF_LOADOUT)
                .where(STAFF_LOADOUT.PLAYER.eq(owner.toString()))
                .and(STAFF_LOADOUT.SERVER_ID.eq(serverId))
                .fetchOptional()
                .map(StaffLoadoutRows::toLoadout));
    }

    @Override
    public void delete(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        write(dsl -> {
            dsl.deleteFrom(STAFF_LOADOUT)
                    .where(STAFF_LOADOUT.PLAYER.eq(owner.toString()))
                    .and(STAFF_LOADOUT.SERVER_ID.eq(serverId))
                    .execute();
            return null;
        });
    }

    /**
     * Move every unscoped row to this repository's server id. A player who already holds a row under this server
     * id keeps it, and their unscoped row stays where it is and is reported, since overwriting would drop a
     * loadout captured on this backend.
     *
     * @return how many rows moved and which players' unscoped rows were left behind
     */
    public StaffLoadoutAdoption adopt() {
        return write(dsl -> {
            // Read first rather than a NOT EXISTS subquery: MySQL refuses an UPDATE whose subquery reads the
            // table being updated.
            List<String> held = dsl.select(STAFF_LOADOUT.PLAYER)
                    .from(STAFF_LOADOUT)
                    .where(STAFF_LOADOUT.SERVER_ID.eq(serverId))
                    .fetch(STAFF_LOADOUT.PLAYER);
            Condition unscoped = STAFF_LOADOUT.SERVER_ID.eq(UNSCOPED_SERVER_ID);
            List<UUID> leftBehind = held.isEmpty()
                    ? List.of()
                    : dsl
                            .select(STAFF_LOADOUT.PLAYER)
                            .from(STAFF_LOADOUT)
                            .where(unscoped)
                            .and(STAFF_LOADOUT.PLAYER.in(held))
                            .fetch(STAFF_LOADOUT.PLAYER)
                            .stream()
                            .map(UUID::fromString)
                            .toList();
            Condition movable = held.isEmpty() ? unscoped : unscoped.and(STAFF_LOADOUT.PLAYER.notIn(held));
            int moved = dsl.update(STAFF_LOADOUT)
                    .set(STAFF_LOADOUT.SERVER_ID, serverId)
                    .where(movable)
                    .execute();
            return new StaffLoadoutAdoption(moved, leftBehind);
        });
    }

    private void upsert(DSLContext dsl, UUID owner, SavedLoadout loadout, long enteredAt) {
        StaffLoadoutRecord record = dsl.newRecord(STAFF_LOADOUT);
        StaffLoadoutRows.apply(record, owner.toString(), serverId, loadout, enteredAt);
        dsl.insertInto(STAFF_LOADOUT)
                .set(record)
                .onConflict(STAFF_LOADOUT.PLAYER, STAFF_LOADOUT.SERVER_ID)
                .doUpdate()
                .set(STAFF_LOADOUT.INVENTORY, record.getInventory())
                .set(STAFF_LOADOUT.ARMOR, record.getArmor())
                .set(STAFF_LOADOUT.OFFHAND, record.getOffhand())
                .set(STAFF_LOADOUT.POTION_EFFECTS, record.getPotionEffects())
                .set(STAFF_LOADOUT.HELD_SLOT, record.getHeldSlot())
                .set(STAFF_LOADOUT.EXP_LEVEL, record.getExpLevel())
                .set(STAFF_LOADOUT.EXP_PROGRESS, record.getExpProgress())
                .set(STAFF_LOADOUT.GAME_MODE, record.getGameMode())
                .set(STAFF_LOADOUT.FLYING, record.getFlying())
                .set(STAFF_LOADOUT.ALLOW_FLIGHT, record.getAllowFlight())
                .set(STAFF_LOADOUT.VANISHED_BEFORE, record.getVanishedBefore())
                .set(STAFF_LOADOUT.ENTERED_AT, record.getEnteredAt())
                .execute();
    }
}
