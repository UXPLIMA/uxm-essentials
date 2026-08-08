package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
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

class SetNpcClickCommandTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private SetNpcClickCommand setCommand;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        setCommand = new SetNpcClickCommand(repository, new Notifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));
    }

    @Test
    void bindsTheCommandSavesAndNotifies() {
        Result<Unit, NpcError> result = setCommand.setCommand(actor, NpcName.of("guide"), "warp spawn");

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(NpcName.of("guide")).orElseThrow().clickCommand())
                .isEqualTo("warp spawn");
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_COMMAND_SET.key());
    }

    @Test
    void aBlankCommandClearsTheBinding() {
        setCommand.setCommand(actor, NpcName.of("guide"), "warp spawn");

        setCommand.setCommand(actor, NpcName.of("guide"), "   ");

        assertThat(repository.find(NpcName.of("guide")).orElseThrow().hasClickCommand())
                .isFalse();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_COMMAND_CLEARED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = setCommand.setCommand(actor, NpcName.of("ghost"), "spawn");

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
