package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /modstats}: the fetch-aggregate-render use case. Over an in-memory history it proves the server-wide
 * view renders a header then one leaderboard entry per staff member (ordered), the per-staff view renders one
 * breakdown line, an empty result renders the matching empty notice, the windowed variants pick the windowed
 * catalog keys, and the {@code days} window (resolved through the fixed clock) filters the rows.
 */
class ReviewPunishmentStatsTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    private FakeSanctionHistory history;
    private CapturingNotifier notifier;
    private ReviewPunishmentStats review;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        history = new FakeSanctionHistory();
        notifier = new CapturingNotifier();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        review = new ReviewPunishmentStats(history, new PunishmentStats(), notifier.notifier(), clock);
        viewer = new PlayerRef(UUID.randomUUID(), "Admin");
    }

    @Test
    void serverWideRendersHeaderThenAnEntryPerStaff() {
        Issuer busy = staff("Busy");
        appendBy(busy, SanctionAction.BAN, NOW.minus(Duration.ofDays(1)));
        appendBy(busy, SanctionAction.MUTE, NOW.minus(Duration.ofDays(1)));
        appendBy(staff("Quiet"), SanctionAction.WARN, NOW.minus(Duration.ofDays(1)));

        review.showServerWide(viewer, OptionalInt.empty());

        assertThat(notifier.keys)
                .containsExactly("moderation.stats.header", "moderation.stats.entry", "moderation.stats.entry");
    }

    @Test
    void serverWideOnAnEmptyHistoryRendersTheEmptyNotice() {
        review.showServerWide(viewer, OptionalInt.empty());

        assertThat(notifier.keys).containsExactly("moderation.stats.empty");
    }

    @Test
    void serverWideWithADaysWindowUsesTheWindowedHeaderAndFiltersOldRows() {
        Issuer mod = staff("Mod");
        appendBy(mod, SanctionAction.BAN, NOW.minus(Duration.ofDays(30))); // outside a 7-day window
        appendBy(mod, SanctionAction.MUTE, NOW.minus(Duration.ofDays(1))); // inside

        review.showServerWide(viewer, OptionalInt.of(7));

        assertThat(notifier.keys).containsExactly("moderation.stats.header-window", "moderation.stats.entry");
        assertThat(notifier.lastPlaceholders).containsEntry("bans", "0").containsEntry("mutes", "1");
    }

    @Test
    void perStaffRendersTheStaffBreakdownLine() {
        PlayerRef mod = new PlayerRef(UUID.randomUUID(), "Mod");
        appendBy(Issuer.of(mod), SanctionAction.KICK, NOW.minus(Duration.ofDays(2)));

        review.showForStaff(viewer, mod, OptionalInt.empty());

        assertThat(notifier.keys).containsExactly("moderation.stats.staff");
        assertThat(notifier.lastPlaceholders).containsEntry("staff", "Mod").containsEntry("kicks", "1");
    }

    @Test
    void perStaffWithNoPunishmentsRendersTheStaffEmptyNotice() {
        PlayerRef mod = new PlayerRef(UUID.randomUUID(), "Mod");

        review.showForStaff(viewer, mod, OptionalInt.empty());

        assertThat(notifier.keys).containsExactly("moderation.stats.staff-empty");
    }

    @Test
    void perStaffWithADaysWindowUsesTheWindowedKey() {
        PlayerRef mod = new PlayerRef(UUID.randomUUID(), "Mod");
        appendBy(Issuer.of(mod), SanctionAction.BAN, NOW.minus(Duration.ofDays(1)));

        review.showForStaff(viewer, mod, OptionalInt.of(3));

        assertThat(notifier.keys).containsExactly("moderation.stats.staff-window");
        assertThat(notifier.lastPlaceholders).containsEntry("days", "3");
    }

    private static Issuer staff(String name) {
        return Issuer.stored(Optional.of(UUID.randomUUID()), name);
    }

    private void appendBy(Issuer actor, SanctionAction action, Instant at) {
        history.append(new SanctionHistoryEntry(
                action, UUID.randomUUID(), actor, Optional.empty(), at, Optional.empty(), Optional.empty()));
    }

    private static final class CapturingNotifier {
        private final List<String> keys = new ArrayList<>();
        private Map<String, String> lastPlaceholders = Map.of();

        Notifier notifier() {
            return new Notifier(new RecordingMessages(keys, this), new NoopSink());
        }
    }

    private static final class RecordingMessages implements Messages {
        private final List<String> keys;
        private final CapturingNotifier owner;

        RecordingMessages(List<String> keys, CapturingNotifier owner) {
            this.keys = keys;
            this.owner = owner;
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            owner.lastPlaceholders = placeholders;
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}
