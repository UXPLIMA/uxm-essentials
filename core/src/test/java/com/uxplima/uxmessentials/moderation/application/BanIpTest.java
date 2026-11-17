package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.RelayAddresses;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * An IP ban removes everyone on the address at once, here and on every other server, and never cuts short an IP ban
 * already running on the address.
 */
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
    private final FakePermissions permissions = new FakePermissions();
    private RelayAddresses relays = RelayAddresses.LOOPBACK;

    private BanIp banIp() {
        return new BanIp(
                repository,
                online,
                new ModerationGuard(permissions),
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
                new ModerationGuard(permissions),
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
    void anExemptHolderAndTheStaffMemberOnTheAddressStayOnline() {
        permissions.grant(SAM, ModerationGuard.EXEMPT_NODE);
        online.connect(STAFF, BANNED);

        banIp().banIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "", Optional.empty());

        assertThat(online.kicked()).containsOnlyKeys(ALEX);
        assertThat(online.onlinePlayers()).containsExactlyInAnyOrder(SAM, ROBIN, STAFF);
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

    @Test
    void aTimedIpBanOnAPermanentlyBannedAddressIsRefusedAndThePermanentBanStays() {
        banIp().banIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "", Optional.of("griefing"));

        assertThat(tempBanIp()
                        .tempBanIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "1d", Optional.empty())
                        .errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);

        assertThat(repository.loadIpBan(BANNED)).hasValueSatisfying(ban -> {
            assertThat(ban.until()).isEmpty();
            assertThat(ban.reason()).contains("griefing");
        });
        assertThat(notifier.sent())
                .last()
                .isEqualTo(new RecordingNotifier.Sent(
                        STAFF, ModerationMessageKey.BANIP_ALREADY_PERMANENT.key(), Map.of("ip", BANNED)));
        assertThat(peers.ipBans).containsExactly(BANNED);
    }

    @Test
    void aShorterIpBanIsRefusedWithTheTimeTheRunningOneStillHas() {
        tempBanIp().tempBanIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "7d", Optional.empty());

        assertThat(banIp().banIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "1d", Optional.empty())
                        .errorOrThrow())
                .isEqualTo(ModerationError.LONGER_BAN_RUNNING);

        assertThat(repository.loadIpBan(BANNED).flatMap(IpBan::until))
                .contains(CLOCK.instant().plus(Duration.ofDays(7)));
        assertThat(notifier.sent())
                .last()
                .isEqualTo(new RecordingNotifier.Sent(
                        STAFF,
                        ModerationMessageKey.BANIP_ALREADY_LONGER.key(),
                        Map.of("ip", BANNED, "remaining", "1w")));
    }

    @Test
    void aPermanentIpBanReplacesATimedOne() {
        tempBanIp().tempBanIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "1h", Optional.empty());

        assertThat(banIp().banIp(STAFF, new BanIp.Target(BANNED, Optional.empty()), "", Optional.of("alts"))
                        .isErr())
                .isFalse();

        assertThat(repository.loadIpBan(BANNED)).hasValueSatisfying(ban -> {
            assertThat(ban.until()).isEmpty();
            assertThat(ban.reason()).contains("alts");
        });
    }

    @Test
    void anAddressEveryPlayerBehindGeyserSharesIsNeverBannedForAPlayerOrTyped() {
        String geyser = "198.51.100.2";
        relays = RelayAddresses.of(List.of(geyser), new QuietLog());

        assertThat(banIp().banIp(STAFF, new BanIp.Target(geyser, Optional.of(ROBIN.uuid())), "", Optional.empty())
                        .errorOrThrow())
                .isEqualTo(ModerationError.SHARED_ADDRESS);
        assertThat(tempBanIp()
                        .tempBanIp(STAFF, new BanIp.Target("127.0.0.1", Optional.empty()), "1h", Optional.empty())
                        .errorOrThrow())
                .isEqualTo(ModerationError.SHARED_ADDRESS);

        assertThat(repository.loadIpBan(geyser)).isEmpty();
        assertThat(repository.loadIpBan("127.0.0.1")).isEmpty();
        assertThat(online.kicked()).isEmpty();
        assertThat(peers.ipBans).isEmpty();
        assertThat(notifier.sent())
                .containsExactly(
                        new RecordingNotifier.Sent(
                                STAFF, ModerationMessageKey.BANIP_SHARED_ADDRESS.key(), Map.of("ip", geyser)),
                        new RecordingNotifier.Sent(
                                STAFF, ModerationMessageKey.BANIP_SHARED_ADDRESS.key(), Map.of("ip", "127.0.0.1")));
    }

    private IpAlts noAlts() {
        return new IpAlts(new NoIpHistory(), address -> address, relays);
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
}
