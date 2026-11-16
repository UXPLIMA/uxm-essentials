package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.permission.PermissionScope;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSnapshot;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Off the server thread, with no answer read in advance, the immunity check and the duration cap refuse rather than
 * read the permission plugin from a worker, and say so in the log.
 */
class LanePermissionsTest {

    private final FakePermissions live = new FakePermissions();
    private final RecordingLogger log = new RecordingLogger();
    private final LanePermissions offThread = new LanePermissions(live, () -> false, log);

    @Test
    void withNothingReadInAdvanceTheTargetCountsAsExemptAndItIsLogged() {
        assertThat(new ModerationGuard(offThread).isExempt(SAM)).isTrue();

        assertThat(log.warnings()).hasSize(1);
        assertThat(log.warnings().get(0)).startsWith("event=moderation_permission_unread");
    }

    @Test
    void withNothingReadInAdvanceTheCapIsZero() {
        Duration capped = new SanctionDurationLimit(offThread).cap(ALEX, "ban", Duration.ofDays(1));

        assertThat(capped).isEqualTo(Duration.ZERO);
        assertThat(log.warnings()).hasSize(1);
    }

    @Test
    void anAnswerReadInAdvanceIsUsedAndNothingIsLogged() {
        live.quota(EnforcementPermissions.maxDuration("mute"), QuotaResult.limited(60));
        PermissionSnapshot answers = LanePermissions.capture(live, ALEX)
                .answered(SAM, ModerationGuard.EXEMPT_NODE, false)
                .build();
        live.grant(SAM, ModerationGuard.EXEMPT_NODE);
        live.quota(EnforcementPermissions.maxDuration("mute"), QuotaResult.unlimited());

        PermissionScope.runWith(answers, () -> {
            assertThat(new ModerationGuard(offThread).isExempt(SAM)).isFalse();
            assertThat(new SanctionDurationLimit(offThread).cap(ALEX, "mute", Duration.ofDays(1)))
                    .isEqualTo(Duration.ofMinutes(1));
        });
        assertThat(log.warnings()).isEmpty();
    }

    @Test
    void onAServerThreadThatCannotReadTheTargetTheTargetCountsAsExempt() {
        // A Folia region thread asking about a player another region owns: the live reader has no answer.
        Permissions unreadable = new Permissions() {
            @Override
            public boolean has(PlayerRef who, String node) {
                return false;
            }

            @Override
            public Optional<Boolean> read(PlayerRef who, String node) {
                return Optional.empty();
            }

            @Override
            public QuotaResult resolveQuota(
                    PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
                return QuotaResult.limited(configDefault);
            }
        };
        LanePermissions onRegion = new LanePermissions(unreadable, () -> true, log);

        assertThat(new ModerationGuard(onRegion).isExempt(SAM)).isTrue();
        assertThat(log.warnings()).hasSize(1);
    }

    @Test
    void onAServerThreadThatOwnsTheTargetTheLiveAnswerIsUsed() {
        LanePermissions onOwner = new LanePermissions(live, () -> true, log);

        assertThat(new ModerationGuard(onOwner).isExempt(SAM)).isFalse();
        live.grant(SAM, ModerationGuard.EXEMPT_NODE);
        assertThat(new ModerationGuard(onOwner).isExempt(SAM)).isTrue();
        assertThat(log.warnings()).isEmpty();
    }

    @Test
    void aSystemActorAnswersAsOfflineWithoutReadingAnything() {
        PlayerRef system = PlayerRef.system("system");

        assertThat(new SanctionDurationLimit(offThread).cap(system, "ban", Duration.ofDays(1)))
                .isEqualTo(Duration.ofDays(1));
        assertThat(log.warnings()).isEmpty();
    }
}
