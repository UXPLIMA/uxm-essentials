package com.uxplima.uxmessentials.persistence.npc;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.NpcRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link NpcRepository} over the generated {@code NPC} table. NPCs are server-wide and keyed by
 * name alone, so a lookup is a single-row {@code SELECT} on the name primary key, the list reads every row in
 * stored creation order, and a {@code save} upserts the row on the name key (a move, re-skin, or command rebind
 * overwrites in place). Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqNpcRepository extends JooqRepository implements NpcRepository {

    private static final com.uxplima.uxmessentials.persistence.jooq.tables.Npc NPC =
            com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;

    public JooqNpcRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Npc> find(NpcName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(NPC)
                .where(NPC.NAME.eq(name.value()))
                .fetchOptional()
                .map(NpcRows::toNpc));
    }

    @Override
    public List<Npc> all() {
        return read(dsl -> dsl.selectFrom(NPC)
                .orderBy(NPC.CREATED_AT.asc(), NPC.NAME.asc())
                .fetch()
                .map(NpcRows::toNpc));
    }

    @Override
    public boolean exists(NpcName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.fetchExists(NPC, NPC.NAME.eq(name.value())));
    }

    @Override
    public void save(Npc npc) {
        Objects.requireNonNull(npc, "npc");
        write(dsl -> {
            upsert(dsl, npc);
            return null;
        });
    }

    @Override
    public void delete(NpcName name) {
        Objects.requireNonNull(name, "name");
        write(dsl -> dsl.deleteFrom(NPC).where(NPC.NAME.eq(name.value())).execute());
    }

    private static void upsert(DSLContext dsl, Npc npc) {
        NpcRecord record = dsl.newRecord(NPC);
        NpcRows.apply(record, npc);
        dsl.insertInto(NPC)
                .set(record)
                .onConflict(NPC.NAME)
                .doUpdate()
                .set(NPC.WORLD, record.getWorld())
                .set(NPC.WORLD_NAME, record.getWorldName())
                .set(NPC.X, record.getX())
                .set(NPC.Y, record.getY())
                .set(NPC.Z, record.getZ())
                .set(NPC.YAW, record.getYaw())
                .set(NPC.PITCH, record.getPitch())
                .set(NPC.SKIN_TEXTURE, record.getSkinTexture())
                .set(NPC.SKIN_SIGNATURE, record.getSkinSignature())
                .set(NPC.CLICK_COMMAND, record.getClickCommand())
                .set(NPC.LOOK_AT_PLAYER, record.getLookAtPlayer())
                .set(NPC.CREATED_AT, record.getCreatedAt())
                .execute();
    }
}
