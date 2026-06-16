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
    void withEquipmentStoresAnOpaqueTokenVerbatim() {
        // The domain never interprets the equipment value — a serialized-item token is stored and returned
        // byte-for-byte, exactly as a material name would be.
        String token = "b64:rO0ABXNyAB...some-opaque-serialized-payload==";
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withEquipment(EquipmentSlot.MAINHAND, token);

        assertThat(npc.equipment()).containsEntry(EquipmentSlot.MAINHAND, token);
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

    @Test
    void createsWithNoActions() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.actions()).isEmpty();
        assertThat(npc.hasActions()).isFalse();
    }

    @Test
    void withActionAddedAppendsInOrderKeepingTheRest() {
        NpcAction first = new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.MESSAGE, "hi");
        NpcAction second = new NpcAction(ClickTrigger.ANY, NpcActionType.SOUND, "minecraft:ui.button.click");
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withClickCommand("spawn")
                .withActionAdded(first)
                .withActionAdded(second);

        assertThat(npc.actions()).containsExactly(first, second);
        assertThat(npc.hasActions()).isTrue();
        assertThat(npc.clickCommand()).isEqualTo("spawn");
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));
    }

    @Test
    void withActionRemovedAtDropsTheChosenOne() {
        NpcAction first = new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.MESSAGE, "hi");
        NpcAction second = new NpcAction(ClickTrigger.ANY, NpcActionType.ACTIONBAR, "bye");
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withActionAdded(first)
                .withActionAdded(second);

        assertThat(npc.withActionRemovedAt(0).actions()).containsExactly(second);
        assertThat(npc.withActionRemovedAt(1).actions()).containsExactly(first);
    }

    @Test
    void withActionRemovedAtRejectsAnOutOfRangeIndex() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withActionAdded(new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "hi"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withActionRemovedAt(1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> npc.withActionRemovedAt(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void withActionsClearedEmptiesTheList() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withActionAdded(new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "hi"))
                .withActionsCleared();

        assertThat(npc.actions()).isEmpty();
        assertThat(npc.hasActions()).isFalse();
    }

    @Test
    void actionsListIsImmutable() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED)
                .withActionAdded(new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "hi"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> npc.actions().add(new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyTransitionPreservesActions() {
        NpcAction action = new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "hi");
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withActionAdded(action);

        assertThat(npc.movedTo(ELSEWHERE).actions()).containsExactly(action);
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).actions()).containsExactly(action);
        assertThat(npc.withClickCommand("spawn").actions()).containsExactly(action);
        assertThat(npc.withLookAtPlayer(false).actions()).containsExactly(action);
        assertThat(npc.withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET").actions())
                .containsExactly(action);
        assertThat(npc.withGlowing(true).actions()).containsExactly(action);
        assertThat(npc.withGlowColor("RED").actions()).containsExactly(action);
        assertThat(npc.withEntityType("VILLAGER").actions()).containsExactly(action);
    }

    @Test
    void createsAsAPlayerTypeByDefault() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED);

        assertThat(npc.entityType()).isEqualTo("PLAYER");
        assertThat(npc.isPlayerType()).isTrue();
    }

    @Test
    void withEntityTypeUpperCasesAndSetsPlayerTypeFalseForAMob() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, NpcSkin.unsigned("tex"), CREATED)
                .withEntityType("villager");

        assertThat(npc.entityType()).isEqualTo("VILLAGER");
        assertThat(npc.isPlayerType()).isFalse();
        // The skin is preserved across a type flip so switching back to PLAYER restores it.
        assertThat(npc.skin()).isEqualTo(NpcSkin.unsigned("tex"));

        Npc backToPlayer = npc.withEntityType("PLAYER");
        assertThat(backToPlayer.isPlayerType()).isTrue();
        assertThat(backToPlayer.skin()).isEqualTo(NpcSkin.unsigned("tex"));
    }

    @Test
    void rejectsABlankEntityTypeAtConstruction() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> Npc.create(NpcName.of("guide"), AT, null, CREATED).withEntityType(" "))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> Npc.create(NpcName.of("guide"), AT, null, CREATED).withEntityType(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTransitionPreservesEntityType() {
        Npc npc = Npc.create(NpcName.of("guide"), AT, null, CREATED).withEntityType("ZOMBIE");
        NpcAction action = new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "hi");

        assertThat(npc.movedTo(ELSEWHERE).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withSkin(NpcSkin.unsigned("tex")).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withClickCommand("spawn").entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withLookAtPlayer(false).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET").entityType())
                .isEqualTo("ZOMBIE");
        assertThat(npc.withGlowing(true).entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withGlowColor("RED").entityType()).isEqualTo("ZOMBIE");
        assertThat(npc.withActionAdded(action).entityType()).isEqualTo("ZOMBIE");
        Npc withTwo = npc.withActionAdded(action).withActionAdded(action);
        assertThat(withTwo.withActionRemovedAt(0).entityType()).isEqualTo("ZOMBIE");
        assertThat(withTwo.withActionsCleared().entityType()).isEqualTo("ZOMBIE");
    }
}
