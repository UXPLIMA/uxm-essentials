package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * {@code /unbanip} lifts the ban stored under the canonical spelling of the address, and a ban stored before addresses
 * were kept canonical under the spelling it was banned with.
 */
class UnbanIpTest {

    private static final String CANONICAL = "2001:db8:0:0:0:0:0:7";
    private static final String TYPED = "2001:DB8::7";

    private final InMemoryIpBans bans = new InMemoryIpBans();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final UnbanIp unbanIp = new UnbanIp(
            bans, notifier.notifier(), new SilentAudit(), new SanctionHistoryRecorder(new DiscardedHistory(), CLOCK));

    private void banned(String ip) {
        bans.saveIpBan(
                new IpBan(ip, Optional.empty(), Optional.empty(), Optional.empty(), Issuer.of(ALEX), CLOCK.instant()));
    }

    @Test
    void aCanonicalBanIsLiftedWhateverSpellingWasTyped() {
        banned(CANONICAL);

        assertThat(unbanIp.unbanIp(ALEX, CANONICAL, TYPED).isOk()).isTrue();

        assertThat(bans.loadIpBan(CANONICAL)).isEmpty();
    }

    @Test
    void aBanStoredAsTypedBeforeCanonicalStorageIsLiftedByTypingItsSpelling() {
        banned(TYPED);

        assertThat(unbanIp.unbanIp(ALEX, CANONICAL, TYPED).isOk()).isTrue();

        assertThat(bans.loadIpBan(TYPED)).isEmpty();
        assertThat(notifier.sent())
                .contains(new RecordingNotifier.Sent(
                        ALEX, ModerationMessageKey.UNBANIP_APPLIED.key(), Map.of("ip", TYPED)));
    }

    @Test
    void anAddressBannedUnderNeitherSpellingIsRefused() {
        assertThat(unbanIp.unbanIp(ALEX, CANONICAL, TYPED).errorOrThrow()).isEqualTo(ModerationError.NOT_IP_BANNED);
    }
}
