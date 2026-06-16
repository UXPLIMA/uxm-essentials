package com.uxplima.uxmessentials.persistence.npc;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.domain.ClickTrigger;
import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcAction;
import com.uxplima.uxmessentials.npc.domain.NpcActionType;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.NpcRecord;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between an {@code npc} row and the domain {@link Npc}. The world uuid is stored as
 * its canonical 36-character text and the creation time as epoch milliseconds, so the column shape is identical
 * on every backend. The skin columns are nullable: a NULL {@code skin_texture} reads back as no skin (the
 * default Steve fake player), and a present texture rebuilds an {@link NpcSkin} carrying its (possibly NULL)
 * signature. The {@code click_command} column is likewise nullable — a NULL means clicking the NPC does
 * nothing. The {@code look_at_player} column is a SMALLINT 0/1 read back as a boolean (whether the NPC rotates
 * to face nearby viewers). Equipment is now stored as an opaque per-slot token (either a legacy material name or
 * a serialized full-item payload) in the V45 TEXT columns ({@code equip_<slot>_b64}); the V40 VARCHAR columns
 * ({@code equip_<slot>}) are kept for backward compatibility, so a save writes the new column and a read takes
 * the new column first and falls back to the old one when it is NULL — an NPC stored before V45 keeps its gear.
 * A slot with both columns NULL is empty. {@code glowing} is a SMALLINT 0/1 and {@code glow_color} the optional
 * outline colour name. The {@code entity_type} column is the uppercase Bukkit {@code EntityType} name the NPC
 * renders as ({@code PLAYER} by default, the fake-player path), NOT NULL so an older row reads back as a player.
 * The {@code pose} column is the uppercase pose name the NPC is frozen in ({@code STANDING} by default), NOT NULL
 * so an older row reads upright; {@code scale} is the size multiplier in a REAL column ({@code 1.0} by default,
 * narrowed to a float on save and widened back on read), NOT NULL so an older row reads back natural-sized.
 * The click-action chain lives in the child {@code npc_action} table and is passed in already ordered — each
 * row's {@code click_trigger}/{@code type} are the enum names and {@code value} the raw operator payload. This
 * class is the single place that translation lives.
 */
final class NpcRows {

    private static final com.uxplima.uxmessentials.persistence.jooq.tables.Npc NPC =
            com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;

    private NpcRows() {}

