package com.uxplima.uxmessentials.npc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class NpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final Position ELSEWHERE = Position.of(WORLD, 9, 70, 9);
    private static final Instant CREATED = Instant.ofEpochMilli(1_000);

    @Test
    void createsAnNpcWithNoSkinAndNoCommand() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.name()).isEqualTo(NpcName.of("guide"));
        assertThat(npc.location()).isEqualTo(AT);
        assertThat(npc.hasSkin()).isFalse();
        assertThat(npc.hasClickCommand()).isFalse();
        assertThat(npc.lookAtPlayer()).isTrue();
        assertThat(npc.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void withLookAtPlayerTogglesAndKeepsEverythingElse() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn");

        Npc unlooking = npc.withLookAtPlayer(false);
        assertThat(unlooking.lookAtPlayer()).isFalse();
        assertThat(unlooking.skin()).isEqualTo(NpcSkin.unsigned("tex"));
        assertThat(unlooking.clickCommand()).isEqualTo("spawn");
        assertThat(unlooking.createdAt()).isEqualTo(CREATED);

        assertThat(unlooking.withLookAtPlayer(true).lookAtPlayer()).isTrue();
    }

    @Test
    void movedToReanchorsAndKeepsEverythingElse() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn")
                .withLookAtPlayer(false);

        Npc moved = npc.movedTo(ELSEWHERE);

        assertThat(moved.location()).isEqualTo(ELSEWHERE);
        assertThat(moved.skin()).isEqualTo(NpcSkin.unsigned("tex"));
        assertThat(moved.clickCommand()).isEqualTo("spawn");
        assertThat(moved.lookAtPlayer()).isFalse();
        assertThat(moved.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void withSkinReplacesTheSkinAndCanClearIt() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        Npc skinned = npc.withSkin(new NpcSkin("tex", "sig"));
        assertThat(skinned.hasSkin()).isTrue();
        assertThat(skinned.skin()).isEqualTo(new NpcSkin("tex", "sig"));

        assertThat(skinned.withSkin(null).hasSkin()).isFalse();
    }

    @Test
    void withClickCommandBindsAndClears() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        Npc bound = npc.withClickCommand("warp spawn");
        assertThat(bound.hasClickCommand()).isTrue();
        assertThat(bound.clickCommand()).isEqualTo("warp spawn");

        assertThat(bound.withClickCommand(null).hasClickCommand()).isFalse();
    }

    @Test
    void createsWithNoEquipmentAndNoGlow() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.equipment()).isEmpty();
        assertThat(npc.hasEquipment()).isFalse();
        assertThat(npc.glowing()).isFalse();
        assertThat(npc.glowColor()).isNull();
        assertThat(npc.hasGlowColor()).isFalse();
    }

    @Test
    void withEquipmentSetsAndClearsASlotKeepingTheRest() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withEquipment(EquipmentSlot.MAINHAND, "STICK");

        assertThat(npc.equipment())
                .containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .containsEntry(EquipmentSlot.MAINHAND, "STICK")
                .hasSize(2);
        assertThat(npc.hasEquipment()).isTrue();

        Npc cleared = npc.withEquipment(EquipmentSlot.HEAD, null);
        assertThat(cleared.equipment()).doesNotContainKey(EquipmentSlot.HEAD).hasSize(1);
    }

    @Test
    void equipmentMapIsImmutable() {
        Npc npc =
                Npc.create(NpcName.of("guide"), AT, null, CREATED).withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.equipment().put(EquipmentSlot.FEET, "BOOTS"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withGlowingAndColorToggleAndTintKeepingTheRest() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withGlowing(true)
                .withGlowColor("RED");

        assertThat(npc.glowing()).isTrue();
        assertThat(npc.glowColor()).isEqualTo("RED");
        assertThat(npc.hasGlowColor()).isTrue();
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));

        Npc cleared = npc.withGlowColor(null);
        assertThat(cleared.hasGlowColor()).isFalse();
        assertThat(cleared.glowing()).isTrue();
        assertThat(npc.withGlowing(false).glowing()).isFalse();
    }

    @Test
    void movedToAndWithSkinPreserveEquipmentAndGlow() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withGlowing(true)
                .withGlowColor("AQUA");

        Npc moved = npc.movedTo(ELSEWHERE).withSkin(NpcSkin.unsigned("tex"));

        assertThat(moved.equipment()).containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        assertThat(moved.glowing()).isTrue();
        assertThat(moved.glowColor()).isEqualTo("AQUA");
    }
}
