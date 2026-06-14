package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * {@code /alts} now scans a target's full IP history, not only the current last-IP: an account that shared an
 * address the target used in the past — even if both have since connected from a different address — is still
 * surfaced. Backed by the broadened {@code ipHistory} + {@code altsByAnyIp} repository pair.
 */
class AltsHistoryTest {

    private static final Instant T0 = Instant.parse("2026-06-13T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "main");
    private static final PlayerRef ALT = new PlayerRef(UUID.randomUUID(), "smurf");

    private ListAlts listAlts(FakeModerationRepository repository, ModerationFakes.RecordingSink sink) {
        ModerationFakes.FixedPlayers players =
                new ModerationFakes.FixedPlayers(Map.of(TARGET.uuid(), TARGET, ALT.uuid(), ALT), Set.of());
        return new ListAlts(repository, players, ModerationFakes.recordingNotifier(sink));
    }

    @Test
    void altsFindsAnAccountSharingAHistoricalNotCurrentIp() {
        FakeModerationRepository repository = new FakeModerationRepository();
        // The target's current address differs from the alt's, but they shared an old address in history.
        repository.recordSeen(TARGET, Optional.of("203.0.113.99"), T0);
        repository.recordIpSeen(TARGET.uuid(), "203.0.113.99", T0);
        repository.recordIpSeen(TARGET.uuid(), "198.51.100.5", T0);
        repository.recordSeen(ALT, Optional.of("198.51.100.5"), T0);
        repository.recordIpSeen(ALT.uuid(), "198.51.100.5", T0);

        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        listAlts(repository, sink).list(ACTOR, TARGET);

        // The shared historical IP surfaces the alt — the current-IP-only lookup would have missed it.
        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_HEADER)).isTrue();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_ENTRY)).isTrue();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_NONE)).isFalse();
    }

    @Test
    void altsReportsNoneWhenNoAccountSharesAnyKnownIp() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.recordSeen(TARGET, Optional.of("203.0.113.99"), T0);
        repository.recordIpSeen(TARGET.uuid(), "203.0.113.99", T0);

        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        listAlts(repository, sink).list(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_NONE)).isTrue();
    }

    @Test
    void altsReportsNoIpWhenTargetHasNoKnownAddress() {
        FakeModerationRepository repository = new FakeModerationRepository();

        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        listAlts(repository, sink).list(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_NO_IP)).isTrue();
    }
}
