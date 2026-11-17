package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.JailSync;
import com.uxplima.uxmessentials.moderation.application.port.NameHolders;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.event.ModerationEvent;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerMuted;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * On an offline-mode server a mute or a jail copied onto another spelling of a name stays with its original: lifting
 * the original lifts the copy, lifting the copy lifts that copy alone and for good, a longer mute reaches the copy, and
 * a copy never comes back to the account it came from.
 */
class CaseVariantCopiesTest {

    private static final String HOME = "198.51.100.4";
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef UPPER_STEVE = new PlayerRef(UUID.randomUUID(), "STEVE");
    private static final NameHolders HOLDERS = name -> List.of(STEVE, UPPER_STEVE);
    private static final MuteState STEVES_MUTE = MuteState.timed(
            NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.of("slurs"), NOW.minus(Duration.ofHours(1)));

    private final InMemoryIpBans repository = new InMemoryIpBans();
    private final InMemoryCaseCopies kept = new InMemoryCaseCopies(repository);
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordedHistory history = new RecordedHistory();
    private final SanctionHistoryRecorder recorder = new SanctionHistoryRecorder(history, CLOCK);
    private final OnlinePlayers online = new OnlinePlayers();
    private final List<ModerationEvent> delivered = new ArrayList<>();
    // The plugin hands every moderation event of this server to the copies, as the subscription does.
    private final DomainEventPublisher events = event -> {
        if (event instanceof ModerationEvent moderation) {
            delivered.add(moderation);
            copies().onEvent(moderation);
        }
    };
    private final CaseVariantCopies copies = new CaseVariantCopies(
            repository,
            kept,
            notifier.notifier(),
            recorder,
            online,
            SanctionSync.NONE,
            JailSync.NONE,
            events,
            new SilentAudit(),
            CLOCK);
    private final Unmute unmute =
            new Unmute(repository, notifier.notifier(), new SilentAudit(), events, recorder, SanctionSync.NONE, CLOCK);

    private CaseVariantCopies copies() {
        return copies;
    }

    @Test
    void unmutingTheOriginalLiftsTheCopyItMade() {
        repository.saveMute(STEVE, STEVES_MUTE);
        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.NEWER);
        assertThat(repository.loadMute(UPPER_STEVE).isActiveAt(NOW)).isTrue();

        unmute.unmute(ALEX, STEVE);