    /** Rebuild a domain {@link Npc} from an {@code npc} row and its already-ordered action list. */
    static Npc toNpc(Record row, List<NpcAction> orderedActions) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(NPC.WORLD)), row.get(NPC.WORLD_NAME));
        Position position = new Position(
                world, row.get(NPC.X), row.get(NPC.Y), row.get(NPC.Z), row.get(NPC.YAW), row.get(NPC.PITCH));
        return new Npc(
                NpcName.of(row.get(NPC.NAME)),
                position,
                skinOf(row.get(NPC.SKIN_TEXTURE), row.get(NPC.SKIN_SIGNATURE)),
                row.get(NPC.CLICK_COMMAND),
                row.get(NPC.LOOK_AT_PLAYER) != 0,
                equipmentOf(row),
                row.get(NPC.GLOWING) != 0,
                row.get(NPC.GLOW_COLOR),
                orderedActions,
                row.get(NPC.ENTITY_TYPE),
                row.get(NPC.POSE),
                row.get(NPC.SCALE),
                Instant.ofEpochMilli(row.get(NPC.CREATED_AT)));
    }

    /** Populate an {@link NpcRecord} from a domain {@link Npc} for an upsert. */
    static void apply(NpcRecord record, Npc npc) {
        Position location = npc.location();
        NpcSkin skin = npc.skin();
        Map<EquipmentSlot, String> equipment = npc.equipment();
        record.setName(npc.name().value())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setSkinTexture(skin == null ? null : skin.texture())
                .setSkinSignature(skin == null ? null : skin.signature())
                .setClickCommand(npc.clickCommand())
                .setLookAtPlayer((short) (npc.lookAtPlayer() ? 1 : 0))
                // The token (material name or serialized item) is written to the V45 TEXT columns; the V40
                // VARCHAR columns are left NULL on a save and only ever read for a pre-V45 row's gear.
                .setEquipMainhandB64(equipment.get(EquipmentSlot.MAINHAND))
                .setEquipOffhandB64(equipment.get(EquipmentSlot.OFFHAND))
                .setEquipHeadB64(equipment.get(EquipmentSlot.HEAD))
                .setEquipChestB64(equipment.get(EquipmentSlot.CHEST))
                .setEquipLegsB64(equipment.get(EquipmentSlot.LEGS))
                .setEquipFeetB64(equipment.get(EquipmentSlot.FEET))
                .setGlowing((short) (npc.glowing() ? 1 : 0))
                .setGlowColor(npc.glowColor())
                .setEntityType(npc.entityType())
                .setPose(npc.pose())
                // scale is a REAL column (jOOQ maps it to Float); the domain carries the wider double, narrowed
                // here for storage and widened back on read — the protocol's scale range fits a float exactly.
                .setScale((float) npc.scale())
                .setCreatedAt(npc.createdAt().toEpochMilli());
    }

    private static @Nullable NpcSkin skinOf(@Nullable String texture, @Nullable String signature) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        return new NpcSkin(texture, signature);
    }

    /**
     * Read the six equipment slots into a slot-keyed map, skipping the empty slots. Each slot prefers its V45
     * {@code equip_<slot>_b64} token and falls back to the V40 {@code equip_<slot>} material name when the new
     * column is NULL, so an NPC stored before V45 keeps its gear.
     */
    private static Map<EquipmentSlot, String> equipmentOf(Record row) {
        Map<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);
        put(equipment, EquipmentSlot.MAINHAND, row.get(NPC.EQUIP_MAINHAND_B64), row.get(NPC.EQUIP_MAINHAND));
        put(equipment, EquipmentSlot.OFFHAND, row.get(NPC.EQUIP_OFFHAND_B64), row.get(NPC.EQUIP_OFFHAND));
        put(equipment, EquipmentSlot.HEAD, row.get(NPC.EQUIP_HEAD_B64), row.get(NPC.EQUIP_HEAD));
        put(equipment, EquipmentSlot.CHEST, row.get(NPC.EQUIP_CHEST_B64), row.get(NPC.EQUIP_CHEST));
        put(equipment, EquipmentSlot.LEGS, row.get(NPC.EQUIP_LEGS_B64), row.get(NPC.EQUIP_LEGS));
        put(equipment, EquipmentSlot.FEET, row.get(NPC.EQUIP_FEET_B64), row.get(NPC.EQUIP_FEET));
        return equipment;
    }

    /** Store {@code token} for {@code slot}, preferring the V45 value and falling back to the legacy one. */
    private static void put(
            Map<EquipmentSlot, String> equipment, EquipmentSlot slot, @Nullable String token, @Nullable String legacy) {
        String value = token != null && !token.isBlank() ? token : legacy;
        if (value != null && !value.isBlank()) {
            equipment.put(slot, value);
        }
    }

    /**
     * Build a domain {@link NpcAction} from a stored row's trigger/type/value, or {@code null} when the trigger
     * or type enum name no longer parses (a forward-incompatible row is skipped on load rather than crashing the
     * whole NPC set). The caller filters the nulls out.
     */
    static @Nullable NpcAction toAction(String trigger, String type, String value) {
        ClickTrigger clickTrigger = enumOrNull(ClickTrigger.class, trigger);
        NpcActionType actionType = enumOrNull(NpcActionType.class, type);
        if (clickTrigger == null || actionType == null) {
            return null;
        }
        return new NpcAction(clickTrigger, actionType, value);
    }

    private static <E extends Enum<E>> @Nullable E enumOrNull(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
