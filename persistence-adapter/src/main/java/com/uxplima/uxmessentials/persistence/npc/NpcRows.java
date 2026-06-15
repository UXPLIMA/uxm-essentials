package com.uxplima.uxmessentials.persistence.npc;

import java.time.Instant;
import java.util.UUID;

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
 * to face nearby viewers). This class is the single place that translation lives.
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
                Instant.ofEpochMilli(row.get(NPC.CREATED_AT)));
    }

    /** Populate an {@link NpcRecord} from a domain {@link Npc} for an upsert. */
    static void apply(NpcRecord record, Npc npc) {
        Position location = npc.location();
        NpcSkin skin = npc.skin();
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
                .setCreatedAt(npc.createdAt().toEpochMilli());
    }

    private static @Nullable NpcSkin skinOf(@Nullable String texture, @Nullable String signature) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        return new NpcSkin(texture, signature);
    }
}
