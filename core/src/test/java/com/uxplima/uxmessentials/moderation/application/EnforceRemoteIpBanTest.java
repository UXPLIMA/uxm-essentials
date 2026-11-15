package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** A server told that another server banned an address kicks its own players on it while the ban holds. */
class EnforceRemoteIpBanTest {

    private static final String BANNED = "203.0.113.7";

    private final InMemoryIpBans repository = new InMemoryIpBans();
    private final OnlinePlayers online =
            new OnlinePlayers().connect(ALEX, BANNED).connect(SAM, "198.51.100.2");
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final FakePermissions permissions = new FakePermissions();
    private final EnforceRemoteIpBan enforce =
            new EnforceRemoteIpBan(repository, online, new ModerationGuard(permissions), notifier.notifier(), CLOCK);

    @Test
    void thePlayersOnABannedAddressAreKickedWithTheBanScreen() {
        repository.saveIpBan(ban(Optional.of("alts")));

        enforce.onRemoteIpBan(BANNED);

        assertThat(online.kicked()).containsOnlyKeys(ALEX);
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        ALEX, ModerationMessageKey.BANIP_KICK.key(), Map.of("reason", "alts")));
    }

    @Test
    void anExemptHolderOnTheAddressStaysOnline() {
        permissions.grant(ALEX, ModerationGuard.EXEMPT_NODE);
        repository.saveIpBan(ban(Optional.empty()));

        enforce.onRemoteIpBan(BANNED);

        assertThat(online.kicked()).isEmpty();
    }

    @Test
    void anAddressUnbannedBeforeTheMessageArrivesKeepsItsPlayers() {
        repository.saveIpBan(ban(Optional.empty()));
        repository.removeIpBan(BANNED);

        enforce.onRemoteIpBan(BANNED);

        assertThat(online.kicked()).isEmpty();
    }

    private static IpBan ban(Optional<String> reason) {
        return new IpBan(BANNED, Optional.empty(), reason, Optional.empty(), Issuer.of(ALEX), NOW);
    }
}
