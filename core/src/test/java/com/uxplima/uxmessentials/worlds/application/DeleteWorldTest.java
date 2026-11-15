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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A world delete is asked for, then confirmed: only within the timeout the worlds config gives. */
class DeleteWorldTest {

    private static final WorldName GAMES = WorldName.of("games");

    private final InMemoryWorlds repository = new InMemoryWorlds();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final MutableClock clock = new MutableClock(NOW);

    private DeleteWorld deleteWorld(String timeout) {
        WorldsSettings settings = new WorldsSettings(new MapConfig().set("delete-confirm-timeout", timeout));
        return new DeleteWorld(
                repository,
                engine,
                new InMemoryPendingDeletions(),
                notifier.notifier(),
                new RecordingEvents(),
                new InlineScheduler(),
                clock,
                settings::protectDefaultWorld,
                settings::deleteConfirmTimeout);
    }

    private Result<Unit, WorldError> confirmAfter(DeleteWorld deleteWorld, Duration wait) {
        deleteWorld.request(ALEX, GAMES);
        clock.advance(wait);
        return deleteWorld.confirm(ALEX, GAMES);
    }

    @BeforeEach
    void aWorldExists() {
        WorldsSettings settings = new WorldsSettings(new MapConfig());
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
        // Two minutes rather than the shipped 30s, so a confirm that passes after 90 seconds proves the key is read.
        Result<Unit, WorldError> confirmed = confirmAfter(deleteWorld("2m"), Duration.ofSeconds(90));

        assertThat(confirmed.isOk())
                .as("confirm 90s after the request, timeout 2m")
                .isTrue();
        assertThat(repository.exists(GAMES)).isFalse();
        assertThat(engine.exists(GAMES)).isFalse();
        assertThat(notifier.keys()).last().isEqualTo("world.deleted");
    }

    @Test
    void aConfirmAfterTheTimeoutIsRefusedAndTheRequestIsDropped() {
        DeleteWorld deleteWorld = deleteWorld("2m");

        Result<Unit, WorldError> late =
                confirmAfter(deleteWorld, Duration.ofMinutes(2).plusSeconds(1));

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

    @ParameterizedTest
    @ValueSource(strings = {"0", "abc"})
    void aTimeoutThatIsNotALengthOfTimeActsAsThirtySeconds(String timeout) {
        assertThat(confirmAfter(deleteWorld(timeout), Duration.ofSeconds(25)).isOk())
                .as("confirm 25s after the request with delete-confirm-timeout = %s", timeout)
                .isTrue();

        aWorldExists();
        assertThat(confirmAfter(deleteWorld(timeout), Duration.ofSeconds(31)).asError())
                .as("confirm 31s after the request with delete-confirm-timeout = %s", timeout)
                .contains(WorldError.DELETE_EXPIRED);
    }
}
