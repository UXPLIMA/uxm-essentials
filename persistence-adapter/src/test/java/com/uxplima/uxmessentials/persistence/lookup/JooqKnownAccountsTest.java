package com.uxplima.uxmessentials.persistence.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** An account is known once any server of the database recorded it joining, under whatever name. */
@EveryBackend
class JooqKnownAccountsTest {

    private static final UUID ALEX = new UUID(0L, 1L);
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half. */
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");

    private JooqPlayerNameRepository names;
    private JooqKnownAccounts known;

    @BeforeEach
    void open(TestDatabase database) {
        names = new JooqPlayerNameRepository(database.dsl());
        known = new JooqKnownAccounts(database.dsl());
    }

    @Test
    void anAccountARecordedJoinOrTheServersListAddedIsKnownAndANewOneIsNot() {
        names.upsert(new PlayerName(ALEX, "Alex", 100L));
        names.addMissing(List.of(new PlayerName(BEDROCK, ".BedrockGamer123", 100L)));

        assertThat(known.known(ALEX)).isTrue();
        assertThat(known.known(BEDROCK)).isTrue();
        assertThat(known.known(new UUID(0L, 2L))).isFalse();
    }

    @Test
    void anOfflineModeAccountIsKnownByItsOwnIdNotByAnotherSpellingsId() {
        UUID lower = UUID.nameUUIDFromBytes("OfflinePlayer:alex".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID upper = UUID.nameUUIDFromBytes("OfflinePlayer:Alex".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        names.upsert(new PlayerName(upper, "Alex", 100L));

        assertThat(known.known(upper)).isTrue();
        assertThat(known.known(lower)).isFalse();
    }
}
