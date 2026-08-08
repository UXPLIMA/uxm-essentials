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
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetNpcLookAtPlayerTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private SetNpcLookAtPlayer setLook;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        setLook = new SetNpcLookAtPlayer(repository, view, new Notifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));
    }

    @Test
    void disablesLookSavesReRendersAndNotifies() {
        Result<Unit, NpcError> result = setLook.setLookAtPlayer(actor, NpcName.of("guide"), false);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().lookAtPlayer())
                .isFalse();
        assertThat(view.rendered).hasSize(1);
        assertThat(view.rendered.getFirst().lookAtPlayer()).isFalse();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_LOOK_DISABLED.key());
    }

    @Test
    void enablingLookNotifiesWithTheEnabledKey() {
        setLook.setLookAtPlayer(actor, NpcName.of("guide"), false);

        setLook.setLookAtPlayer(actor, NpcName.of("guide"), true);

        assertThat(repository.find(NpcName.of("guide")).orElseThrow().lookAtPlayer())
                .isTrue();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_LOOK_ENABLED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = setLook.setLookAtPlayer(actor, NpcName.of("ghost"), true);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
