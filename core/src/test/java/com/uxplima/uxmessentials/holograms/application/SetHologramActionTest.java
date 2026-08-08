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
import com.uxplima.uxmessentials.shared.application.message.Notifier;
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

class SetHologramActionTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final ClickAction REPLACEMENT =
            new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_CONSOLE, "say hi");

    private FakeHologramRepository repository;
    private CapturingSink sink;
    private SetHologramAction set;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        sink = new CapturingSink();
        set = new SetHologramAction(repository, new Notifier(new HologramTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void replacesTheActionAtTheIndexAndNotifies() {
        repository.save(withTwoActions("spawn"));

        Result<Unit, HologramError> result = set.set(actor, HologramName.of("spawn"), 1, REPLACEMENT);

        assertThat(result.isOk()).isTrue();
        Hologram updated = repository.find(HologramName.of("spawn")).orElseThrow();
        assertThat(updated.actions()).hasSize(2);
        assertThat(updated.actions().get(0)).isEqualTo(REPLACEMENT);
        assertThat(updated.actions().get(1).value()).isEqualTo("second");
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_ACTION_SET.key());
    }

    @Test
    void rejectsAnOutOfRangeIndex() {
        repository.save(withTwoActions("spawn"));

        Result<Unit, HologramError> result = set.set(actor, HologramName.of("spawn"), 9, REPLACEMENT);

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.ACTION_INDEX_OUT_OF_RANGE);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_ACTION_INDEX_INVALID.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, HologramError> result = set.set(actor, HologramName.of("ghost"), 1, REPLACEMENT);

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    private Hologram withTwoActions(String name) {
        return Hologram.create(
                        HologramName.of(name),
                        Position.of(WORLD, 0, 64, 0),
                        List.of(new HologramLine("line")),
                        Instant.EPOCH)
                .withActionAdded(new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, "first"))
                .withActionAdded(new ClickAction(ClickTrigger.LEFT_CLICK, ClickActionType.MESSAGE, "second"));
    }
}
