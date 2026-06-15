package com.uxplima.uxmessentials.persistence.npc;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.npc.domain.Npc;
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
 * to face nearby viewers). Equipment is six nullable material-name columns ({@code equip_<slot>}), one per
 * wearable slot, NULL for an empty slot; {@code glowing} is a SMALLINT 0/1 and {@code glow_color} the optional
 * outline colour name. This class is the single place that translation lives.
 */
final class NpcRows {

    private static final com.uxplima.uxmessentials.persistence.jooq.tables.Npc NPC =
            com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;

    private NpcRows() {}

    /** Rebuild a domain {@link Npc} from an {@code npc} row. */
    static Npc toNpc(Record row) {
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
                .setEquipMainhand(equipment.get(EquipmentSlot.MAINHAND))
                .setEquipOffhand(equipment.get(EquipmentSlot.OFFHAND))
                .setEquipHead(equipment.get(EquipmentSlot.HEAD))
                .setEquipChest(equipment.get(EquipmentSlot.CHEST))
                .setEquipLegs(equipment.get(EquipmentSlot.LEGS))
                .setEquipFeet(equipment.get(EquipmentSlot.FEET))
                .setGlowing((short) (npc.glowing() ? 1 : 0))
                .setGlowColor(npc.glowColor())
                .setCreatedAt(npc.createdAt().toEpochMilli());
    }

    private static @Nullable NpcSkin skinOf(@Nullable String texture, @Nullable String signature) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        return new NpcSkin(texture, signature);
    }

    /** Read the six equipment columns into a slot-keyed map, skipping the NULL (empty) slots. */
    private static Map<EquipmentSlot, String> equipmentOf(Record row) {
        Map<EquipmentSlot, String> equipment = new EnumMap<>(EquipmentSlot.class);
        put(equipment, EquipmentSlot.MAINHAND, row.get(NPC.EQUIP_MAINHAND));
        put(equipment, EquipmentSlot.OFFHAND, row.get(NPC.EQUIP_OFFHAND));
        put(equipment, EquipmentSlot.HEAD, row.get(NPC.EQUIP_HEAD));
        put(equipment, EquipmentSlot.CHEST, row.get(NPC.EQUIP_CHEST));
        put(equipment, EquipmentSlot.LEGS, row.get(NPC.EQUIP_LEGS));
        put(equipment, EquipmentSlot.FEET, row.get(NPC.EQUIP_FEET));
        return equipment;
    }

    private static void put(Map<EquipmentSlot, String> equipment, EquipmentSlot slot, @Nullable String material) {
        if (material != null && !material.isBlank()) {
            equipment.put(slot, material);
        }
    }
}
