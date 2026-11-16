package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.moderation.adapter.SanctionRows;
import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import org.junit.jupiter.api.Test;

/**
 * {@code moderation_ban_remaining} reads {@code permanent} for a permanent ban, however long ago it was issued, and the
 * whole seconds left for a timed ban, a {@code /ban} shortened by a duration cap included.
 */
class BanRemainingPlaceholderTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final Issuer STAFF =
            Issuer.of(new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex"));

    private final SanctionRows bans = new SanctionRows();
    private final PlaceholderResolver resolver = new PlaceholderResolver(PlaceholderContexts.builder()
            .moderation(new GateModerationPlaceholders(
                    MutePolicy.NEVER,
                    JailGate.NEVER,
                    bans,
                    new NoSanctions(),
                    (viewer, key, placeholders) -> key.key(),
                    Clock.fixed(NOW, ZoneOffset.UTC)))
            .build());

    private Optional<String> banRemaining() {
        return resolver.resolve(SAM, true, "moderation_ban_remaining");
    }

    private void banSam(Instant issuedAt, Instant until) {
        bans.ban(SAM, TempbanState.active(until, STAFF, Optional.empty(), issuedAt));
    }

    @Test
    void aPermanentBanReadsPermanent() {
        Instant issuedAt = NOW.minus(Duration.ofDays(400));
        banSam(issuedAt, issuedAt.plus(Ban.PERMANENT_SPAN));

        assertThat(banRemaining()).contains("permanent");
    }

    @Test
    void aBanShortenedByADurationCapReadsTheSecondsLeft() {
        Instant issuedAt = NOW.minus(Duration.ofMinutes(15));
        banSam(issuedAt, issuedAt.plus(Duration.ofHours(1)));

        assertThat(banRemaining()).contains(Long.toString(Duration.ofMinutes(45).toSeconds()));
    }

    @Test
    void aTimedBanReadsTheSecondsLeft() {
        banSam(NOW, NOW.plus(Duration.ofDays(2)));

        assertThat(banRemaining()).contains(Long.toString(Duration.ofDays(2).toSeconds()));
    }

    @Test
    void aTimedBanLongerThanACenturyIsStillTimed() {
        Duration twoCenturies = Duration.ofDays(73_000);
        banSam(NOW, NOW.plus(twoCenturies));

        assertThat(banRemaining()).contains(Long.toString(twoCenturies.toSeconds()));
    }

    /** No live player is touched by a ban placeholder. */
    private static final class NoSanctions implements Sanctions {

        @Override
        public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void kickAddress(String ip, Predicate<PlayerRef> kickable, Function<PlayerRef, String> reasonFor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<PlayerRef> onlinePlayers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void freeze(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unfreeze(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFrozen(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendToJail(PlayerRef target, String jail) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseFromJail(PlayerRef target) {
            throw new UnsupportedOperationException();
        }
    }
}
