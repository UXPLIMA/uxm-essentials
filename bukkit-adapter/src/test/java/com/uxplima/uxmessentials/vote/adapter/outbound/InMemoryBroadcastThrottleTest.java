package com.uxplima.uxmessentials.vote.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link InMemoryBroadcastThrottle}: an unrecorded voter has no stamp, a recorded one reads
 * back its timestamp, and a later record replaces the earlier one.
 */
class InMemoryBroadcastThrottleTest {

    private final InMemoryBroadcastThrottle throttle = new InMemoryBroadcastThrottle();

    @Test
    void unrecordedVoterHasNoStamp() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");

        assertThat(throttle.lastBroadcastAt(alice)).isEmpty();
    }

    @Test
    void recordedVoterReadsBackTheTimestamp() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        Instant at = Instant.ofEpochSecond(1_000);

        throttle.recordBroadcast(alice, at);

        assertThat(throttle.lastBroadcastAt(alice)).contains(at);
    }

    @Test
    void aLaterRecordReplacesTheEarlierStamp() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        throttle.recordBroadcast(alice, Instant.ofEpochSecond(1_000));

        Instant later = Instant.ofEpochSecond(2_000);
        throttle.recordBroadcast(alice, later);

        assertThat(throttle.lastBroadcastAt(alice)).contains(later);
    }
}
