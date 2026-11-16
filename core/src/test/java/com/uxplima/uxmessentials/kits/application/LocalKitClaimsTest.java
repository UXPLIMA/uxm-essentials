package com.uxplima.uxmessentials.kits.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.MemoryClaimLedger;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.Test;

/**
 * A single server keeps one-time claims in each player's own stamps; a claim the database records for a player (an
 * import's) reaches those stamps when the player joins, and a reset takes it out of both.
 */
class LocalKitClaimsTest {

    private static final PlayerRef STEVE =
            new PlayerRef(UUID.fromString("5d1f0c1e-3b4a-4f7e-9a51-2a8c6f0e7b10"), "Steve");
    private static final KitId WELCOME = KitId.of("welcome");
    private static final KitId STARTER = KitId.of("starter");

    private final MemoryClaimLedger ledger = new MemoryClaimLedger();
    private final Stamps stamps = new Stamps();
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final RecordingLogger log = new RecordingLogger();
    private final LocalKitClaims claims = new LocalKitClaims(stamps, ledger, scheduler, log);

    @Test
    void aClaimTheDatabaseRecordsIsClaimedOnceThePlayerHasJoined() {
        ledger.claimKit(STEVE.uuid(), "welcome", 1L);

        claims.load(STEVE.uuid());
        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
        claims.joined(STEVE);

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isTrue();
        assertThat(claims.hasClaimed(STEVE, STARTER)).isFalse();
    }

    @Test
    void aClaimOfThisServerStaysInTheStampsAndNeverReachesTheDatabase() {
        claims.markClaimed(STEVE, WELCOME);
        scheduler.runAll();

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isTrue();
        assertThat(ledger.hasKit(STEVE.uuid(), "welcome")).isFalse();
    }

    @Test
    void aResetTakesTheClaimOutOfTheDatabaseSoTheNextJoinDoesNotBringItBack() {
        ledger.claimKit(STEVE.uuid(), "welcome", 1L);
        claims.load(STEVE.uuid());
        claims.joined(STEVE);

        claims.reset(STEVE, WELCOME);
        scheduler.runAll();
        claims.load(STEVE.uuid());
        claims.joined(STEVE);

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
        assertThat(ledger.hasKit(STEVE.uuid(), "welcome")).isFalse();
    }

    @Test
    void aResetBetweenTheLoginReadAndTheJoinIsNotUndoneByTheJoin() {
        ledger.claimKit(STEVE.uuid(), "welcome", 1L);
        ledger.claimKit(STEVE.uuid(), "starter", 1L);
        claims.load(STEVE.uuid());

        claims.resetAll(STEVE);
        claims.joined(STEVE);
        scheduler.runAll();

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
        assertThat(claims.hasClaimed(STEVE, STARTER)).isFalse();
        assertThat(ledger.hasKit(STEVE.uuid(), "welcome")).isFalse();
        assertThat(ledger.hasKit(STEVE.uuid(), "starter")).isFalse();
    }

    @Test
    void aPlayerAlreadyOnlineIsGivenTheRecordedClaimsOnTheirOwnThread() {
        ledger.claimKit(STEVE.uuid(), "welcome", 1L);

        claims.adopt(STEVE);
        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
        scheduler.runAll();

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isTrue();
    }

    @Test
    void aClaimRecordedWhileThePlayerIsOnlineIsStampedAtOnce() {
        claims.stamp(STEVE, WELCOME);
        scheduler.runAll();

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isTrue();
    }

    @Test
    void anUnreachableDatabaseAtLoginLeavesThePlayerWithTheirStampsAndSaysSo() {
        claims.markClaimed(STEVE, STARTER);
        ledger.failNextLoads(1);

        claims.load(STEVE.uuid());
        claims.joined(STEVE);

        assertThat(claims.hasClaimed(STEVE, STARTER)).isTrue();
        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
        assertThat(log.warnings()).isNotEmpty();
    }

    /** A player's own stamps, standing in for PDC. */
    private static final class Stamps implements KitClaimStore {

        private final Set<String> claimed = new HashSet<>();

        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return claimed.contains(who.uuid() + "/" + kit.value());
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {
            claimed.add(who.uuid() + "/" + kit.value());
        }

        @Override
        public void reset(PlayerRef who, KitId kit) {
            claimed.remove(who.uuid() + "/" + kit.value());
        }

        @Override
        public void resetAll(PlayerRef who) {
            claimed.removeIf(key -> key.startsWith(who.uuid() + "/"));
        }
    }
}
