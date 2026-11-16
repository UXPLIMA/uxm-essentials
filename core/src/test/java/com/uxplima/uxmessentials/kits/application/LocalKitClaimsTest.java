package com.uxplima.uxmessentials.kits.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger;
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

    private final Ledger ledger = new Ledger();
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

    @Test
    void aResetThatLandsWhileTheLoginReadIsInFlightIsNotUndoneByTheJoin() {
        ledger.claimKit(STEVE.uuid(), "welcome", 1L);
        // The read has its rows when the reset comes in, and the reset's delete lands before the read is used.
        ledger.duringNextRead(() -> {
            claims.reset(STEVE, WELCOME);
            scheduler.runAll();
        });

        claims.load(STEVE.uuid());
        claims.joined(STEVE);

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
        assertThat(ledger.hasKit(STEVE.uuid(), "welcome")).isFalse();
    }

    @Test
    void aResetWhoseDeleteIsStillQueuedIsNotUndoneByAJoin() {
        ledger.claimKit(STEVE.uuid(), "welcome", 1L);
        ledger.duringNextRead(() -> claims.reset(STEVE, WELCOME));

        claims.load(STEVE.uuid());
        claims.joined(STEVE);

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
        scheduler.runAll();
        assertThat(ledger.hasKit(STEVE.uuid(), "welcome")).isFalse();
    }

    @Test
    void aResetWhoseDeleteFailsStaysResetOnThisServerAndTellsTheOperator() {
        ledger.claimKit(STEVE.uuid(), "welcome", 1L);
        ledger.claimKit(STEVE.uuid(), "starter", 1L);
        ledger.failReleases();

        claims.reset(STEVE, WELCOME);
        scheduler.runAll();
        claims.load(STEVE.uuid());
        claims.joined(STEVE);

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
        assertThat(claims.hasClaimed(STEVE, STARTER)).isTrue();
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("kit_claim_reset_failed"));
    }

    @Test
    void aResetOfEveryKitWhoseDeleteFailsKeepsEveryRecordedClaimOff() {
        ledger.claimKit(STEVE.uuid(), "welcome", 1L);
        ledger.failReleases();

        claims.resetAll(STEVE);
        scheduler.runAll();
        claims.load(STEVE.uuid());
        claims.joined(STEVE);

        assertThat(claims.hasClaimed(STEVE, WELCOME)).isFalse();
    }

    /** The claim table, which can run something in the middle of a read and refuse deletes. */
    private static final class Ledger implements PlayerClaimLedger {

        private final MemoryClaimLedger rows = new MemoryClaimLedger();
        private Runnable duringNextRead = () -> {};
        private boolean failReleases;

        void duringNextRead(Runnable action) {
            duringNextRead = action;
        }

        void failReleases() {
            failReleases = true;
        }

        void failNextLoads(int count) {
            rows.failNextLoads(count);
        }

        boolean hasKit(UUID player, String kit) {
            return rows.hasKit(player, kit);
        }

        @Override
        public Claims load(UUID player, long now) {
            return rows.load(player, now);
        }

        @Override
        public Set<String> kits(UUID player) {
            Set<String> read = rows.kits(player);
            Runnable action = duringNextRead;
            duringNextRead = () -> {};
            action.run();
            return read;
        }

        @Override
        public boolean claimKit(UUID player, String kit, long claimedAt) {
            return rows.claimKit(player, kit, claimedAt);
        }

        @Override
        public void releaseKit(UUID player, String kit) {
            if (failReleases) {
                throw new IllegalStateException("the database is unreachable");
            }
            rows.releaseKit(player, kit);
        }

        @Override
        public void releaseAllKits(UUID player) {
            if (failReleases) {
                throw new IllegalStateException("the database is unreachable");
            }
            rows.releaseAllKits(player);
        }

        @Override
        public void saveCooldown(UUID player, String scope, long readyAt) {
            rows.saveCooldown(player, scope, readyAt);
        }
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
