package com.uxplima.uxmessentials.persistence.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import org.junit.jupiter.api.Test;

/** The switches a player sets, through the real migrations, as every backend of one database reads them. */
@EveryBackend
class JooqPlayerSwitchStoreTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Test
    void aSwitchSetOnOneBackendIsReadOnAnotherAndSettingItAgainReplacesIt(TestDatabase database) {
        new JooqPlayerSwitchStore(database.dsl()).save(ALEX, Map.of("messages", false, "replies", true));
        JooqPlayerSwitchStore other = new JooqPlayerSwitchStore(database.dsl());
        other.save(ALEX, Map.of("messages", true));

        assertThat(other.load(ALEX, Set.of("messages", "replies")))
                .isEqualTo(Map.of("messages", true, "replies", true));
        assertThat(other.load(SAM, Set.of("messages"))).isEmpty();
    }

    @Test
    void onlyTheNamedSwitchesAreRead(TestDatabase database) {
        JooqPlayerSwitchStore store = new JooqPlayerSwitchStore(database.dsl());
        store.save(ALEX, Map.of("messages", false, "broadcasts", false));

        assertThat(store.load(ALEX, Set.of("broadcasts"))).isEqualTo(Map.of("broadcasts", false));
    }
}
