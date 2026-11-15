package com.uxplima.uxmessentials.worlds.application;

import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.worlds.application.port.WorldClaims;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.Test;

/** An operator takes an unclaimed world onto this server with {@code /worlds claim}. */
class ClaimWorldTest {

    private static final WorldName ARENA = WorldName.of("arena");

    private final RecordingNotifier notifier = new RecordingNotifier();

    private void claim(WorldClaims claims) {
        new ClaimWorld(claims, notifier.notifier(), new InlineScheduler(), new RecordingLogger()).claim(SAM, ARENA);
    }

    @Test
    void aClaimedWorldIsConfirmedAndOneThatCannotBeClaimedIsRefused() {
        claim(name -> true);
        claim(name -> false);

        assertThat(notifier.keys()).containsExactly("world.claimed", "world.claim-none");
    }

    @Test
    void aClaimThatFailsInTheDatabaseStillAnswersTheOperator() {
        claim(name -> {
            throw new IllegalStateException("database gone");
        });

        assertThat(notifier.sent()).singleElement().satisfies(line -> {
            assertThat(line.key()).isEqualTo("world.io-error");
            assertThat(line.placeholders()).containsEntry("world", "arena");
        });
    }
}
