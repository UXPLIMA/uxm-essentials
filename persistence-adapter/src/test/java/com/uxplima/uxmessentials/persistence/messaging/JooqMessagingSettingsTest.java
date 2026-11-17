package com.uxplima.uxmessentials.persistence.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/** The players a spy watches and each player's reply partner, through the real migrations. */
@EveryBackend
class JooqMessagingSettingsTest {

    private static final UUID MOD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a3"), "Sam");
    private static final Instant EARLY = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant LATE = Instant.parse("2030-01-01T00:00:05Z");

    @Test
    void addingAndRemovingWatchedPlayersChangesOnlyThoseRows(TestDatabase database) {
        JooqSpyTargetStore store = new JooqSpyTargetStore(database.dsl());
        store.change(MOD, Set.of(ALEX.uuid()), Set.of());
        new JooqSpyTargetStore(database.dsl()).change(MOD, Set.of(SAM.uuid(), ALEX.uuid()), Set.of());

        store.change(MOD, Set.of(), Set.of(ALEX.uuid()));

        assertThat(new JooqSpyTargetStore(database.dsl()).load(MOD)).containsExactly(SAM.uuid());
    }

    @Test
    void theLastPartnerReplacesTheOneBeforeAndKeepsWhenItWasTouched(TestDatabase database) {
        JooqReplyPartnerStore store = new JooqReplyPartnerStore(database.dsl());
        store.save(MOD, LastConversation.with(ALEX, EARLY));
        store.save(MOD, LastConversation.with(SAM, LATE));

        assertThat(new JooqReplyPartnerStore(database.dsl()).load(MOD)).contains(LastConversation.with(SAM, LATE));
        assertThat(store.load(ALEX.uuid())).isEmpty();
    }

    @Test
    void anOlderConversationWrittenLateKeepsTheNewerOne(TestDatabase database) {
        JooqReplyPartnerStore store = new JooqReplyPartnerStore(database.dsl());
        store.save(MOD, LastConversation.with(SAM, LATE));

        store.save(MOD, LastConversation.with(ALEX, EARLY));

        assertThat(store.load(MOD)).contains(LastConversation.with(SAM, LATE));
    }
}
