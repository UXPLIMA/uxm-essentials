package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingView;
import com.uxplima.uxmessentials.npc.domain.EquipmentSlot;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetNpcEquipmentTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private SetNpcEquipment setEquipment;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        setEquipment = new SetNpcEquipment(repository, view, new Notifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));
    }

    @Test
    void setsASlotSavesReRendersAndNotifies() {
        Result<Unit, NpcError> result =
                setEquipment.setEquipment(actor, NpcName.of("guide"), EquipmentSlot.HEAD, "DIAMOND_HELMET");

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().equipment())
                .containsEntry(EquipmentSlot.HEAD, "DIAMOND_HELMET");
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_EQUIP_SET.key());
    }

    @Test
    void clearsASlotWhenTheMaterialIsBlank() {
        setEquipment.setEquipment(actor, NpcName.of("guide"), EquipmentSlot.HEAD, "DIAMOND_HELMET");

        setEquipment.setEquipment(actor, NpcName.of("guide"), EquipmentSlot.HEAD, "");

        assertThat(repository.find(NpcName.of("guide")).orElseThrow().equipment())
                .doesNotContainKey(EquipmentSlot.HEAD);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_EQUIP_CLEARED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result =
                setEquipment.setEquipment(actor, NpcName.of("ghost"), EquipmentSlot.HEAD, "DIAMOND_HELMET");

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
