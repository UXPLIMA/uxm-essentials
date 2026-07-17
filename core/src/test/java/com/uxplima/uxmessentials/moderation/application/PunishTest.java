package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.PunishmentTemplate;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * {@code /punish} dispatches a configured template to the existing sanction use cases: a timed template lands
 * a {@link TempBan} with the template's reason + span, a permanent one a permanent {@link Ban}, and an unknown
 * template is refused with {@link ModerationError#UNKNOWN_TEMPLATE} told to the actor and no sanction applied.
 */
class PunishTest {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    private final FakeModerationRepository repository = new FakeModerationRepository();
    private final FakeSanctionHistory history = new FakeSanctionHistory();
    private final FakeSanctions sanctions = new FakeSanctions(TARGET);

    @Test
    void aTimedTemplateAppliesATempbanWithTheTemplateReasonAndDuration() {
        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        Punish punish = punish(
                ModerationFakes.recordingNotifier(sink),
                PunishmentTemplate.timed("griefing", "Griefing", Duration.ofDays(7)));

        var result = punish.punish(ACTOR, TARGET, "griefing", false);

        assertThat(result.isOk()).isTrue();
        TempbanState stored = repository.loadTempban(TARGET);
        assertThat(stored).isInstanceOf(TempbanState.Active.class);
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofDays(6)))).isTrue();
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofDays(8)))).isFalse();
        assertThat(history.appended).singleElement().satisfies(row -> {
            assertThat(row.action()).isEqualTo(SanctionAction.BAN);
            assertThat(row.reason()).contains("Griefing");
            assertThat(row.expiry()).isPresent();
        });
        assertThat(sanctions.kicked).containsExactly(TARGET);
    }

    @Test
    void aPermanentTemplateAppliesAPermanentBan() {
        Punish punish = punish(ModerationFakes.notifier(), PunishmentTemplate.permanent("cheating", "Cheating"));

        var result = punish.punish(ACTOR, TARGET, "cheating", false);

        assertThat(result.isOk()).isTrue();
        TempbanState stored = repository.loadTempban(TARGET);
        assertThat(stored).isInstanceOf(TempbanState.Active.class);
        // A permanent ban stays active far past any tempban span.
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofDays(100_000)))).isTrue();
        assertThat(history.appended).singleElement().satisfies(row -> {
            assertThat(row.action()).isEqualTo(SanctionAction.BAN);
            assertThat(row.reason()).contains("Cheating");
            assertThat(row.expiry()).isEmpty();
        });
    }

    @Test
    void anUnknownTemplateIsRefusedWithAMessageAndAppliesNoSanction() {
        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        Punish punish =
                punish(ModerationFakes.recordingNotifier(sink), PunishmentTemplate.permanent("cheating", "Cheating"));

        var result = punish.punish(ACTOR, TARGET, "nope", false);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.UNKNOWN_TEMPLATE);
        assertThat(sink.sent(ACTOR, ModerationMessageKey.TEMPLATE_UNKNOWN)).isTrue();
        assertThat(repository.loadTempban(TARGET)).isInstanceOf(TempbanState.None.class);
        assertThat(history.appended).isEmpty();
    }

    private Punish punish(ModerationNotifier notifier, PunishmentTemplate template) {
        RecordingModerationAudit audit = new RecordingModerationAudit();
        SanctionHistoryRecorder recorder = new SanctionHistoryRecorder(history, CLOCK);
        SanctionDurationLimit limit = new SanctionDurationLimit(ModerationFakes.exempt());
        ModerationGuard guard = new ModerationGuard(ModerationFakes.exempt());
        ModerationFakes.RecordingEvents events = new ModerationFakes.RecordingEvents();
        Ban ban = new Ban(
                repository,
                sanctions,
                guard,
                notifier,
                audit,
                events,
                recorder,
                limit,
                ModerationFakes.broadcast(),
                SanctionSync.NONE,
                AddressStrictness.NORMAL,
                CLOCK);
        TempBan tempBan = new TempBan(
                repository,
                sanctions,
                guard,
                notifier,
                audit,
                events,
                recorder,
                limit,
                ModerationFakes.broadcast(),
                SanctionSync.NONE,
                CLOCK);
        ResolveTemplate templates = new ResolveTemplate(Map.of(template.name(), template));
        return new Punish(templates, ban, tempBan, notifier);
    }
}
