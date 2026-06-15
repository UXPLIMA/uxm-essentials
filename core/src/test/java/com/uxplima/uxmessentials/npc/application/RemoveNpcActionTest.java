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

class RemoveNpcActionTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final NpcAction FIRST = new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.MESSAGE, "hi");
    private static final NpcAction SECOND = new NpcAction(ClickTrigger.ANY, NpcActionType.ACTIONBAR, "bye");

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private RemoveNpcAction removeAction;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        removeAction = new RemoveNpcAction(repository, new NpcNotifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000))
                .withActionAdded(FIRST)
                .withActionAdded(SECOND));
    }

    @Test
    void removesTheOneBasedIndexedAction() {
        Result<Unit, NpcError> result = removeAction.remove(actor, NpcName.of("guide"), 1);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().actions()).containsExactly(SECOND);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_ACTION_REMOVED.key());
    }

    @Test
    void rejectsAnOutOfRangeIndex() {
        Result<Unit, NpcError> result = removeAction.remove(actor, NpcName.of("guide"), 3);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.ACTION_INDEX_INVALID);
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().actions()).hasSize(2);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_ACTION_INDEX_INVALID.key());
    }

    @Test
    void rejectsZeroAsOutOfRange() {
        Result<Unit, NpcError> result = removeAction.remove(actor, NpcName.of("guide"), 0);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.ACTION_INDEX_INVALID);
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = removeAction.remove(actor, NpcName.of("ghost"), 1);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
