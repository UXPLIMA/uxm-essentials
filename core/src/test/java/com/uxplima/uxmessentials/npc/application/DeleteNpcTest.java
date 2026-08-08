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
import com.uxplima.uxmessentials.npc.domain.event.NpcDeleted;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeleteNpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private RecordingView view;
    private RecordingEvents events;
    private CapturingSink sink;
    private DeleteNpc delete;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        events = new RecordingEvents();
        sink = new CapturingSink();
        delete = new DeleteNpc(repository, view, new Notifier(new NpcTestSupport.KeyMessages(), sink), events);
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void deletesDespawnsPublishesAndNotifies() {
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));

        Result<Unit, NpcError> result = delete.delete(actor, NpcName.of("guide"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.exists(NpcName.of("guide"))).isFalse();
        assertThat(view.despawned).containsExactly(NpcName.of("guide"));
        assertThat(events.published).hasSize(1).first().isInstanceOf(NpcDeleted.class);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_DELETED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = delete.delete(actor, NpcName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(view.despawned).isEmpty();
        assertThat(events.published).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
