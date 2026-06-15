package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingView;
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

class MoveNpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final Position ELSEWHERE = Position.of(WORLD, 9, 70, 9);

    private FakeNpcRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private MoveNpc move;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        move = new MoveNpc(repository, view, new NpcNotifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void reanchorsSavesRendersAndNotifies() {
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));

        Result<Unit, NpcError> result = move.move(actor, NpcName.of("guide"), ELSEWHERE);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().location())
                .isEqualTo(ELSEWHERE);
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_MOVED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = move.move(actor, NpcName.of("ghost"), ELSEWHERE);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
