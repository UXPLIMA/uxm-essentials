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

class SetNpcGlowingTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private SetNpcGlowing setGlowing;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        setGlowing = new SetNpcGlowing(repository, view, new NpcNotifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));
    }

    @Test
    void enablesGlowWithAColorSavesReRendersAndNotifies() {
        Result<Unit, NpcError> result = setGlowing.setGlowing(actor, NpcName.of("guide"), true, "RED");

        assertThat(result.isOk()).isTrue();
        Npc saved = repository.find(NpcName.of("guide")).orElseThrow();
        assertThat(saved.glowing()).isTrue();
        assertThat(saved.glowColor()).isEqualTo("RED");
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_GLOW_SET.key());
    }

    @Test
    void enablesGlowWithoutAColorUsesTheEnabledKey() {
        setGlowing.setGlowing(actor, NpcName.of("guide"), true, "");

        Npc saved = repository.find(NpcName.of("guide")).orElseThrow();
        assertThat(saved.glowing()).isTrue();
        assertThat(saved.glowColor()).isNull();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_GLOW_ENABLED.key());
    }

    @Test
    void disablingGlowClearsTheColorAndUsesTheDisabledKey() {
        setGlowing.setGlowing(actor, NpcName.of("guide"), true, "RED");

        setGlowing.setGlowing(actor, NpcName.of("guide"), false, "");

        Npc saved = repository.find(NpcName.of("guide")).orElseThrow();
        assertThat(saved.glowing()).isFalse();
        assertThat(saved.glowColor()).isNull();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_GLOW_DISABLED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = setGlowing.setGlowing(actor, NpcName.of("ghost"), true, "RED");

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
