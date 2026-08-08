package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListNpcsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private ListNpcs list;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        list = new ListNpcs(repository, new Notifier(new NpcTestSupport.KeyMessages(), sink));
        viewer = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void anEmptyListSendsTheEmptyNotice() {
        assertThat(list.list(viewer)).isEmpty();
        assertThat(sink.textFor(viewer)).contains(NpcMessageKey.NPC_LIST_EMPTY.key());
    }

    @Test
    void aPopulatedListSendsHeaderAndAnEntryPerNpc() {
        repository.save(Npc.create(NpcName.of("a"), AT, null, Instant.ofEpochMilli(1_000)));
        repository.save(Npc.create(NpcName.of("b"), AT, null, Instant.ofEpochMilli(2_000)));

        assertThat(list.list(viewer)).hasSize(2);
        assertThat(sink.textFor(viewer)).contains(NpcMessageKey.NPC_LIST_HEADER.key());
        assertThat(sink.textFor(viewer).stream().filter(t -> t.equals(NpcMessageKey.NPC_LIST_ENTRY.key())))
                .hasSize(2);
    }

    @Test
    void aTypeFilterNarrowsAndNameSortsTheList() {
        repository.save(Npc.create(NpcName.of("zombieB"), AT, null, Instant.ofEpochMilli(1_000))
                .withEntityType("ZOMBIE"));
        repository.save(Npc.create(NpcName.of("player"), AT, null, Instant.ofEpochMilli(2_000)));
        repository.save(Npc.create(NpcName.of("zombieA"), AT, null, Instant.ofEpochMilli(3_000))
                .withEntityType("ZOMBIE"));

        assertThat(list.list(viewer, "zombie"))
                .extracting(npc -> npc.name().value())
                .containsExactly("zombiea", "zombieb");
    }

    @Test
    void aTypeFilterWithNoMatchesSendsTheEmptyNotice() {
        repository.save(Npc.create(NpcName.of("player"), AT, null, Instant.ofEpochMilli(1_000)));

        assertThat(list.list(viewer, "creeper")).isEmpty();
        assertThat(sink.textFor(viewer)).contains(NpcMessageKey.NPC_LIST_EMPTY.key());
    }
}
