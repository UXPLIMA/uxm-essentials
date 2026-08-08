package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingEvents;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.event.NpcMoved;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
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
    private RecordingEvents events;
    private MoveNpc move;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        events = new RecordingEvents();
        move = new MoveNpc(repository, view, new Notifier(new NpcTestSupport.KeyMessages(), sink), events);
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void reanchorsSavesRendersNotifiesAndPublishes() {
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));

        Result<Unit, NpcError> result = move.move(actor, NpcName.of("guide"), ELSEWHERE);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().location())
                .isEqualTo(ELSEWHERE);
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_MOVED.key());
        assertThat(events.published)
                .hasSize(1)
                .first()
                .isInstanceOf(NpcMoved.class)
                .isEqualTo(new NpcMoved(NpcName.of("guide"), ELSEWHERE));
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = move.move(actor, NpcName.of("ghost"), ELSEWHERE);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
        assertThat(events.published).isEmpty();
    }
}
