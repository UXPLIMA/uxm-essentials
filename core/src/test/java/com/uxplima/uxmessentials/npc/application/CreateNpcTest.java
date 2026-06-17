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

class CreateNpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private RecordingView view;
    private RecordingEvents events;
    private CapturingSink sink;
    private CreateNpc create;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        view = new RecordingView();
        events = new RecordingEvents();
        sink = new CapturingSink();
        create = new CreateNpc(
                repository,
                view,
                new NpcNotifier(new NpcTestSupport.KeyMessages(), sink),
                events,
                Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void createsStoresRendersPublishesAndNotifies() {
        Result<Unit, NpcError> result = create.create(actor, NpcName.of("guide"), AT, new NpcSkin("tex", "sig"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.exists(NpcName.of("guide"))).isTrue();
        assertThat(view.rendered).hasSize(1);
        assertThat(events.published).hasSize(1).first().isInstanceOf(NpcCreated.class);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_CREATED.key());
    }

    @Test
    void createsWithNoSkinSendsTheNoSkinFeedback() {
        create.create(actor, NpcName.of("guide"), AT, null);

        assertThat(repository.find(NpcName.of("guide")).orElseThrow().hasSkin()).isFalse();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_CREATED_NO_SKIN.key());
    }

    @Test
    void rejectsATakenName() {
        create.create(actor, NpcName.of("guide"), AT, null);

        Result<Unit, NpcError> again = create.create(actor, NpcName.of("Guide"), AT, null);

        assertThat(again.errorOrThrow()).isEqualTo(NpcError.NAME_TAKEN);
        assertThat(repository.all()).hasSize(1);
        assertThat(view.rendered).hasSize(1); // no second render
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NAME_TAKEN.key());
    }

    @Test
    void createsAsTheGivenEntityType() {
        create.create(actor, NpcName.of("piggy"), AT, null, "ZOMBIE");

        assertThat(repository.find(NpcName.of("piggy")).orElseThrow().entityType())
                .isEqualTo("ZOMBIE");
    }

    @Test
    void theCreatedNpcCarriesTheClockInstant() {
        create.create(actor, NpcName.of("guide"), AT, null);

        assertThat(repository.find(NpcName.of("guide")).orElseThrow().createdAt())
                .isEqualTo(Instant.ofEpochMilli(5_000));
    }
}
