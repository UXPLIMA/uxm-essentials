package com.uxplima.uxmessentials.persistence.staff;

import static com.uxplima.uxmessentials.persistence.jooq.tables.StaffLoadout.STAFF_LOADOUT;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.StaffLoadoutRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link StaffLoadoutRepository} over the generated {@code STAFF_LOADOUT} table — a single row
 * per owner, keyed by the player uuid. A {@link #save} upserts on the {@code player} key: entering staff mode
 * again (or re-entering after a row was left behind) overwrites the captured loadout in place rather than
 * inserting a duplicate. A {@link #load} resolves the one owner row and rebuilds the {@link SavedLoadout},
 * returning empty when no row is held; a {@link #delete} removes the one owner row and is a silent no-op when
 * none exists. The four item/effect regions are stored as base64 TEXT (mirroring the vaults context); the
 * scalars are first-class columns. The {@code entered_at} column records the capture instant read from the
 * injected {@link Clock} so the time source is testable. Every statement is typed jOOQ DSL; no SQL is ever
 * string-concatenated.
 */
public final class JooqStaffLoadoutRepository extends JooqRepository implements StaffLoadoutRepository {

    private final Clock clock;

    public JooqStaffLoadoutRepository(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
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
                .fetchOptional()
                .map(StaffLoadoutRows::toLoadout));
    }

    @Override
    public void delete(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        write(dsl -> {
            dsl.deleteFrom(STAFF_LOADOUT)
                    .where(STAFF_LOADOUT.PLAYER.eq(owner.toString()))
                    .execute();
            return null;
        });
    }

    private static void upsert(DSLContext dsl, UUID owner, SavedLoadout loadout, long enteredAt) {
        StaffLoadoutRecord record = dsl.newRecord(STAFF_LOADOUT);
        StaffLoadoutRows.apply(record, owner.toString(), loadout, enteredAt);
        dsl.insertInto(STAFF_LOADOUT)
                .set(record)
                .onConflict(STAFF_LOADOUT.PLAYER)
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
                .set(STAFF_LOADOUT.VANISHED_BEFORE, record.getVanishedBefore())
                .set(STAFF_LOADOUT.ENTERED_AT, record.getEnteredAt())
                .execute();
    }
}
