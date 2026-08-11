package com.uxplima.uxmessentials.holograms.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramClickCommand;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published hologram writes: they run the same use cases {@code /hologram} runs, line numbers count from one on
 * the way in, and the last remaining line is protected the way the command protects it.
 */
class HologramActionsTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UxmLocation SOMEWHERE = new UxmLocation("world", 10, 70, -20);

    private HologramApiSupport.FakeRepository repository;
    private HologramApiSupport.RecordingView view;
    private ActionDoubles.InlineScheduler scheduler;
    private HologramActions actions;

    @BeforeEach
    void setUp() {
        repository = new HologramApiSupport.FakeRepository();
        view = new HologramApiSupport.RecordingView();
        scheduler = new ActionDoubles.InlineScheduler();
        actions = actions();
    }

    @Test
    void createStoresOneLineOffTheCallingThreadAndDrawsIt() {
        UxmOutcome outcome = actions.create(ACTOR.uuid(), "spawn", SOMEWHERE, "<gold>Welcome")
                .join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(lines("spawn")).containsExactly("<gold>Welcome");
        assertThat(view.renders()).isOne();
        assertThat(scheduler.asyncCalls()).isOne();
        assertThat(scheduler.entityCalls()).isZero();
    }

    @Test
    void aWorldNobodyLoadedIsAnsweredNotScheduled() {
        UxmOutcome outcome = actions.create(ACTOR.uuid(), "spawn", new UxmLocation("nether", 0, 64, 0), "hi")
                .join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void theSameNameTwiceIsAlreadyExists() {
        actions.create(ACTOR.uuid(), "spawn", SOMEWHERE, "hi").join();

        UxmOutcome again =
                actions.create(ACTOR.uuid(), "spawn", SOMEWHERE, "hi").join();

        assertThat(again.failureOrThrow().code()).isEqualTo(UxmFailure.ALREADY_EXISTS);
    }

    @Test
    void linesAreAddedReplacedAndRemovedByTheNumberOnScreen() {
        actions.create(ACTOR.uuid(), "spawn", SOMEWHERE, "first").join();
        actions.addLine(ACTOR.uuid(), "spawn", "second").join();

        assertThat(actions.setLine(ACTOR.uuid(), "spawn", 1, "changed").join().succeeded())
                .isTrue();
        assertThat(lines("spawn")).containsExactly("changed", "second");

        assertThat(actions.removeLine(ACTOR.uuid(), "spawn", 2).join().succeeded())
                .isTrue();
        assertThat(lines("spawn")).containsExactly("changed");
    }

    @Test
    void aLineNumberPastTheEndIsNotFound() {
        actions.create(ACTOR.uuid(), "spawn", SOMEWHERE, "only").join();

        UxmOutcome outcome = actions.setLine(ACTOR.uuid(), "spawn", 4, "nope").join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
    }

    @Test
    void theLastLineIsRefusedRatherThanLeavingAnInvisibleHologram() {
        actions.create(ACTOR.uuid(), "spawn", SOMEWHERE, "only").join();

        UxmOutcome outcome = actions.removeLine(ACTOR.uuid(), "spawn", 1).join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(lines("spawn")).containsExactly("only");
    }

    @Test
    void lineNumbersCountFromOne() {
        assertThatThrownBy(() -> actions.setLine(ACTOR.uuid(), "spawn", 0, "nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveReAnchorsItAndDeleteTakesItAway() {
        actions.create(ACTOR.uuid(), "spawn", SOMEWHERE, "hi").join();

        assertThat(actions.move(ACTOR.uuid(), "spawn", new UxmLocation("world", 1, 2, 3))
                        .join()
                        .succeeded())
                .isTrue();
        assertThat(stored("spawn").location().x()).isEqualTo(1);

        assertThat(actions.delete(ACTOR.uuid(), "spawn").join().succeeded()).isTrue();
        assertThat(repository.all()).isEmpty();
        assertThat(view.despawns()).isOne();
    }

    @Test
    void aClickCommandIsStoredWithoutItsSlashAndCanBeUnbound() {
        actions.create(ACTOR.uuid(), "spawn", SOMEWHERE, "hi").join();

        actions.setClickCommand(ACTOR.uuid(), "spawn", "/spawn").join();
        assertThat(stored("spawn").clickCommand()).isEqualTo("spawn");

        actions.clearClickCommand(ACTOR.uuid(), "spawn").join();
        assertThat(stored("spawn").clickCommand()).isNull();
    }

    @Test
    void aNameNoHologramCouldHaveIsRefusedBeforeAnythingIsRead() {
        UxmOutcome outcome = actions.delete(ACTOR.uuid(), "x".repeat(HologramName.MAX_LENGTH + 1))
                .join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    private List<String> lines(String name) {
        return stored(name).lines().stream().map(HologramLine::value).toList();
    }

    private Hologram stored(String name) {
        return repository.find(HologramName.of(name)).orElseThrow();
    }

    private HologramActions actions() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);
        var notifier = ActionDoubles.silentNotifier();
        var events = new ActionDoubles.RecordingEvents();
        return new HologramActions(
                new CreateHologram(repository, view, notifier, events, clock),
                new DeleteHologram(repository, view, notifier, events),
                new MoveHologram(repository, view, notifier),
                new AddHologramLine(repository, view, notifier),
                new SetHologramLine(repository, view, notifier),
                new RemoveHologramLine(repository, view, notifier),
                new SetHologramClickCommand(repository, view, notifier),
                new QueryDoubles.MapLookup().with(ACTOR),
                new ActionDoubles.NamedWorlds().with(WORLD),
                scheduler);
    }
}
