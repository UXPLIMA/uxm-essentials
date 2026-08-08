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

class ListNpcTypeDataTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private ListNpcTypeData listData;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        listData = new ListNpcTypeData(repository, new Notifier(new NpcTestSupport.KeyMessages(), sink));
        viewer = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void listsTheMetadataWithHeaderAndEntries() {
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000))
                .withTypeData("size", "4")
                .withTypeData("charged", "true"));

        Result<Unit, NpcError> result = listData.list(viewer, NpcName.of("guide"));

        assertThat(result.isOk()).isTrue();
        assertThat(sink.textFor(viewer))
                .contains(NpcMessageKey.NPC_DATA_LIST_HEADER.key())
                .contains(NpcMessageKey.NPC_DATA_LIST_ENTRY.key());
    }

    @Test
    void reportsTheEmptyNoticeWhenNoMetadata() {
        repository.save(Npc.create(NpcName.of("guide"), AT, null, Instant.ofEpochMilli(1_000)));

        Result<Unit, NpcError> result = listData.list(viewer, NpcName.of("guide"));

        assertThat(result.isOk()).isTrue();
        assertThat(sink.textFor(viewer)).contains(NpcMessageKey.NPC_DATA_NONE.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, NpcError> result = listData.list(viewer, NpcName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.NOT_FOUND);
        assertThat(sink.textFor(viewer)).contains(NpcMessageKey.NPC_NOT_FOUND.key());
    }
}
