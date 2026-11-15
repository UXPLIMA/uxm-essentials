package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** An IP ban removes everyone on the address at once, here and on every other server. */
class BanIpTest {

    private static final PlayerRef STAFF =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000057a"), "Staff");
    private static final PlayerRef ROBIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000b0"), "Robin");
    private static final String BANNED = "203.0.113.7";

    private final InMemoryIpBans repository = new InMemoryIpBans();
    private final OnlinePlayers online =
            new OnlinePlayers().connect(ALEX, BANNED).connect(SAM, BANNED).connect(ROBIN, "198.51.100.2");
    private final Peers peers = new Peers();
    private final RecordingNotifier notifier = new RecordingNotifier();

    private BanIp banIp() {
        return new BanIp(
                repository,
                online,
                peers,
                noAlts(),
                notifier.notifier(),
                new SilentAudit(),
                new RecordingEvents(),
                new SanctionHistoryRecorder(new DiscardedHistory(), CLOCK),
                CLOCK);
    }

    private TempBanIp tempBanIp() {
        return new TempBanIp(
                repository,
                online,
                peers,
                noAlts(),
                notifier.notifier(),
                new SilentAudit(),
                new RecordingEvents(),
                new SanctionHistoryRecorder(new DiscardedHistory(), CLOCK),
                CLOCK);
    }

    @Test
    void aBanKicksEveryoneOnTheAddressWithTheBanScreenAndTellsThePeers() {
        banIp().banIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "", Optional.of("griefing"));

        assertThat(online.kicked()).containsOnlyKeys(ALEX, SAM).containsValue(ModerationMessageKey.BANIP_KICK.key());
        assertThat(notifier.sent())
                .contains(new RecordingNotifier.Sent(
                        ALEX, ModerationMessageKey.BANIP_KICK.key(), Map.of("reason", "griefing")));
        assertThat(online.onlinePlayers()).containsExactly(ROBIN);
        assertThat(peers.ipBans).containsExactly(BANNED);
    }

    @Test
    void aTimedBanKicksEveryoneOnTheAddressAndTellsThePeers() {
        tempBanIp().tempBanIp(STAFF, new BanIp.Target(BANNED, Optional.of(ALEX.uuid())), "1h", Optional.empty());

        assertThat(online.kicked()).containsOnlyKeys(ALEX, SAM);
        assertThat(peers.ipBans).containsExactly(BANNED);
    }

    @Test
    void aTimedBanWithoutADurationKicksNobody() {
        tempBanIp().tempBanIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "", Optional.empty());

        assertThat(online.kicked()).isEmpty();
        assertThat(peers.ipBans).isEmpty();
    }

    private static IpAlts noAlts() {
        return new IpAlts(new NoIpHistory(), address -> address);
    }

    /** The bus seam, keeping each address announced to the other servers. */
    private static final class Peers implements SanctionSync {

        private final List<String> ipBans = new ArrayList<>();

        @Override
        public void banChanged(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void muteChanged(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ipBanChanged(String ip) {
            ipBans.add(ip);
        }
    }

    private static final class NoIpHistory implements IpHistoryStore {

        @Override
        public void record(UUID account, String ipToken, @Nullable String address, Instant seenAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<UUID> accountsOnToken(String ipToken) {
            return Set.of();
        }

        @Override
        public List<IpAssociation> sharingTokenWith(UUID account) {
            return List.of();
        }

        @Override
        public Set<String> addressesOf(UUID account) {
            return Set.of();
        }
    }

    private static final class DiscardedHistory implements SanctionHistory {

        @Override
        public void append(SanctionHistoryEntry entry) {
            // The history is not what these tests look at.
        }

        @Override
        public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> recentForTarget(UUID target, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> allSince(Instant threshold, int limit) {
            return List.of();
        }
    }

    /** The audit log is not what these tests look at. */
    private static final class SilentAudit implements ModerationAudit {

        @Override
        public void muted(
                PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason) {}

        @Override
        public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void jailed(
                PlayerRef actor,
                PlayerRef target,
                String jail,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {}

        @Override
        public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void tempbanned(
                PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason) {}

        @Override
        public void unbanned(PlayerRef actor, PlayerRef target, boolean ok) {}

        @Override
        public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count) {}

        @Override
        public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {}

        @Override
        public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {}

        @Override
        public void ipBanned(
                PlayerRef actor,
                String targetIp,
                Optional<UUID> target,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {}

        @Override
        public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {}

        @Override
        public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {}

        @Override
        public void jailLocationDefined(PlayerRef actor, String jail) {}

        @Override
        public void jailLocationRemoved(PlayerRef actor, String jail) {}

        @Override
        public void lockdown(UUID actor, boolean enabled) {}
    }
}
