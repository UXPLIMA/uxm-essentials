package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.NameHolders;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerTempbanned;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * On an offline-mode server a ban lands on the other spellings of the banned name that are connected at that moment
 * too, on the server that issued it and on every server the ban frame reaches, as far as the ban reaches.
 */
class CaseVariantBanKickTest {

    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final PlayerRef UPPER_STEVE = new PlayerRef(UUID.randomUUID(), "STEVE");
    private static final PlayerRef LOWER_STEVE = new PlayerRef(UUID.randomUUID(), "steve");
    private static final NameHolders HOLDERS = name -> List.of(STEVE, UPPER_STEVE, LOWER_STEVE);
    private static final TempbanState.Active BAN = new TempbanState.Active(
            NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.of("cheating"), NOW.minus(Duration.ofMinutes(5)));

    private final InMemoryIpBans repository = new InMemoryIpBans();
    private final OnlinePlayers online =
            new OnlinePlayers().connect(UPPER_STEVE, "198.51.100.4").connect(LOWER_STEVE, "198.51.100.9");
    private final RecordingNotifier notifier = new RecordingNotifier();

    @Test
    void aBanIssuedHereRemovesTheConnectedSpellingFirstSeenAfterIt() {
        // STEVE first joined a minute ago, after the ban; steve has played under its own spelling for a month.
        firstSeen(UPPER_STEVE, NOW.minus(Duration.ofMinutes(1)));
        firstSeen(LOWER_STEVE, NOW.minus(Duration.ofDays(30)));

        enforce(CaseVariantSanctions.Reach.NEWER).onBanned(new PlayerTempbanned(STEVE, BAN, NOW));

        assertThat(online.kicked()).containsOnlyKeys(UPPER_STEVE);
    }

    @Test
    void aBanFromAnotherServerRemovesEverySpellingConnectedHereWhenTheServerSaysSo() {
        firstSeen(STEVE, NOW.minus(Duration.ofDays(60)));
        firstSeen(LOWER_STEVE, NOW.minus(Duration.ofDays(30)));
        repository.saveTempban(STEVE, BAN);

        enforce(CaseVariantSanctions.Reach.ALL).onRemoteBan(STEVE.uuid());

        assertThat(online.kicked()).containsOnlyKeys(UPPER_STEVE, LOWER_STEVE);
    }

    @Test
    void withAuthenticatedLoginsNoOtherSpellingIsTouched() {
        repository.saveTempban(STEVE, BAN);
        EnforceRemoteBan enforce = new EnforceRemoteBan(
                repository,
                online,
                notifier.notifier(),
                CLOCK,
                new CaseVariantSanctions(repository, NameHolders.NONE, CaseVariantSanctions.Reach.ALL));

        enforce.onBanned(new PlayerTempbanned(STEVE, BAN, NOW));

        assertThat(online.kicked()).isEmpty();
    }

    private EnforceRemoteBan enforce(CaseVariantSanctions.Reach reach) {
        return new EnforceRemoteBan(
                repository, online, notifier.notifier(), CLOCK, new CaseVariantSanctions(repository, HOLDERS, reach));
    }

    private void firstSeen(PlayerRef account, java.time.Instant at) {
        repository.recordSeen(SeenRecord.firstJoin(account, Optional.empty(), at));
    }
}
