package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationAction;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/**
 * A warn ladder sanction's reason is kept as its catalog line, so every reader reads it in their own language and
 * with the line as the operator has it now: the punished player in Turkish, the staff member in English. A reason a
 * staff member typed reads as typed, whoever reads it.
 */
class LadderReasonTest {

    private static final String ADDRESS = "198.51.100.4";
    private static final WarnEscalation LADDER = new WarnEscalation(List.of(
            new EscalationStep(2, EscalationAction.TEMPMUTE, Optional.of(Duration.ofHours(1))),
            new EscalationStep(3, EscalationAction.TEMPBAN, Optional.of(Duration.ofDays(1)))));

    /** The reason line in each language; Sam reads Turkish, everyone else English. */
    private final Map<String, String> english = new HashMap<>(Map.of(
            ModerationMessageKey.MOD_WARN_ESCALATION_REASON.key(), "reached {count} warnings, the last for: {reason}"));

    private final Map<String, String> turkish = new HashMap<>(Map.of(
            ModerationMessageKey.MOD_WARN_ESCALATION_REASON.key(), "{count} uyarıya ulaştı, sonuncusu: {reason}"));

    private final Messages messages = this::resolve;
    private final List<Line> told = new ArrayList<>();
    private final Notifier notifier = new Notifier(messages, (viewer, text) -> told.add(new Line(viewer, text)));
    private final List<Optional<DrawnReason>> announced = new ArrayList<>();
    private final SanctionBroadcast broadcast = new SanctionBroadcast() {
        @Override
        public void announce(MessageKey key, Map<String, String> placeholders) {
            announced.add(Optional.empty());
        }

        @Override
        public void announce(MessageKey key, Map<String, String> placeholders, Optional<DrawnReason> drawn) {
            announced.add(drawn);
        }
    };

    private final MutableClock clock = new MutableClock(NOW);
    private final SanctionRecords records = new SanctionRecords();
    private final RecordedHistory stored = new RecordedHistory();
    private final OnlinePlayers online = new OnlinePlayers().connect(SAM, ADDRESS);
    private final FakePermissions permissions = new FakePermissions();
    private final ModerationGuard guard = new ModerationGuard(permissions);
    private final SanctionHistoryRecorder history = new SanctionHistoryRecorder(stored, clock);
    private final SanctionDurationLimit limit = new SanctionDurationLimit(permissions);
    private final Mute mute = new Mute(
            records,
            guard,
            notifier,
            new SilentAudit(),
            new RecordingEvents(),
            history,
            limit,
            broadcast,
            SanctionSync.NONE,
            clock);
    private final TempBan tempBan = new TempBan(
            records,
            online,
            guard,
            notifier,
            new SilentAudit(),
            new RecordingEvents(),
            history,
            limit,
            broadcast,
            SanctionSync.NONE,
            clock);
    private final Ban ban = new Ban(
            records,
            online,
            guard,
            notifier,
            new SilentAudit(),
            new RecordingEvents(),
            history,
            limit,
            broadcast,
            SanctionSync.NONE,
            AddressStrictness.NORMAL,
            new NoIpHistory(),
            clock);
    private final Kick kick =
            new Kick(online, guard, notifier, new SilentAudit(), history, broadcast, new NobodyElsewhere());
    private final IssueWarn warn = new IssueWarn(
            records,
            guard,
            notifier,
            new SilentAudit(),
            new RecordingEvents(),
            history,
            broadcast,
            new WarnEscalator(LADDER, mute, tempBan, ban, kick, notifier),
            clock);
    private final CheckMute checkMute = new CheckMute(records, notifier, clock);
    private final CheckBan checkBan = new CheckBan(records, notifier, clock);
    private final ReviewSanctionHistory review = new ReviewSanctionHistory(stored, notifier);

    private record Line(PlayerRef viewer, String text) {}

    /** A reason the server drew is a catalogue line, so it goes into the reader's line as written. */
    private static String drawn(String line) {
        return TrustedMarkup.of(line);
    }

