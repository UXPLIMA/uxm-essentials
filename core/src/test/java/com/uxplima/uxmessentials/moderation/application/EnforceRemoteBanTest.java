package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The live reaction to a ban on another backend: {@link EnforceRemoteBan} re-reads the now-authoritative
 * tempban from the shared DB and kicks the target only if they are connected here <em>and</em> the ban is
 * actually in effect. An offline target is left to the login check on their next reconnect; a frame for a
 * player whose ban is not (or no longer) active never kicks — the DB, not the frame, is the source of truth.
 * Self-origin frames never reach here (the bus client drops them before dispatch), so this layer only ever
 * sees genuine peer bans.
 */
class EnforceRemoteBanTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    private static EnforceRemoteBan enforce(
            FakeModerationRepository repository, FakeSanctions sanctions, Set<UUID> online) {
        ModerationFakes.FixedPlayers players = new ModerationFakes.FixedPlayers(Map.of(TARGET.uuid(), TARGET), online);
        return new EnforceRemoteBan(
                repository, players, sanctions, ModerationFakes.notifier(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void kicksAnOnlineTargetWhoseBanIsInEffect() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveTempban(
                TARGET,
                TempbanState.active(NOW.plus(Duration.ofDays(7)), Issuer.console("console"), Optional.of("ban"), NOW));
        FakeSanctions sanctions = new FakeSanctions(TARGET);

        enforce(repository, sanctions, Set.of(TARGET.uuid())).onRemoteBan(TARGET.uuid());

        assertThat(sanctions.kicked).containsExactly(TARGET);
    }

    @Test
    void doesNotKickAnOfflineTarget() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveTempban(
                TARGET,
                TempbanState.active(NOW.plus(Duration.ofDays(7)), Issuer.console("console"), Optional.empty(), NOW));
        FakeSanctions sanctions = new FakeSanctions();

        enforce(repository, sanctions, Set.of()).onRemoteBan(TARGET.uuid());

        assertThat(sanctions.kicked).isEmpty();
    }

    @Test
    void doesNotKickWhenNoActiveBanIsOnRecord() {
        // Online here, but the shared DB has no active ban (e.g. it was lifted between the frame and this read).
        FakeModerationRepository repository = new FakeModerationRepository();
        FakeSanctions sanctions = new FakeSanctions(TARGET);

        enforce(repository, sanctions, Set.of(TARGET.uuid())).onRemoteBan(TARGET.uuid());

        assertThat(sanctions.kicked).isEmpty();
    }

    @Test
    void doesNotKickWhenTheBanHasAlreadyExpired() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveTempban(
                TARGET,
                TempbanState.active(
                        NOW.minus(Duration.ofMinutes(1)),
                        Issuer.console("console"),
                        Optional.empty(),
                        NOW.minus(Duration.ofHours(1))));
        FakeSanctions sanctions = new FakeSanctions(TARGET);

        enforce(repository, sanctions, Set.of(TARGET.uuid())).onRemoteBan(TARGET.uuid());

        assertThat(sanctions.kicked).isEmpty();
    }
}
