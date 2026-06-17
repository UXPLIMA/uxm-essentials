package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingEvents;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.RecordingView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.npc.domain.event.NpcCreated;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CopyNpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position SOURCE_AT = Position.of(WORLD, 1, 64, 1);
    private static final Position HERE = Position.of(WORLD, 10, 70, 10);

    private FakeNpcRepository repository;
    private RecordingView view;
    private RecordingEvents events;
    private CapturingSink sink;
    private CopyNpc copy;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        events = new RecordingEvents();
        sink = new CapturingSink();
        copy = new CopyNpc(
                repository,
                view,
                new NpcNotifier(new NpcTestSupport.KeyMessages(), sink),
                events,
                Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(
                Npc.create(NpcName.of("guide"), SOURCE_AT, new NpcSkin("tex", "sig"), Instant.ofEpochMilli(1_000)));
    }

    @Test
    void copiesAttributesToANewNameAtTheGivenPosition() {
        Result<Unit, NpcError> result = copy.copy(actor, NpcName.of("guide"), NpcName.of("guide2"), HERE);

        assertThat(result.isOk()).isTrue();
        Npc clone = repository.find(NpcName.of("guide2")).orElseThrow();
        assertThat(clone.location()).isEqualTo(HERE);
        assertThat(clone.hasSkin()).isTrue();
        assertThat(clone.createdAt()).isEqualTo(Instant.ofEpochMilli(9_000));
        assertThat(view.rendered).hasSize(1);
        assertThat(events.published).hasSize(1).first().isInstanceOf(NpcCreated.class);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_COPIED.key());
    }

    @Test
    void rejectsAMissingSource() {
        Result<Unit, NpcError> result = copy.copy(actor, NpcName.of("ghost"), NpcName.of("guide2"), HERE);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(repository.exists(NpcName.of("guide2"))).isFalse();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }

    @Test
    void rejectsATakenTargetName() {
        Result<Unit, NpcError> result = copy.copy(actor, NpcName.of("guide"), NpcName.of("Guide"), HERE);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NAME_TAKEN);
        assertThat(repository.all()).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NAME_TAKEN.key());
    }
}
