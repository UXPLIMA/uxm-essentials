package com.uxplima.uxmessentials.persistence.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.NpcRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * The jOOQ-backed {@link NpcRepository} over the generated {@code NPC} table and its ordered {@code NPC_ACTION}
 * child table. NPCs are server-wide and keyed by name alone, so a lookup is a single-row {@code SELECT} on the
 * name primary key plus its ordered actions, the list reads every row in stored creation order, and a {@code
 * save} upserts the name row then rewrites that NPC's action rows in one transaction (so an action edit never
 * leaves a stale row behind). Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqNpcRepository extends JooqRepository implements NpcRepository {

    private static final com.uxplima.uxmessentials.persistence.jooq.tables.Npc NPC =
            com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;
    private static final com.uxplima.uxmessentials.persistence.jooq.tables.NpcAction NPC_ACTION =
            com.uxplima.uxmessentials.persistence.jooq.tables.NpcAction.NPC_ACTION;
    private static final com.uxplima.uxmessentials.persistence.jooq.tables.NpcTypeData NPC_TYPE_DATA =
            com.uxplima.uxmessentials.persistence.jooq.tables.NpcTypeData.NPC_TYPE_DATA;

    public JooqNpcRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Npc> find(NpcName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(NPC)
                .where(NPC.NAME.eq(name.value()))
                .fetchOptional()
                .map(row -> NpcRows.toNpc(row, actions(dsl, name.value()), typeData(dsl, name.value()))));
    }

    @Override
    public List<Npc> all() {
        return read(dsl -> {
            Map<String, List<ClickAction>> actionsByName = allActions(dsl);
            Map<String, Map<String, String>> typeDataByName = allTypeData(dsl);
            return dsl.selectFrom(NPC)
                    .orderBy(NPC.CREATED_AT.asc(), NPC.NAME.asc())
                    .fetch()
                    .map(row -> NpcRows.toNpc(
                            row,
                            actionsByName.getOrDefault(row.get(NPC.NAME), List.of()),
                            typeDataByName.getOrDefault(row.get(NPC.NAME), Map.of())));
        });
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
            rewriteActions(dsl, npc);
            rewriteTypeData(dsl, npc);
            return null;
        });
    }

    @Override
    public void delete(NpcName name) {
        Objects.requireNonNull(name, "name");
        write(dsl -> {
            dsl.deleteFrom(NPC_ACTION)
                    .where(NPC_ACTION.NPC_NAME.eq(name.value()))
                    .execute();
            dsl.deleteFrom(NPC_TYPE_DATA)
                    .where(NPC_TYPE_DATA.NPC_NAME.eq(name.value()))
                    .execute();
            return dsl.deleteFrom(NPC).where(NPC.NAME.eq(name.value())).execute();
        });
    }

    private static List<ClickAction> actions(DSLContext dsl, String name) {
        List<ClickAction> actions = new ArrayList<>();
        for (Record row : dsl.select(NPC_ACTION.CLICK_TRIGGER, NPC_ACTION.TYPE, NPC_ACTION.VALUE)
                .from(NPC_ACTION)
                .where(NPC_ACTION.NPC_NAME.eq(name))
                .orderBy(NPC_ACTION.ORDINAL.asc())
                .fetch()) {
            addAction(actions, row);
        }
        return actions;
    }

    private static Map<String, List<ClickAction>> allActions(DSLContext dsl) {
        Map<String, List<ClickAction>> byName = new LinkedHashMap<>();
        for (Record row : dsl.select(NPC_ACTION.NPC_NAME, NPC_ACTION.CLICK_TRIGGER, NPC_ACTION.TYPE, NPC_ACTION.VALUE)
                .from(NPC_ACTION)
                .orderBy(NPC_ACTION.NPC_NAME.asc(), NPC_ACTION.ORDINAL.asc())
                .fetch()) {
            addAction(byName.computeIfAbsent(row.get(NPC_ACTION.NPC_NAME), key -> new ArrayList<>()), row);
        }
        return byName;
    }

    private static void addAction(List<ClickAction> target, Record row) {
        ClickAction action = NpcRows.toAction(
                row.get(NPC_ACTION.CLICK_TRIGGER), row.get(NPC_ACTION.TYPE), row.get(NPC_ACTION.VALUE));
        if (action != null) {
            target.add(action);
        }
    }

    private static Map<String, String> typeData(DSLContext dsl, String name) {
        Map<String, String> data = new LinkedHashMap<>();
        for (Record row : dsl.select(NPC_TYPE_DATA.DATA_KEY, NPC_TYPE_DATA.DATA_VALUE)
                .from(NPC_TYPE_DATA)
                .where(NPC_TYPE_DATA.NPC_NAME.eq(name))
                .orderBy(NPC_TYPE_DATA.DATA_KEY.asc())
                .fetch()) {
            data.put(row.get(NPC_TYPE_DATA.DATA_KEY), row.get(NPC_TYPE_DATA.DATA_VALUE));
        }
        return data;
    }

    private static Map<String, Map<String, String>> allTypeData(DSLContext dsl) {
        Map<String, Map<String, String>> byName = new LinkedHashMap<>();
        for (Record row : dsl.select(NPC_TYPE_DATA.NPC_NAME, NPC_TYPE_DATA.DATA_KEY, NPC_TYPE_DATA.DATA_VALUE)
                .from(NPC_TYPE_DATA)
                .orderBy(NPC_TYPE_DATA.NPC_NAME.asc(), NPC_TYPE_DATA.DATA_KEY.asc())
                .fetch()) {
            byName.computeIfAbsent(row.get(NPC_TYPE_DATA.NPC_NAME), key -> new LinkedHashMap<>())
                    .put(row.get(NPC_TYPE_DATA.DATA_KEY), row.get(NPC_TYPE_DATA.DATA_VALUE));
        }
        return byName;
    }

    private static void rewriteTypeData(DSLContext dsl, Npc npc) {
        String name = npc.name().value();
        dsl.deleteFrom(NPC_TYPE_DATA).where(NPC_TYPE_DATA.NPC_NAME.eq(name)).execute();
        for (Map.Entry<String, String> entry : npc.typeData().entrySet()) {
            dsl.insertInto(NPC_TYPE_DATA)
                    .set(NPC_TYPE_DATA.NPC_NAME, name)
                    .set(NPC_TYPE_DATA.DATA_KEY, entry.getKey())
                    .set(NPC_TYPE_DATA.DATA_VALUE, entry.getValue())
                    .execute();
        }
    }

    private static void rewriteActions(DSLContext dsl, Npc npc) {
        String name = npc.name().value();
        dsl.deleteFrom(NPC_ACTION).where(NPC_ACTION.NPC_NAME.eq(name)).execute();
        List<ClickAction> actions = npc.actions();
        for (int ordinal = 0; ordinal < actions.size(); ordinal++) {
            ClickAction action = actions.get(ordinal);
            dsl.insertInto(NPC_ACTION)
                    .set(NPC_ACTION.NPC_NAME, name)
                    .set(NPC_ACTION.ORDINAL, ordinal)
                    .set(NPC_ACTION.CLICK_TRIGGER, action.trigger().name())
                    .set(NPC_ACTION.TYPE, action.type().name())
                    .set(NPC_ACTION.VALUE, action.value())
                    .execute();
        }
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
                .set(NPC.SKIN_SLIM, record.getSkinSlim())
                .set(NPC.CLICK_COMMAND, record.getClickCommand())
                .set(NPC.LOOK_AT_PLAYER, record.getLookAtPlayer())
                .set(NPC.INTERACTION_COOLDOWN_MILLIS, record.getInteractionCooldownMillis())
                // Equipment is written to the V45 token columns; the legacy V40 columns are cleared on update so
                // a migrated row reads its gear from the new column rather than a now-stale material name.
                .set(NPC.EQUIP_MAINHAND, record.getEquipMainhand())
                .set(NPC.EQUIP_OFFHAND, record.getEquipOffhand())
                .set(NPC.EQUIP_HEAD, record.getEquipHead())
                .set(NPC.EQUIP_CHEST, record.getEquipChest())
                .set(NPC.EQUIP_LEGS, record.getEquipLegs())
                .set(NPC.EQUIP_FEET, record.getEquipFeet())
                .set(NPC.EQUIP_MAINHAND_B64, record.getEquipMainhandB64())
                .set(NPC.EQUIP_OFFHAND_B64, record.getEquipOffhandB64())
                .set(NPC.EQUIP_HEAD_B64, record.getEquipHeadB64())
                .set(NPC.EQUIP_CHEST_B64, record.getEquipChestB64())
                .set(NPC.EQUIP_LEGS_B64, record.getEquipLegsB64())
                .set(NPC.EQUIP_FEET_B64, record.getEquipFeetB64())
                .set(NPC.GLOWING, record.getGlowing())
                .set(NPC.GLOW_COLOR, record.getGlowColor())
                .set(NPC.ENTITY_TYPE, record.getEntityType())
                .set(NPC.POSE, record.getPose())
                .set(NPC.SCALE, record.getScale())
                .set(NPC.DISPLAY_NAME, record.getDisplayName())
                .set(NPC.MIRROR_SKIN, record.getMirrorSkin())
                .set(NPC.COLLIDABLE, record.getCollidable())
                .set(NPC.SHOW_IN_TAB, record.getShowInTab())
                .set(NPC.ON_FIRE, record.getOnFire())
                .set(NPC.INVISIBLE, record.getInvisible())
                .set(NPC.SILENT, record.getSilent())
                .set(NPC.VIEW_DISTANCE, record.getViewDistance())
                .set(NPC.TURN_DISTANCE, record.getTurnDistance())
                .set(NPC.CREATED_AT, record.getCreatedAt())
                .execute();
    }
}
