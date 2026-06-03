package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /banhistory} and {@code /mutehistory}: read-only reviews of a player's full sanction history. An
 * empty history renders the empty notice; a populated one renders a header with the count then one entry per
 * row, scoped to the right family (ban-family vs mute-family) and newest-first.
 */
class SanctionHistoryReviewTest {

    private static final Instant T0 = Instant.parse("2026-06-02T00:00:00Z");

    private FakeSanctionHistory history;
    private CapturingNotifier notifier;
    private PlayerRef staff;
    private PlayerRef target;

    @BeforeEach
    void setUp() {
        history = new FakeSanctionHistory();
        notifier = new CapturingNotifier();
        staff = new PlayerRef(UUID.randomUUID(), "Staff");
        target = new PlayerRef(UUID.randomUUID(), "Griefer");
    }

    @Test
    void banHistoryWithNoRowsRendersEmpty() {
        new ReviewBanHistory(history, notifier.notifier()).review(staff, target);

        assertThat(notifier.keys).containsExactly("moderation.banhistory.empty");
    }

    @Test
    void banHistoryRendersHeaderAndEntryPerRowNewestFirst() {
        append(SanctionAction.BAN, T0, Optional.of("grief"));
        append(SanctionAction.UNBAN, T0.plusSeconds(60), Optional.empty());
        // A mute-family row for the same target must not bleed into the ban review.
        append(SanctionAction.MUTE, T0.plusSeconds(30), Optional.of("spam"));

        new ReviewBanHistory(history, notifier.notifier()).review(staff, target);

        assertThat(notifier.keys)
                .containsExactly(
                        "moderation.banhistory.header", "moderation.banhistory.entry", "moderation.banhistory.entry");
    }

    @Test
    void muteHistoryWithNoRowsRendersEmpty() {
        new ReviewMuteHistory(history, notifier.notifier()).review(staff, target);

        assertThat(notifier.keys).containsExactly("moderation.mutehistory.empty");
    }

    @Test
    void muteHistoryRendersHeaderAndEntryPerRow() {
        append(SanctionAction.MUTE, T0, Optional.of("spam"));
        append(SanctionAction.UNMUTE, T0.plusSeconds(60), Optional.empty());
        // A ban-family row for the same target must not bleed into the mute review.
        append(SanctionAction.BAN, T0.plusSeconds(30), Optional.of("grief"));

        new ReviewMuteHistory(history, notifier.notifier()).review(staff, target);

        assertThat(notifier.keys)
                .containsExactly(
                        "moderation.mutehistory.header",
                        "moderation.mutehistory.entry",
                        "moderation.mutehistory.entry");
    }

    private void append(SanctionAction action, Instant at, Optional<String> reason) {
        history.append(new SanctionHistoryEntry(
                action, target.uuid(), Issuer.console("Staff"), reason, at, Optional.empty(), Optional.empty()));
    }

    private static final class CapturingNotifier {
        private final List<String> keys = new ArrayList<>();

        ModerationNotifier notifier() {
            return new ModerationNotifier(new RecordingMessages(keys), new NoopSink());
        }
    }

    private static final class RecordingMessages implements Messages {
        private final List<String> keys;

        RecordingMessages(List<String> keys) {
            this.keys = keys;
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}
