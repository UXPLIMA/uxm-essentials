package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The pure sliding-window decision behind command-spam protection: the first N commands in the window are fine and the
 * N+1-th trips, timestamps outside the interval are pruned so the count only reflects the current window, and an invalid
 * threshold or window is rejected up front.
 */
class CommandRateLimiterTest {

    @Test
    void theNPlusFirstCommandInTheWindowTrips() {
        CommandRateLimiter limiter = CommandRateLimiter.of(true, 3, 2, SpamAction.BLOCK);

        CommandWindow window = CommandWindow.empty();
        // Three commands at the same instant are within the limit of three.
        for (int i = 0; i < 3; i++) {
            CommandRateLimiter.Evaluation evaluation = limiter.evaluate(window, 1_000L);
            assertThat(evaluation.tripped()).isFalse();
            window = evaluation.window();
        }
        // The fourth command in the same window trips the limit.
        CommandRateLimiter.Evaluation fourth = limiter.evaluate(window, 1_000L);
        assertThat(fourth.tripped()).isTrue();
        assertThat(fourth.window().size()).isEqualTo(4);
    }

    @Test
    void underTheLimitNeverTrips() {
        CommandRateLimiter limiter = CommandRateLimiter.of(true, 5, 2, SpamAction.WARN);

        CommandWindow window = CommandWindow.empty();
        for (int i = 0; i < 5; i++) {
            CommandRateLimiter.Evaluation evaluation = limiter.evaluate(window, 500L + i);
            assertThat(evaluation.tripped()).isFalse();
            window = evaluation.window();
        }
        assertThat(window.size()).isEqualTo(5);
    }

    @Test
    void commandsOutsideTheWindowArePrunedSoTheCountResets() {
        CommandRateLimiter limiter = CommandRateLimiter.of(true, 2, 2, SpamAction.KICK);

        // Two commands at t=0 fill the window; a third at t=0 would trip.
        CommandWindow window = CommandWindow.empty();
        window = limiter.evaluate(window, 0L).window();
        window = limiter.evaluate(window, 0L).window();
        assertThat(limiter.evaluate(window, 0L).tripped()).isTrue();

        // Ten seconds later (well past the 2s window) the earlier commands are pruned, so a fresh command is fine
        // again.
        CommandRateLimiter.Evaluation later = limiter.evaluate(window, 10_000L);
        assertThat(later.tripped()).isFalse();
        assertThat(later.window().size()).isEqualTo(1);
    }

    @Test
    void aNullPreviousWindowIsTreatedAsEmpty() {
        CommandRateLimiter limiter = CommandRateLimiter.of(true, 1, 2, SpamAction.BLOCK);

        CommandRateLimiter.Evaluation first = limiter.evaluate(null, 1_000L);
        assertThat(first.tripped()).isFalse();
        assertThat(first.window().size()).isEqualTo(1);
    }

    @Test
    void anInvalidThresholdOrWindowIsRejected() {
        assertThatThrownBy(() -> CommandRateLimiter.of(true, 0, 2, SpamAction.BLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommandRateLimiter.of(true, 3, 0, SpamAction.BLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actionParsingIsCaseInsensitiveAndFallsBackOnAnUnknownValue() {
        assertThat(SpamAction.fromConfig("KICK", SpamAction.BLOCK)).isEqualTo(SpamAction.KICK);
        assertThat(SpamAction.fromConfig("warn", SpamAction.BLOCK)).isEqualTo(SpamAction.WARN);
        assertThat(SpamAction.fromConfig("nonsense", SpamAction.BLOCK)).isEqualTo(SpamAction.BLOCK);
        assertThat(SpamAction.fromConfig(null, SpamAction.WARN)).isEqualTo(SpamAction.WARN);
    }
}
