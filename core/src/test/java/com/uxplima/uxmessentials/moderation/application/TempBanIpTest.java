package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * {@code /tempbanip}: a timed IP ban. The {@link IpBan}'s {@code until} expiry is honoured by the existing
 * {@link LoginEnforcement}, so a connection from the banned address is refused before expiry and admitted
 * after; a permanent/blank duration is refused (use {@code /banip} for permanent).
 */
class TempBanIpTest {

    private static final Instant NOW = Instant.parse("2026-06-02T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef OFFENDER = new PlayerRef(UUID.randomUUID(), "griefer");
    private static final String IP = "203.0.113.7";

    @Test
    void tempBanIpBarsTheAddressUntilExpiryThenAdmitsIt() {
        FakeModerationRepository repository = new FakeModerationRepository();
        TempBanIp tempBanIp = useCase(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = tempBanIp.tempBanIp(
                ACTOR, new BanIp.Target(IP, Optional.of(OFFENDER.uuid())), "1h", Optional.of("alts"));

        assertThat(result.isOk()).isTrue();
        // Before the hour is up, the login listener refuses a connection from the banned address.
        LoginEnforcement gate = enforcement(repository, NOW.plus(Duration.ofMinutes(30)));
        assertThat(gate.evaluate(OFFENDER, IP).allowed()).isFalse();
        // After the hour, the same address is admitted again — the ban auto-expired.
        LoginEnforcement later = enforcement(repository, NOW.plus(Duration.ofHours(2)));
        assertThat(later.evaluate(OFFENDER, IP).allowed()).isTrue();
    }

    @Test
    void aPermanentOrBlankDurationIsRefused() {
        FakeModerationRepository repository = new FakeModerationRepository();
        TempBanIp tempBanIp = useCase(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = tempBanIp.tempBanIp(ACTOR, new BanIp.Target(IP, Optional.empty()), "", Optional.empty());

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.BAD_DURATION);
        // Nothing was written: the address is still admitted.
        assertThat(enforcement(repository, NOW).evaluate(OFFENDER, IP).allowed())
                .isTrue();
    }

    private TempBanIp useCase(FakeModerationRepository repository, Clock clock) {
        return new TempBanIp(
                repository,
                ModerationFakes.notifier(),
                new RecordingModerationAudit(),
                new ModerationFakes.RecordingEvents(),
                clock);
    }

    private LoginEnforcement enforcement(FakeModerationRepository repository, Instant at) {
        return new LoginEnforcement(
                repository,
                ModerationFakes.notifier(),
                new RecordingModerationAudit(),
                Clock.fixed(at, ZoneOffset.UTC));
    }
}