        assertThat(repository.loadMute(STEVE)).isEqualTo(MuteState.none());
        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(MuteState.none());
    }

    @Test
    void theCopyReadsAsInheritedInItsReasonAndInTheHistory() {
        repository.saveMute(STEVE, STEVES_MUTE);

        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.NEWER);

        MuteState copy = repository.loadMute(UPPER_STEVE);
        assertThat(copy).isInstanceOfSatisfying(MuteState.Timed.class, timed -> {
            assertThat(timed.until()).isEqualTo(NOW.plus(Duration.ofDays(1)));
            assertThat(timed.drawnReason())
                    .hasValueSatisfying(
                            drawn -> assertThat(drawn.line()).isEqualTo(ModerationMessageKey.MOD_CASE_VARIANT_REASON));
        });
        List<SanctionHistoryEntry> rows = history.entries();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().action()).isEqualTo(SanctionAction.MUTE);
        assertThat(rows.getFirst().target()).isEqualTo(UPPER_STEVE.uuid());
        assertThat(rows.getFirst().drawnReason())
                .hasValueSatisfying(drawn -> assertThat(drawn.args()).containsEntry("player", "Steve"));
    }

    @Test
    void unmutingTheCopyLeavesTheOriginalAndTheCopyIsNotMadeAgain() {
        repository.saveMute(STEVE, STEVES_MUTE);
        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.NEWER);

        unmute.unmute(ALEX, UPPER_STEVE);
        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.NEWER);

        assertThat(repository.loadMute(STEVE)).isEqualTo(STEVES_MUTE);
        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(MuteState.none());
    }

    @Test
    void onEverySpellingACopyNeverComesBackToTheAccountItCameFrom() {
        repository.saveMute(STEVE, STEVES_MUTE);
        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.ALL);
        // The original is lifted where no event reaches this server (an import, a server without the copies), so the
        // copy is still in force when Steve logs in again.
        repository.saveMute(STEVE, MuteState.none());

        logIn(STEVE, CaseVariantSanctions.Reach.ALL);

        assertThat(repository.loadMute(STEVE)).isEqualTo(MuteState.none());
    }

    @Test
    void onEverySpellingUnmutingTheOriginalLeavesNothingToComeBack() {
        repository.saveMute(STEVE, STEVES_MUTE);
        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.ALL);

        unmute.unmute(ALEX, STEVE);
        logIn(STEVE, CaseVariantSanctions.Reach.ALL);
        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.ALL);

        assertThat(repository.loadMute(STEVE)).isEqualTo(MuteState.none());
        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(MuteState.none());
    }

    @Test
    void aLongerMuteOfTheOriginalReachesTheCopy() {
        repository.saveMute(STEVE, STEVES_MUTE);
        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.NEWER);
        MuteState longer = MuteState.permanent(Issuer.of(ALEX), Optional.of("again"), NOW);
        repository.saveMute(STEVE, longer);

        events.publish(new PlayerMuted(STEVE, longer, NOW));

        assertThat(repository.loadMute(UPPER_STEVE)).isInstanceOf(MuteState.Permanent.class);
    }

    @Test
    void aCopyTheStoreCannotKeepLeavesNothingBehind() {
        repository.saveMute(STEVE, STEVES_MUTE);
        kept.goDown();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> logIn(UPPER_STEVE, CaseVariantSanctions.Reach.NEWER))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(MuteState.none());
        assertThat(kept.kept()).isEmpty();
        assertThat(history.entries()).isEmpty();
        assertThat(delivered).isEmpty();
    }

    @Test
    void aRefusedLoginIsGivenNoCopy() {
        repository.saveMute(STEVE, STEVES_MUTE);
        repository.saveTempban(
                STEVE,
                com.uxplima.uxmessentials.moderation.domain.TempbanState.active(
                        NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.of("cheating"), NOW));

        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.NEWER);

        assertThat(repository.loadMute(UPPER_STEVE)).isEqualTo(MuteState.none());
        assertThat(repository.seen(UPPER_STEVE)).isEmpty();
        assertThat(kept.kept()).isEmpty();
    }

    @Test
    void unjailingTheOriginalReleasesTheCopyItMade() {
        JailState.Active cell = (JailState.Active)
                JailState.permanent("cell", Issuer.of(ALEX), Optional.of("grief"), NOW.minus(Duration.ofHours(1)));
        repository.saveJail(STEVE, cell);
        logIn(UPPER_STEVE, CaseVariantSanctions.Reach.NEWER);
        assertThat(repository.loadJail(UPPER_STEVE)).isEqualTo(cell);
        Unjail unjail =
                new Unjail(repository, online, JailSync.NONE, notifier.notifier(), new SilentAudit(), events, CLOCK);

        unjail.unjail(ALEX, STEVE);

        assertThat(repository.loadJail(UPPER_STEVE)).isEqualTo(JailState.none());
        assertThat(online.released()).contains(UPPER_STEVE);
    }

    private void logIn(PlayerRef who, CaseVariantSanctions.Reach reach) {
        LoginEnforcement login = new LoginEnforcement(
                repository,
                new IpAlts(new NoIpHistory(), address -> address),
                notifier.notifier(),
                new SilentAudit(),
                CLOCK,
                new CaseVariantSanctions(repository, HOLDERS, reach, kept),
                copies::carryOver,
                false);
        Logins.check(login, who, HOME, false, false);
    }
}
