package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NearbyNpcsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant T = Instant.ofEpochMilli(1_000);

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private NearbyNpcs nearby;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        nearby = new NearbyNpcs(repository, new NpcNotifier(new NpcTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void listsNpcsWithinRadiusNearestFirst() {
        repository.save(Npc.create(NpcName.of("far"), Position.of(WORLD, 100, 64, 0), null, T));
        repository.save(Npc.create(NpcName.of("guide"), Position.of(WORLD, 0, 64, 0), null, T));
        repository.save(Npc.create(NpcName.of("near"), Position.of(WORLD, 5, 64, 0), null, T));

        List<Npc> matches = nearby.nearby(actor, Position.of(WORLD, 0, 64, 0), 16);

        assertThat(matches).extracting(npc -> npc.name().value()).containsExactly("guide", "near");
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NEARBY_HEADER.key());
    }

    @Test
    void emptyWhenNoneWithinRadius() {
        repository.save(Npc.create(NpcName.of("far"), Position.of(WORLD, 100, 64, 0), null, T));

        List<Npc> matches = nearby.nearby(actor, Position.of(WORLD, 0, 64, 0), 16);

        assertThat(matches).isEmpty();
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_NEARBY_EMPTY.key());
    }
}
