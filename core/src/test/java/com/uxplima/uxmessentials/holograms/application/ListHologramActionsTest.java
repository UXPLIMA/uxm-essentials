package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListHologramActionsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private CapturingSink sink;
    private ListHologramActions list;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        sink = new CapturingSink();
        list = new ListHologramActions(repository, new HologramNotifier(new HologramTestSupport.KeyMessages(), sink));
        viewer = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void pushesAHeaderAndAnEntryPerAction() {
        repository.save(Hologram.create(
                        HologramName.of("spawn"),
                        Position.of(WORLD, 0, 64, 0),
                        List.of(new HologramLine("line")),
                        Instant.EPOCH)
                .withActionAdded(new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, "first"))
                .withActionAdded(new ClickAction(ClickTrigger.LEFT_CLICK, ClickActionType.MESSAGE, "second")));

        Result<Unit, HologramError> result = list.list(viewer, HologramName.of("spawn"));

        assertThat(result.isOk()).isTrue();
        assertThat(sink.textFor(viewer))
                .containsExactly(
                        HologramsMessageKey.HOLOGRAM_ACTION_LIST_HEADER.key(),
                        HologramsMessageKey.HOLOGRAM_ACTION_LIST_ENTRY.key(),
                        HologramsMessageKey.HOLOGRAM_ACTION_LIST_ENTRY.key());
    }

    @Test
    void pushesTheEmptyNoticeWhenThereAreNoActions() {
        repository.save(Hologram.create(
                HologramName.of("spawn"),
                Position.of(WORLD, 0, 64, 0),
                List.of(new HologramLine("line")),
                Instant.EPOCH));

        Result<Unit, HologramError> result = list.list(viewer, HologramName.of("spawn"));

        assertThat(result.isOk()).isTrue();
        assertThat(sink.textFor(viewer)).containsExactly(HologramsMessageKey.HOLOGRAM_ACTION_LIST_EMPTY.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, HologramError> result = list.list(viewer, HologramName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(sink.textFor(viewer)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }
}
