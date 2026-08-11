package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.shared.application.port.FakeIpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * {@code /alts} scans every address a target has used, not only their latest: an account that shared an address
 * the target used in the past, even if both have since connected from a different one, is still surfaced. It
 * reads the kernel IP-history store, the same rows security's {@code /ipalts} answers from, and matches on the
 * keyed tokens rather than the addresses themselves.
 */
class AltsHistoryTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "main");
    private static final PlayerRef ALT = new PlayerRef(UUID.randomUUID(), "smurf");

    private ListAlts listAlts(FakeIpHistoryStore store, ModerationFakes.RecordingSink sink) {
        ModerationFakes.FixedPlayers players =
                new ModerationFakes.FixedPlayers(Map.of(TARGET.uuid(), TARGET, ALT.uuid(), ALT), Set.of());
        return new ListAlts(store, players, ModerationFakes.recordingNotifier(sink));
    }

    @Test
    void altsFindsAnAccountSharingAHistoricalNotCurrentAddress() {
        FakeIpHistoryStore store = new FakeIpHistoryStore();
        // The target's current address differs from the alt's, but they shared an old one.
        store.seen(TARGET.uuid(), "token-current", "203.0.113.99");
        store.seen(TARGET.uuid(), "token-old", "198.51.100.5");
        store.seen(ALT.uuid(), "token-old", "198.51.100.5");

        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        listAlts(store, sink).list(ACTOR, TARGET);

        // The shared historical address surfaces the alt; a current-address-only lookup would have missed it.
        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_HEADER)).isTrue();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_ENTRY)).isTrue();
        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_NONE)).isFalse();
    }

    @Test
    void altsReportsNoneWhenNoAccountSharesAnyKnownAddress() {
        FakeIpHistoryStore store = new FakeIpHistoryStore();
        store.seen(TARGET.uuid(), "token-current", "203.0.113.99");

        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        listAlts(store, sink).list(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_NONE)).isTrue();
    }

    @Test
    void altsReportsNoIpWhenTargetHasNoKnownAddress() {
        FakeIpHistoryStore store = new FakeIpHistoryStore();

        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        listAlts(store, sink).list(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_NO_IP)).isTrue();
    }

    @Test
    void altsIsBlindToAnAddressOnlyAnotherServerWouldTokeniseTheSameWay() {
        // Two accounts on the same address always produce the same token on one server, and the store never sees
        // the address itself, so a differing token is a differing address as far as the lookup is concerned.
        FakeIpHistoryStore store = new FakeIpHistoryStore();
        store.seen(TARGET.uuid(), "token-a", "203.0.113.99");
        store.seen(ALT.uuid(), "token-b", "203.0.113.99");

        ModerationFakes.RecordingSink sink = new ModerationFakes.RecordingSink();
        listAlts(store, sink).list(ACTOR, TARGET);

        assertThat(sink.sent(ACTOR, ModerationMessageKey.ALTS_NONE)).isTrue();
    }
}
