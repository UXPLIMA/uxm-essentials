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
}
