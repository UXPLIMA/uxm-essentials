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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CenterNpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeNpcRepository repository;
    private RecordingView view;
    private RecordingEvents events;
    private CapturingSink sink;
    private CenterNpc center;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        events = new RecordingEvents();
        sink = new CapturingSink();
        center = new CenterNpc(repository, view, new NpcNotifier(new NpcTestSupport.KeyMessages(), sink), events);
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void snapsTheNpcToItsBlockCentreKeepingYAndFacing() {
        repository.save(Npc.create(
                NpcName.of("guide"),
                new Position(WORLD, 10.7, 64.0, -3.2, 90f, 0f),
                null,
                Instant.ofEpochMilli(1_000)));

        Result<Unit, NpcError> result = center.center(actor, NpcName.of("guide"));

        assertThat(result.isOk()).isTrue();
        Position at = repository.find(NpcName.of("guide")).orElseThrow().location();
        assertThat(at.x()).isEqualTo(10.5);
        assertThat(at.z()).isEqualTo(-3.5);
        assertThat(at.y()).isEqualTo(64.0);
        assertThat(at.yaw()).isEqualTo(90f);
        assertThat(view.rendered).hasSize(1);
        assertThat(events.published).hasSize(1).first().isInstanceOf(NpcMoved.class);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_CENTERED.key());
    }

    @Test
    void rejectsAMissingNpc() {
        Result<Unit, NpcError> result = center.center(actor, NpcName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
