package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DescribeNpcTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private DescribeNpc describe;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        describe = new DescribeNpc(repository, new NpcNotifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void printsAHeaderAndEveryPropertyBlock() {
        repository.save(Npc.create(NpcName.of("guide"), AT, new NpcSkin("tex", "sig"), Instant.ofEpochMilli(1_000)));

        Result<Unit, NpcError> result = describe.describe(actor, NpcName.of("guide"));

        assertThat(result.isOk()).isTrue();
        assertThat(sink.textFor(actor))
                .contains(
                        NpcMessageKey.NPC_INFO_HEADER.key(),
                        NpcMessageKey.NPC_INFO_LOCATION.key(),
                        NpcMessageKey.NPC_INFO_APPEARANCE.key(),
                        NpcMessageKey.NPC_INFO_FLAGS.key(),
                        NpcMessageKey.NPC_INFO_RANGES.key(),
                        NpcMessageKey.NPC_INFO_BEHAVIOR.key());
    }

    @Test
    void rejectsAMissingNpc() {
        Result<Unit, NpcError> result = describe.describe(actor, NpcName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
