package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.domain.ClickTrigger;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcAction;
import com.uxplima.uxmessentials.npc.domain.NpcActionType;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddNpcActionTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private AddNpcAction addAction;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        addAction = new AddNpcAction(repository, new NpcNotifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));
    }

    @Test
    void appendsActionsInOrderSavesAndNotifies() {
        NpcAction first = new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.MESSAGE, "hi");
        NpcAction second = new NpcAction(ClickTrigger.ANY, NpcActionType.SOUND, "ui.button.click");

        addAction.add(actor, NpcName.of("guide"), first);
        Result<Unit, NpcError> result = addAction.add(actor, NpcName.of("guide"), second);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().actions()).containsExactly(first, second);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_ACTION_ADDED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result =
                addAction.add(actor, NpcName.of("ghost"), new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "hi"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
