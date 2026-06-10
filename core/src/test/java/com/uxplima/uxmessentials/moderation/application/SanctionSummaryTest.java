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
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /sanction}: an aggregated read-only summary. A clean target renders the header then the four
 * "none" lines and a zero warning count; a sanctioned target renders the matching "active" lines and the
 * count of warnings still in effect. The use case mutates nothing and resolves every line through a
 * {@link com.uxplima.uxmessentials.shared.application.message.MessageKey}.
 */
class SanctionSummaryTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Griefer");

    private FakeModerationRepository repo;
    private CapturingNotifier notifier;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repo = new FakeModerationRepository();
        notifier = new CapturingNotifier();
        clock = Clock.fixed(T0, ZoneOffset.UTC);
    }

    @Test
    void aCleanTargetRendersTheHeaderEveryNoneLineAndZeroWarnings() {
        new SanctionSummary(repo, notifier.notifier(), clock).summarize(STAFF, TARGET);

        assertThat(notifier.keys)
                .containsExactly(
                        "moderation.sanction.header",
                        "moderation.sanction.mute-none",
                        "moderation.sanction.jail-none",
                        "moderation.sanction.ban-none",
                        "moderation.sanction.warns");
        assertThat(notifier.last("moderation.sanction.warns")).containsEntry("count", "0");
    }

    @Test
    void aSanctionedTargetRendersTheActiveLinesAndTheWarningCount() {
        repo.saveMute(TARGET, MuteState.permanent(Issuer.console("Staff"), Optional.of("spam"), T0));
        repo.saveJail(TARGET, JailState.permanent("cells", Issuer.console("Staff"), Optional.of("grief"), T0));
        repo.saveTempban(
                TARGET,
                TempbanState.active(T0.plus(Duration.ofHours(2)), Issuer.console("Staff"), Optional.empty(), T0));
        repo.appendWarn(TARGET, new Warn(Issuer.console("Staff"), Optional.of("first"), T0, Optional.empty()));

        new SanctionSummary(repo, notifier.notifier(), clock).summarize(STAFF, TARGET);

        assertThat(notifier.keys)
                .containsExactly(
                        "moderation.sanction.header",
                        "moderation.sanction.mute-active",
                        "moderation.sanction.jail-active",
                        "moderation.sanction.ban-active",
                        "moderation.sanction.warns");
        assertThat(notifier.last("moderation.sanction.jail-active")).containsEntry("jail", "cells");
        assertThat(notifier.last("moderation.sanction.warns")).containsEntry("count", "1");
    }

    private static final class CapturingNotifier {
        private final List<String> keys = new ArrayList<>();
        private final List<Map<String, String>> placeholders = new ArrayList<>();

        ModerationNotifier notifier() {
            return new ModerationNotifier(new RecordingMessages(keys, placeholders), new NoopSink());
        }

        Map<String, String> last(String key) {
            for (int i = keys.size() - 1; i >= 0; i--) {
                if (keys.get(i).equals(key)) {
                    return placeholders.get(i);
                }
            }
            throw new AssertionError("no rendered line for " + key);
        }
    }

    private record RecordingMessages(List<String> keys, List<Map<String, String>> placeholders) implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> ph) {
            keys.add(key.key());
            placeholders.add(ph);
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}
