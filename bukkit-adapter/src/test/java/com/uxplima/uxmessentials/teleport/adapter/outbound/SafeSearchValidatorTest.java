package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;
import org.junit.jupiter.api.Test;

/**
 * Pins the safe-search probe guard: a region read that throws must still COMPLETE the future the refill
 * loop awaits (completing it empty and logging), never leave it orphaned — an orphaned future would hang
 * the loop and burn that queue slot forever. A healthy probe completes with its value and logs nothing.
 */
class SafeSearchValidatorTest {

    private final RecordingLogger log = new RecordingLogger();
    private final SafeSearchValidator validator =
            new SafeSearchValidator(mock(Scheduler.class), mock(SafeSearchPolicy.class), Clock.systemUTC(), log);

    @Test
    void aThrowingProbeCompletesTheFutureEmptyAndLogsRatherThanOrphaningIt() throws Exception {
        CompletableFuture<Optional<RtpSafeLocation>> result = new CompletableFuture<>();

        validator.completeGuarded(result, () -> {
            throw new RuntimeException("chunk read failed");
        });

        assertThat(result).isDone();
        assertThat(result.get()).isEmpty();
        assertThat(log.errors).hasSize(1);
    }

    @Test
    void aSuccessfulProbeCompletesWithItsValueAndLogsNothing() throws Exception {
        CompletableFuture<Optional<RtpSafeLocation>> result = new CompletableFuture<>();
        RtpSafeLocation location = new RtpSafeLocation(
                Position.of(new WorldRef(UUID.randomUUID(), "world"), 0.0, 64.0, 0.0), 0.0, Instant.now());

        validator.completeGuarded(result, () -> Optional.of(location));

        assertThat(result).isDone();
        assertThat(result.get()).contains(location);
        assertThat(log.errors).isEmpty();
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
