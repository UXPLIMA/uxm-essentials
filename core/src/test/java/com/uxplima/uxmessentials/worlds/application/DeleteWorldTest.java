package com.uxplima.uxmessentials.worlds.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.worlds.domain.WorldCreateOptions;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A world delete is asked for, then confirmed: only within the timeout the worlds config gives. */
class DeleteWorldTest {

    private static final WorldName GAMES = WorldName.of("games");

    private final InMemoryWorlds repository = new InMemoryWorlds();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final MutableClock clock = new MutableClock(NOW);
    // Two minutes rather than the shipped 30s, so a confirm that passes after 90 seconds proves the key is read.
    private final WorldsSettings settings = new WorldsSettings(new MapConfig().set("delete-confirm-timeout", "2m"));

    private final DeleteWorld deleteWorld = new DeleteWorld(
            repository,
            engine,
            new InMemoryPendingDeletions(),
            notifier.notifier(),
            new RecordingEvents(),
            new InlineScheduler(),
            clock,
            settings::protectDefaultWorld,
            settings::deleteConfirmTimeout);

    @BeforeEach
    void aWorldExists() {
        new CreateWorld(
                        repository,
                        engine,
                        notifier.notifier(),
                        new RecordingEvents(),
                        new InlineScheduler(),
                        clock,
                        settings::creationDefaults)
                .create(ALEX, GAMES, WorldCreateOptions.none(), true);
    }

    @Test
    void aConfirmWithinTheTimeoutDeletesTheWorld() {
        deleteWorld.request(ALEX, GAMES);
        clock.advance(Duration.ofSeconds(90));

        Result<Unit, WorldError> confirmed = deleteWorld.confirm(ALEX, GAMES);

        assertThat(confirmed.isOk())
                .as("confirm 90s after the request, timeout 2m")
                .isTrue();
        assertThat(repository.exists(GAMES)).isFalse();
        assertThat(engine.exists(GAMES)).isFalse();
        assertThat(notifier.keys()).last().isEqualTo("world.deleted");
    }

    @Test
    void aConfirmAfterTheTimeoutIsRefusedAndTheRequestIsDropped() {
        deleteWorld.request(ALEX, GAMES);
        clock.advance(Duration.ofMinutes(2).plusSeconds(1));

        Result<Unit, WorldError> late = deleteWorld.confirm(ALEX, GAMES);

        assertThat(late.asError()).contains(WorldError.DELETE_EXPIRED);
        assertThat(repository.exists(GAMES))
                .as("the world row survives a late confirm")
                .isTrue();
        assertThat(engine.exists(GAMES))
                .as("the world files survive a late confirm")
                .isTrue();
        assertThat(notifier.keys()).last().isEqualTo("world.delete-expired");

        assertThat(deleteWorld.confirm(ALEX, GAMES).asError())
                .as("a second confirm finds nothing staged")
                .contains(WorldError.NOT_FOUND);
        assertThat(notifier.keys()).last().isEqualTo("world.delete-none");
    }
}
