package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcTeleporter;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeleportToNpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 12, 65, -7);

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private RecordingTeleporter teleporter;
    private TeleportToNpc teleport;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        teleporter = new RecordingTeleporter();
        teleport = new TeleportToNpc(repository, teleporter, new Notifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void sendsTheOperatorToTheNpcLocation() {
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));

        Result<Unit, NpcError> result = teleport.teleport(actor, NpcName.of("guide"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.who).isEqualTo(actor);
        assertThat(teleporter.destination).isEqualTo(AT);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_TELEPORTED.key());
    }

    @Test
    void rejectsAMissingNpc() {
        Result<Unit, NpcError> result = teleport.teleport(actor, NpcName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(teleporter.destination).isNull();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }

    private static final class RecordingTeleporter implements NpcTeleporter {
        private @Nullable PlayerRef who;
        private @Nullable Position destination;

        @Override
        public void teleport(PlayerRef who, Position destination) {
            this.who = who;
            this.destination = destination;
        }
    }
}
