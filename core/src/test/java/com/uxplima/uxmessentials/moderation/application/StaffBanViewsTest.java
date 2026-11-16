package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * What staff read about a ban: {@code /checkban}, {@code /banlist} and {@code /sanction} call a permanent ban permanent
 * rather than giving the far-future instant it is stored with, and still give a timed ban's end.
 */
class StaffBanViewsTest {

    private static final String PERMANENT = ModerationMessageKey.MOD_GUI_VALUE_PERMANENT.key();

    private final SanctionRecords records = new SanctionRecords();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final PlayerLookup players = new PlayerLookup() {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.of(SAM).filter(sam -> sam.uuid().equals(uuid));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    };
    private final CheckBan checkBan = new CheckBan(records, notifier.notifier(), CLOCK);
    private final ListBans listBans = new ListBans(records, players, notifier.notifier(), CLOCK);
    private final SanctionSummary summary = new SanctionSummary(records, notifier.notifier(), CLOCK);

    private void banSam(Instant until) {
        records.saveTempban(SAM, TempbanState.active(until, Issuer.of(ALEX), Optional.of("cheating"), NOW));
    }

    /** The value each line Alex read put in {@code placeholder}, among lines that carry one. */
    private List<String> toldAlex(String placeholder) {
        return notifier.sent().stream()
                .filter(line ->
                        line.viewer().equals(ALEX) && line.placeholders().containsKey(placeholder))
                .map(line -> line.placeholders().get(placeholder))
                .toList();
    }

    @Test
    void checkbanCallsAPermanentBanPermanent() {
        banSam(NOW.plus(Ban.PERMANENT_SPAN));

        checkBan.show(ALEX, SAM);

        assertThat(toldAlex("expiry")).containsExactly(PERMANENT);
    }

    @Test
    void checkbanStillGivesATimedBansEnd() {
        Instant end = NOW.plus(Duration.ofDays(3));
        banSam(end);

        checkBan.show(ALEX, SAM);

        assertThat(toldAlex("expiry")).containsExactly(end.toString());
    }

    @Test
    void banlistCallsAPermanentBanPermanent() {
        banSam(NOW.plus(Ban.PERMANENT_SPAN));

        listBans.list(ALEX);

        assertThat(toldAlex("expires")).containsExactly(PERMANENT);
    }

    @Test
    void banlistStillGivesATimedBansEnd() {
        Instant end = NOW.plus(Duration.ofHours(6));
        banSam(end);

        listBans.list(ALEX);

        assertThat(toldAlex("expires")).containsExactly(end.toString());
    }

    @Test
    void theSanctionSummaryCallsAPermanentBanPermanent() {
        banSam(NOW.plus(Ban.PERMANENT_SPAN));

        summary.summarize(ALEX, SAM);

        assertThat(notifier.sent().stream()
                        .filter(line -> line.key().equals(ModerationMessageKey.SANCTION_BAN_ACTIVE.key()))
                        .map(line -> line.placeholders().get("until")))
                .containsExactly(PERMANENT);
    }

    @Test
    void theSanctionSummaryStillGivesATimedBansEnd() {
        Instant end = NOW.plus(Duration.ofDays(1));
        banSam(end);

        summary.summarize(ALEX, SAM);

        assertThat(notifier.sent().stream()
                        .filter(line -> line.key().equals(ModerationMessageKey.SANCTION_BAN_ACTIVE.key()))
                        .map(line -> line.placeholders().get("until")))
                .containsExactly(end.toString());
    }
}
