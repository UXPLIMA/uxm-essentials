package com.uxplima.uxmessentials.persistence.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A server's own list of players tops up the name index without overwriting what joins recorded. */
@EveryBackend
class JooqPlayerNameRepositoryTest {

    private static final UUID ALEX = new UUID(0L, 1L);
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half, the name prefixed with a dot. */
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");

    private JooqPlayerNameRepository names;

    @BeforeEach
    void open(TestDatabase database) {
        names = new JooqPlayerNameRepository(database.dsl());
    }

    @Test
    void theMissingPlayersAreAddedABedrockOneTooAndARecordedNameIsKept() {
        names.upsert(new PlayerName(ALEX, "Alex", 200L));

        int added = names.addMissing(List.of(
                new PlayerName(ALEX, "OldAlex", 100L),
                new PlayerName(BEDROCK, ".BedrockGamer123", 100L),
                new PlayerName(new UUID(0L, 3L), "a_name_longer_than_sixteen", 100L)));

        assertThat(added).isEqualTo(1);
        assertThat(names.count()).isEqualTo(2);
        assertThat(names.loadRecent(10))
                .extracting(PlayerName::name)
                .containsExactlyInAnyOrder("Alex", ".BedrockGamer123");
    }
}
