package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The IP bans a STRICT {@code /ban} adds are in the history, one row per address it banned, each naming the player;
 * an address whose own ban already runs at least as long keeps that ban and gets no row.
 */
class StrictBanHistoryTest {

    private static final String HOME = "198.51.100.9";
    private static final String SCHOOL = "203.0.113.4";

    private final MutableClock clock = new MutableClock(NOW);
    private final SanctionRecords records = new SanctionRecords();
    private final FakePermissions permissions = new FakePermissions();
    private final RecordedHistory history = new RecordedHistory();
    private final Ban ban = new Ban(
            records,
            new OnlinePlayers(),
            new ModerationGuard(permissions),
            new RecordingNotifier().notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(history, clock),
            new SanctionDurationLimit(permissions),
            (key, placeholders) -> {},
            new SanctionSync() {
                @Override
                public void banChanged(PlayerRef target) {}

                @Override
                public void muteChanged(PlayerRef target) {}

                @Override
                public void ipBanChanged(String ip) {}
            },
            AddressStrictness.STRICT,
            new SamsAddresses(),
            clock);

    @Test
    void eachAddressAStrictBanBansIsRecordedForThePlayer() {
        ban.ban(ALEX, SAM, Optional.of("cheating"), false);

        assertThat(ipRows()).extracting(row -> row.ip().orElseThrow()).containsExactlyInAnyOrder(HOME, SCHOOL);
        assertThat(ipRows()).allSatisfy(row -> {
            assertThat(row.action()).isEqualTo(SanctionAction.BAN);
            assertThat(row.target()).isEqualTo(SAM.uuid());
            assertThat(row.actor().uuid()).contains(ALEX.uuid());
            assertThat(row.reason()).contains("cheating");
            assertThat(row.expiry()).isEmpty();
        });
    }

    @Test
    void anAddressThatKeepsItsOwnLongerBanIsNotRecorded() {
        records.saveIpBan(
                new IpBan(SCHOOL, Optional.empty(), Optional.of("alts"), Optional.empty(), Issuer.of(ALEX), NOW));

        ban.ban(ALEX, SAM, Optional.of("cheating"), false);

        assertThat(ipRows()).extracting(row -> row.ip().orElseThrow()).containsExactly(HOME);
    }

    @Test
    void aCappedStrictBanRecordsTheAddressesWithTheCappedEnd() {
        permissions.quota(
                EnforcementPermissions.maxDuration("ban"),
                QuotaResult.limited(Duration.ofDays(1).toSeconds()));

        ban.ban(ALEX, SAM, Optional.empty(), false);

        assertThat(ipRows())
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.expiry()).contains(NOW.plus(Duration.ofDays(1))));
    }

    private List<SanctionHistoryEntry> ipRows() {
        return history.entries().stream().filter(row -> row.ip().isPresent()).toList();
    }

    /** An IP history that knows the two addresses Sam connected from. */
    private static final class SamsAddresses implements IpHistoryStore {

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
            return account.equals(SAM.uuid()) ? Set.of(HOME, SCHOOL) : Set.of();
        }
    }
}