    /** A catalog that knows the reason line in two languages and echoes every other key with its reason. */
    private String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        String template = (viewer.equals(SAM) ? turkish : english).get(key.key());
        if (template == null) {
            return key.key() + " reason=" + placeholders.getOrDefault("reason", "");
        }
        String filled = template;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            filled = filled.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return filled;
    }

    private void warnSam(int times) {
        for (int i = 0; i < times; i++) {
            warn.warn(ALEX, SAM, Optional.of("spam"), false);
        }
    }

    private List<String> toldAlex() {
        return told.stream()
                .filter(line -> line.viewer().equals(ALEX))
                .map(Line::text)
                .toList();
    }

    @Test
    void aLadderMuteKeepsThePunishedPlayersTextAndItsLine() {
        warnSam(2);

        assertThat(records.loadMute(SAM)).isInstanceOfSatisfying(MuteState.Timed.class, timed -> {
            assertThat(timed.reason()).contains("2 uyarıya ulaştı, sonuncusu: spam");
            assertThat(timed.drawnReason())
                    .contains(new DrawnReason(
                            ModerationMessageKey.MOD_WARN_ESCALATION_REASON, Map.of("count", "2", "reason", "spam")));
        });
    }

    @Test
    void staffReadALadderMuteInTheirOwnLanguage() {
        warnSam(2);
        told.clear();

        checkMute.show(ALEX, SAM);
        review.show(ALEX, SAM);

        assertThat(toldAlex())
                .contains(
                        ModerationMessageKey.MOD_CHECK_MUTED.key() + " reason="
                                + drawn("reached 2 warnings, the last for: spam"),
                        ModerationMessageKey.MOD_HISTORY_ENTRY.key() + " reason="
                                + drawn("reached 2 warnings, the last for: spam"));
    }

    @Test
    void aLadderBanReadsInTheReadersLanguageAndOnTheKickScreenInThePlayersOwn() {
        warnSam(3);
        told.clear();

        checkBan.show(ALEX, SAM);

        assertThat(toldAlex())
                .containsExactly(ModerationMessageKey.MOD_CHECK_BANNED.key() + " reason="
                        + drawn("reached 3 warnings, the last for: spam"));
        assertThat(online.kicked())
                .containsEntry(
                        SAM,
                        ModerationMessageKey.TEMPBAN_KICK.key() + " reason="
                                + drawn("3 uyarıya ulaştı, sonuncusu: spam"));
        assertThat(records.loadTempban(SAM))
                .isInstanceOfSatisfying(
                        TempbanState.Active.class,
                        active -> assertThat(active.reason()).contains("3 uyarıya ulaştı, sonuncusu: spam"));
    }

    @Test
    void anEditOfTheLineReachesSanctionsAlreadyGiven() {
        warnSam(2);
        english.put(ModerationMessageKey.MOD_WARN_ESCALATION_REASON.key(), "warning {count}: {reason}");
        told.clear();

        checkMute.show(ALEX, SAM);

        assertThat(toldAlex())
                .containsExactly(ModerationMessageKey.MOD_CHECK_MUTED.key() + " reason=" + drawn("warning 2: spam"));
    }

    @Test
    void theStaffBroadcastIsHandedTheLineToDrawForEachReader() {
        warnSam(2);

        assertThat(announced)
                .contains(Optional.of(new DrawnReason(
                        ModerationMessageKey.MOD_WARN_ESCALATION_REASON, Map.of("count", "2", "reason", "spam"))));
    }

    @Test
    void aReasonAStaffMemberTypedReadsAsTypedForEveryone() {
        mute.mute(ALEX, SAM, "1h", Optional.of("slurs"), false);
        told.clear();

        checkMute.show(ALEX, SAM);
        review.show(ALEX, SAM);

        assertThat(records.loadMute(SAM))
                .isInstanceOfSatisfying(
                        MuteState.Timed.class,
                        timed -> assertThat(timed.drawnReason()).isEmpty());
        assertThat(toldAlex())
                .contains(
                        ModerationMessageKey.MOD_CHECK_MUTED.key() + " reason=slurs",
                        ModerationMessageKey.MOD_HISTORY_ENTRY.key() + " reason=slurs");
    }

    /** No player is on another server. */
    private static final class NobodyElsewhere implements NetworkPresence {

        @Override
        public String localServer() {
            return "survival";
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return Optional.empty();
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return List.of();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.of();
        }
    }
}
