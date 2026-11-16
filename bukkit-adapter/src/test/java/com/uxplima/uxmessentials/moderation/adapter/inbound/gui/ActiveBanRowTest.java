package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/** The active-punishments menu shows a permanent ban as permanent and a timed ban with its end. */
class ActiveBanRowTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final Issuer STAFF =
            Issuer.of(new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex"));

    private static ActivePunishment row(Instant until) {
        return ActivePunishment.ofBan(new BanEntry(TARGET, STAFF, Optional.empty(), until, NOW), "Sam");
    }

    @Test
    void aPermanentBanRowIsPermanent() {
        ActivePunishment row = row(NOW.plus(Ban.PERMANENT_SPAN));

        assertThat(row.isPermanent()).isTrue();
        assertThat(row.until()).isEmpty();
    }

    @Test
    void aTimedBanRowKeepsItsEnd() {
        Instant end = NOW.plus(Duration.ofDays(2));

        ActivePunishment row = row(end);

        assertThat(row.isPermanent()).isFalse();
        assertThat(row.until()).contains(end);
    }
}
