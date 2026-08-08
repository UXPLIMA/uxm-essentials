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
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
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
        create = createWith(new NpcQuota(new FixedPermissions(QuotaResult.unlimited()), -1));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    private CreateNpc createWith(NpcQuota quota) {
        return new CreateNpc(
                repository,
                view,
                new Notifier(new NpcTestSupport.KeyMessages(), sink),
                events,
                Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC),
                quota);
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

    @Test
    void recordsTheCreatorAsTheOwner() {
        create.create(actor, NpcName.of("guide"), AT, null);

        assertThat(repository.find(NpcName.of("guide")).orElseThrow().owner()).isEqualTo(actor.uuid());
    }

    @Test
    void rejectsCreateWhenTheOwnerIsAtTheirLimit() {
        CreateNpc limited = createWith(new NpcQuota(new FixedPermissions(QuotaResult.limited(1)), 0));
        limited.create(actor, NpcName.of("first"), AT, null);

        Result<Unit, NpcError> result = limited.create(actor, NpcName.of("second"), AT, null);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.LIMIT_REACHED);
        assertThat(repository.exists(NpcName.of("second"))).isFalse();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_LIMIT_REACHED.key());
    }

    @Test
    void countsOnlyTheOwnersOwnNpcsAgainstTheLimit() {
        CreateNpc limited = createWith(new NpcQuota(new FixedPermissions(QuotaResult.limited(1)), 0));
        PlayerRef other = new PlayerRef(UUID.randomUUID(), "Other");
        limited.create(other, NpcName.of("theirs"), AT, null);

        Result<Unit, NpcError> result = limited.create(actor, NpcName.of("mine"), AT, null);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.exists(NpcName.of("mine"))).isTrue();
    }

    private static final class FixedPermissions implements Permissions {

        private final QuotaResult result;

        FixedPermissions(QuotaResult result) {
            this.result = result;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return result;
        }
    }
}
