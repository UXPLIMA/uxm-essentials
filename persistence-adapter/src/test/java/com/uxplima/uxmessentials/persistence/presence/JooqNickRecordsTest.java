package com.uxplima.uxmessentials.persistence.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.presence.domain.NickState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A nick is kept per player, found by name in any case, and an older write never replaces a newer nick. */
@EveryBackend
class JooqNickRecordsTest {

    private static final UUID ALEX = new UUID(0L, 1L);
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half. */
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");

    private JooqNickRecords nicks;

    @BeforeEach
    void open(TestDatabase database) {
        nicks = new JooqNickRecords(database.dsl());
    }

    @Test
    void aNickIsKeptAndClearedAndAPlayerWithoutOneReadsNone() {
        assertThat(nicks.read(ALEX)).isEqualTo(NickState.NONE);

        nicks.write(ALEX, new NickState("Captain", 100L));
        assertThat(nicks.read(ALEX)).isEqualTo(new NickState("Captain", 100L));

        nicks.write(ALEX, new NickState("", 200L));
        assertThat(nicks.read(ALEX).value()).isEmpty();
        assertThat(nicks.holders("captain")).isEmpty();
    }

    @Test
    void anOlderWriteDoesNotReplaceANewerNick() {
        nicks.write(ALEX, new NickState("Newer", 200L));

        nicks.write(ALEX, new NickState("Older", 100L));

        assertThat(nicks.read(ALEX)).isEqualTo(new NickState("Newer", 200L));
    }

    @Test
    void theHoldersOfANickAreFoundInAnyCaseABedrockNameToo() {
        nicks.write(ALEX, new NickState("Captain", 100L));
        nicks.write(BEDROCK, new NickState(".BedrockGamer", 100L));

        assertThat(nicks.holders("CAPTAIN")).containsExactly(ALEX);
        assertThat(nicks.holders(".bedrockgamer")).containsExactly(BEDROCK);
        assertThat(nicks.holders("nobody")).isEmpty();
    }
}
