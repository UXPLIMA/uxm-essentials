package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.playerwarps.application.port.RentMailer;
import com.uxplima.uxmessentials.playerwarps.application.port.RentReminderCandidate;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class RentRemindersTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PlayerWarpId WARP = new PlayerWarpId(1);
    private static final PlayerRef OWNER = new PlayerRef(new java.util.UUID(3L, 3L), "mara");

    private final PlayerWarpTestSupport.Repo repo = new PlayerWarpTestSupport.Repo();
    private final RecordingMailer mailer = new RecordingMailer();

    private static RentConfig config(boolean enabled) {
        return new RentConfig(
                enabled,
                new BigDecimal("100"),
                "default",
                Duration.ofDays(7),
                Duration.ofDays(3),
                List.of(Duration.ofHours(24), Duration.ofHours(12), Duration.ofHours(6), Duration.ofHours(1)),
                Set.of(),
                Set.of(),
                Set.of());
    }

    private RentReminders reminders(RentConfig config) {
        return new RentReminders(repo, mailer, config, CLOCK);
    }

    private static RentReminderCandidate candidate(Duration remaining, int remindedStage) {
        return new RentReminderCandidate(WARP, OWNER, PlayerWarpName.of("citadel"), NOW.plus(remaining), remindedStage);
    }

    @Test
    void aWarpEnteringItsFirstWindowGetsOneMailAndBumpsTheStage() {
        boolean sent = reminders(config(true)).remind(candidate(Duration.ofHours(20), 0));

        assertThat(sent).isTrue();
        assertThat(mailer.mails).hasSize(1);
        assertThat(mailer.mails.get(0).owner()).isEqualTo(OWNER);
        assertThat(mailer.mails.get(0).key()).isEqualTo(PlayerwarpsMessageKey.PWARP_RENT_REMINDER);
        assertThat(mailer.mails.get(0).placeholders()).containsEntry("warp", "citadel");
        assertThat(repo.reminded.get(WARP)).isEqualTo(1);
    }

    @Test
    void asecondPassInTheSameWindowSendsNoDuplicate() {
        // The dedup counter has already recorded stage 1, so a second pass still inside the 24h window mails nothing.
        boolean sent = reminders(config(true)).remind(candidate(Duration.ofHours(20), 1));

        assertThat(sent).isFalse();
        assertThat(mailer.mails).isEmpty();
    }

    @Test
    void crossingIntoATighterWindowMailsAgain() {
        // Already reminded at stage 1 (24h); now inside the 12h window (stage 2), so a fresh mail is due.
        boolean sent = reminders(config(true)).remind(candidate(Duration.ofHours(10), 1));

        assertThat(sent).isTrue();
        assertThat(mailer.mails).hasSize(1);
        assertThat(repo.reminded.get(WARP)).isEqualTo(2);
    }

    @Test
    void aDisabledSubGroupMailsNothing() {
        boolean sent = reminders(config(false)).remind(candidate(Duration.ofHours(20), 0));

        assertThat(sent).isFalse();
        assertThat(mailer.mails).isEmpty();
        assertThat(repo.reminded).isEmpty();
    }

    @Test
    void theStageClimbsMonotonicallyAsTheTermApproaches() {
        RentReminders reminders = reminders(config(true));
        assertThat(reminders.stageFor(NOW.plus(Duration.ofHours(30)), NOW)).isZero();
        assertThat(reminders.stageFor(NOW.plus(Duration.ofHours(20)), NOW)).isEqualTo(1);
        assertThat(reminders.stageFor(NOW.plus(Duration.ofHours(10)), NOW)).isEqualTo(2);
        assertThat(reminders.stageFor(NOW.plus(Duration.ofHours(3)), NOW)).isEqualTo(3);
        assertThat(reminders.stageFor(NOW.plus(Duration.ofMinutes(30)), NOW)).isEqualTo(4);
    }

    /** Records every mail the reminder pass leaves, for owner/key/placeholder assertions. */
    private static final class RecordingMailer implements RentMailer {
        private record Sent(PlayerRef owner, MessageKey key, Map<String, String> placeholders) {}

        private final List<Sent> mails = new ArrayList<>();

        @Override
        public void mail(PlayerRef owner, MessageKey key, Map<String, String> placeholders) {
            mails.add(new Sent(owner, key, Map.copyOf(placeholders)));
        }
    }
}
