package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Address-strictness on {@code /ban}: STRICT additionally IP-bans the banned player's known addresses (the IP
 * history union the last-seen IP), so a banned account cannot return on a fresh one from the same connection;
 * NORMAL (the default) does not, leaving the UUID ban as the only effect. The STRICT fan-out is fail-safe (no
 * IP on record still bans the UUID) and never runs against an exempt target.
 */
class BanAddressStrictnessTest {

    private static final Instant NOW = Instant.parse("2026-06-13T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    private Ban ban(AddressStrictness strictness, ModerationGuard guard, FakeModerationRepository repository) {
        return new Ban(
                repository,
                new FakeSanctions(TARGET),
                guard,
                ModerationFakes.notifier(),
                new RecordingModerationAudit(),
                new ModerationFakes.RecordingEvents(),
                new SanctionHistoryRecorder(new FakeSanctionHistory(), Clock.fixed(NOW, ZoneOffset.UTC)),
                new SanctionDurationLimit(ModerationFakes.exempt()),
                ModerationFakes.broadcast(),
                strictness,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void strictBanAlsoIpBansEveryKnownAddress() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.recordSeen(TARGET, Optional.of("203.0.113.7"), NOW);
        repository.recordIpSeen(TARGET.uuid(), "203.0.113.7", NOW);
        repository.recordIpSeen(TARGET.uuid(), "198.51.100.5", NOW);

        ban(AddressStrictness.STRICT, new ModerationGuard(ModerationFakes.exempt()), repository)
                .ban(ACTOR, TARGET, Optional.of("cheating"), false);

        // Both historical addresses are now permanently IP-banned against the target UUID.
        assertThat(repository.activeIpBan("203.0.113.7", NOW)).isPresent().get().satisfies(b -> {
            assertThat(b.target()).contains(TARGET.uuid());
            assertThat(b.until()).isEmpty();
        });
        assertThat(repository.activeIpBan("198.51.100.5", NOW)).isPresent();
    }

    @Test
    void normalBanCreatesNoIpBan() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.recordSeen(TARGET, Optional.of("203.0.113.7"), NOW);
        repository.recordIpSeen(TARGET.uuid(), "203.0.113.7", NOW);

        ban(AddressStrictness.NORMAL, new ModerationGuard(ModerationFakes.exempt()), repository)
                .ban(ACTOR, TARGET, Optional.of("cheating"), false);

        assertThat(repository.activeIpBan("203.0.113.7", NOW)).isEmpty();
    }

    @Test
    void strictBanIsFailSafeWhenNoIpIsOnRecord() {
        FakeModerationRepository repository = new FakeModerationRepository();

        var result = ban(AddressStrictness.STRICT, new ModerationGuard(ModerationFakes.exempt()), repository)
                .ban(ACTOR, TARGET, Optional.of("cheating"), false);

        // The UUID ban still succeeds even though there was no address to fan out to.
        assertThat(result.isOk()).isTrue();
        assertThat(repository.loadTempban(TARGET).isActiveAt(NOW)).isTrue();
    }

    @Test
    void strictBanNeverIpBansAnExemptTarget() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.recordSeen(TARGET, Optional.of("203.0.113.7"), NOW);
        repository.recordIpSeen(TARGET.uuid(), "203.0.113.7", NOW);
        // An exempt target's /ban is refused outright, so no UUID ban and certainly no IP fan-out.
        ModerationGuard guard = new ModerationGuard(ModerationFakes.exempt(TARGET.uuid()));

        var result = ban(AddressStrictness.STRICT, guard, repository).ban(ACTOR, TARGET, Optional.empty(), false);

        assertThat(result.isErr()).isTrue();
        assertThat(repository.activeIpBan("203.0.113.7", NOW)).isEmpty();
    }
}
