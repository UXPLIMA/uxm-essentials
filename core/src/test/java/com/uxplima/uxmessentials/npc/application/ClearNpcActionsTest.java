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

class ClearNpcActionsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private ClearNpcActions clearActions;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        clearActions = new ClearNpcActions(repository, new NpcNotifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000))
                .withClickCommand("spawn")
                .withActionAdded(new NpcAction(ClickTrigger.ANY, NpcActionType.MESSAGE, "hi")));
    }

    @Test
    void clearsEveryActionButKeepsTheClickCommand() {
        Result<Unit, NpcError> result = clearActions.clear(actor, NpcName.of("guide"));

        assertThat(result.isOk()).isTrue();
        Npc reloaded = repository.find(NpcName.of("guide")).orElseThrow();
        assertThat(reloaded.actions()).isEmpty();
        assertThat(reloaded.clickCommand()).isEqualTo("spawn");
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_ACTION_CLEARED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = clearActions.clear(actor, NpcName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
