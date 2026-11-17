package com.uxplima.uxmessentials.persistence.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.presence.application.port.NickWrite;
import com.uxplima.uxmessentials.presence.domain.NickState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A nick is kept per player and held by one player at most in any case; a write over a row that moved on is refused,
 * and of players claiming one nick at once exactly one gets it.
 */
@EveryBackend
class JooqNickRecordsTest {

    private static final UUID ALEX = new UUID(0L, 1L);
    private static final UUID STEVE = new UUID(0L, 2L);
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

        assertThat(nicks.write(ALEX, 0L, "Captain")).isEqualTo(new NickWrite.Written(new NickState("Captain", 1L)));
        assertThat(nicks.read(ALEX)).isEqualTo(new NickState("Captain", 1L));

        assertThat(nicks.write(ALEX, 1L, "")).isInstanceOf(NickWrite.Written.class);
        assertThat(nicks.read(ALEX)).isEqualTo(new NickState("", 2L));
        assertThat(nicks.holders("captain")).isEmpty();
    }

    @Test
    void aWriteOverAVersionTheRowMovedOnFromIsRefused() {
        nicks.write(ALEX, 0L, "First");
        nicks.write(ALEX, 1L, "Second");

        assertThat(nicks.write(ALEX, 1L, "Late")).isInstanceOf(NickWrite.Stale.class);
        assertThat(nicks.write(ALEX, 0L, "Late")).isInstanceOf(NickWrite.Stale.class);
        assertThat(nicks.read(ALEX)).isEqualTo(new NickState("Second", 2L));
    }

    @Test
    void aNickAnotherPlayerHoldsInAnyCaseIsRefusedAndClearedRowsLiveSideBySide() {
        nicks.write(ALEX, 0L, "Captain");

        assertThat(nicks.write(STEVE, 0L, "CAPTAIN")).isInstanceOf(NickWrite.Taken.class);
        assertThat(nicks.read(STEVE)).isEqualTo(NickState.NONE);

        nicks.write(STEVE, 0L, "Mate");
        assertThat(nicks.write(STEVE, 1L, "captain")).isInstanceOf(NickWrite.Taken.class);
        nicks.write(ALEX, 1L, "");
        assertThat(nicks.write(STEVE, 1L, "")).isInstanceOf(NickWrite.Written.class);
        assertThat(nicks.write(STEVE, 2L, "captain")).isInstanceOf(NickWrite.Written.class);
    }

    @Test
    void theHoldersOfANickAreFoundInAnyCaseABedrockNameToo() {
        nicks.write(ALEX, 0L, "Captain");
        nicks.write(BEDROCK, 0L, ".BedrockGamer");

        assertThat(nicks.holders("CAPTAIN")).containsExactly(ALEX);
        assertThat(nicks.holders(".bedrockgamer")).containsExactly(BEDROCK);
        assertThat(nicks.holders("nobody")).isEmpty();
    }

    @Test
    void ofPlayersClaimingOneNickAtOnceExactlyOneGetsIt() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<NickWrite>> claims = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UUID player = new UUID(1L, i);
            String spelling = i % 2 == 0 ? "Foo" : "fOO";
            claims.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return nicks.write(player, 0L, spelling);
            }));
        }
        start.countDown();

        int written = 0;
        int taken = 0;
        for (CompletableFuture<NickWrite> claim : claims) {
            NickWrite outcome = claim.get(30, TimeUnit.SECONDS);
            if (outcome instanceof NickWrite.Written) {
                written++;
            } else if (outcome instanceof NickWrite.Taken) {
                taken++;
            }
        }

        assertThat(written).isEqualTo(1);
        assertThat(taken).isEqualTo(7);
        assertThat(nicks.holders("foo")).hasSize(1);
    }
}
