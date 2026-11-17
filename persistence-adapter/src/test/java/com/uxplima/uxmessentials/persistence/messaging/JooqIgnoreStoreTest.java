package com.uxplima.uxmessentials.persistence.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.UUID;

import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.persistence.lookup.JooqPlayerNameRepository;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** An ignore list read back names each ignored player as they last joined, on any backend. */
@EveryBackend
class JooqIgnoreStoreTest {

    private static final PlayerRef OWNER = new PlayerRef(new UUID(0L, 1L), "Owner");
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half, the name prefixed with a dot. */
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");

    private static final UUID NEVER_SEEN = new UUID(0L, 7L);

    private JooqIgnoreStore ignores;
    private JooqPlayerNameRepository names;

    @BeforeEach
    void open(TestDatabase database) {
        ignores = new JooqIgnoreStore(database.dsl());
        names = new JooqPlayerNameRepository(database.dsl());
    }

    @Test
    void anIgnoredPlayerIsListedUnderTheNameTheyLastJoinedWith() {
        ignores.ignore(OWNER, new PlayerRef(BEDROCK, ".OldName"), IgnoreScope.MESSAGES);
        names.upsert(new PlayerName(BEDROCK, ".BedrockGamer123", 100L));

        assertThat(ignores.load(OWNER).entries())
                .extracting(IgnoreEntry::ignored)
                .extracting(PlayerRef::uuid, PlayerRef::name)
                .containsExactly(tuple(BEDROCK, ".BedrockGamer123"));
    }

    @Test
    void anIgnoredPlayerNoServerHasSeenKeepsTheUuidAsTheName() {
        ignores.ignore(OWNER, new PlayerRef(NEVER_SEEN, "Ghost"), IgnoreScope.ALL);

        assertThat(ignores.load(OWNER).entries())
                .extracting(entry -> entry.ignored().name())
                .containsExactly(NEVER_SEEN.toString());
    }
}
