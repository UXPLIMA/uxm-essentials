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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NpcEquipmentManagementTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final NpcName NAME = NpcName.of("guard");

    private FakeNpcRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private SetNpcEquipment equip;
    private ListNpcEquipment listEquip;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        NpcNotifier notifier = new NpcNotifier(new NpcTestSupport.KeyMessages(), sink);
        equip = new SetNpcEquipment(repository, view, notifier);
        listEquip = new ListNpcEquipment(repository, notifier);
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    private void seedEquipped() {
        Npc npc = Npc.create(NAME, AT, null, Instant.ofEpochMilli(1_000))
                .withEquipment(EquipmentSlot.HEAD, "DIAMOND_HELMET")
                .withEquipment(EquipmentSlot.CHEST, "IRON_CHESTPLATE");
        repository.save(npc);
    }

    @Test
    void clearAllRemovesEveryItemAndReRenders() {
        seedEquipped();

        Result<Unit, NpcError> result = equip.clearAll(actor, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NAME).orElseThrow().equipment()).isEmpty();
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_EQUIP_CLEARED_ALL.key());
    }

    @Test
    void clearAllRejectsAMissingNpc() {
        Result<Unit, NpcError> result = equip.clearAll(actor, NpcName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }

    @Test
    void listShowsAHeaderAndAnEntryPerWornSlot() {
        seedEquipped();

        Result<Unit, NpcError> result = listEquip.list(actor, NAME);

        assertThat(result.isOk()).isTrue();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_EQUIP_LIST_HEADER.key());
        assertThat(sink.textFor(actor).stream().filter(t -> t.equals(NpcMessageKey.NPC_EQUIP_LIST_ENTRY.key())))
                .hasSize(2);
    }

    @Test
    void listSendsTheEmptyNoticeWhenNothingWorn() {
        repository.save(Npc.create(NAME, AT, null, Instant.ofEpochMilli(1_000)));

        listEquip.list(actor, NAME);

        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_EQUIP_LIST_EMPTY.key());
    }
}
