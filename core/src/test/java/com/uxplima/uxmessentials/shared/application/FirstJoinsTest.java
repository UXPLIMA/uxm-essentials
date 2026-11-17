package com.uxplima.uxmessentials.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.KnownAccounts;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.Test;

/** A player is greeted as new only when no server of the network saw them before, read during their login. */
class FirstJoinsTest {

    private static final UUID STEVE = UUID.randomUUID();
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half. */
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");

    private final Set<UUID> index = new HashSet<>();
    private boolean unreachable;
    private final KnownAccounts accounts = player -> {
        if (unreachable) {
            throw new IllegalStateException("database down");
        }
        return index.contains(player);
    };
    private final RecordingLogger log = new RecordingLogger();
    private final FirstJoins firstJoins = new FirstJoins(accounts, log);

    @Test
    void aPlayerNoServerSawBeforeIsNew() {
        firstJoins.loggingIn(STEVE);

        assertThat(firstJoins.firstJoin(STEVE, false)).isTrue();
    }

    @Test
    void aPlayerMovingToAServerTheyNeverPlayedOnIsNotNew() {
        index.add(STEVE);
        index.add(BEDROCK);
        firstJoins.loggingIn(STEVE);
        firstJoins.loggingIn(BEDROCK);

        assertThat(firstJoins.firstJoin(STEVE, false)).isFalse();
        assertThat(firstJoins.firstJoin(BEDROCK, false)).isFalse();
    }

    @Test
    void anOfflineModeCaseVariantIsItsOwnAccountAndNewWhenOnlyTheOtherSpellingIsKnown() {
        UUID upper = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
        UUID lower = UUID.nameUUIDFromBytes("OfflinePlayer:steve".getBytes(StandardCharsets.UTF_8));
        index.add(upper);
        firstJoins.loggingIn(lower);

        assertThat(firstJoins.firstJoin(lower, false)).isTrue();
    }

    @Test
    void aPlayerThisServerKnowsIsNeverNew() {
        firstJoins.loggingIn(STEVE);

        assertThat(firstJoins.firstJoin(STEVE, true)).isFalse();
    }

    @Test
    void anUnreadableIndexCountsThePlayerAsKnownAndSaysSo() {
        unreachable = true;
        firstJoins.loggingIn(STEVE);

        assertThat(firstJoins.firstJoin(STEVE, false)).isFalse();
        assertThat(log.warnings()).hasSize(1);
    }

    @Test
    void aPlayerWhoseLoginWasNotReadIsJudgedByThisServer() {
        index.add(STEVE);

        assertThat(firstJoins.firstJoin(STEVE, false)).isTrue();
        assertThat(firstJoins.firstJoin(STEVE, true)).isFalse();
    }

    @Test
    void whatWasReadIsLetGoWhenThePlayerLeaves() {
        index.add(STEVE);
        firstJoins.loggingIn(STEVE);
        firstJoins.forget(STEVE);

        assertThat(firstJoins.firstJoin(STEVE, false)).isTrue();
    }
}
