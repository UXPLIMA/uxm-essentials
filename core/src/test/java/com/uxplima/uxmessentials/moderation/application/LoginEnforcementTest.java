package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** A banned address is refused at login, except for an exempt holder, as the live kick spares them. */
class LoginEnforcementTest {

    private static final String BANNED = "203.0.113.7";

    private final InMemoryIpBans repository = new InMemoryIpBans();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final LoginEnforcement login = new LoginEnforcement(
            repository,
            new IpAlts(new NoIpHistory(), address -> address),
            notifier.notifier(),
            new SilentAudit(),
            CLOCK);

    @Test
    void aPlayerOnABannedAddressIsRefusedWithTheBanScreen() {
        repository.saveIpBan(
                new IpBan(BANNED, Optional.empty(), Optional.empty(), Optional.empty(), Issuer.of(ALEX), NOW));

        LoginEnforcement.Decision decision = login.evaluate(ALEX, BANNED, false, false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.kickReason()).contains(ModerationMessageKey.BANIP_KICK.key());
    }

    @Test
    void anExemptHolderOnABannedAddressIsLetIn() {
        repository.saveIpBan(
                new IpBan(BANNED, Optional.empty(), Optional.empty(), Optional.empty(), Issuer.of(ALEX), NOW));

        LoginEnforcement.Decision decision = login.evaluate(ALEX, BANNED, false, true);

        assertThat(decision.allowed()).isTrue();
    }
}
