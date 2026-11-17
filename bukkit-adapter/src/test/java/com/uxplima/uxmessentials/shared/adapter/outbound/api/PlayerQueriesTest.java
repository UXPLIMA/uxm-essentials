package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmPlayer;
import com.uxplima.uxmessentials.api.view.UxmPlayerMatch;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * A consumer asking for a name gets the answer a command would act on: the account, every case variant on an
 * offline-mode server, or nobody. The lookup runs on a worker, where it may read the name store.
 */
class PlayerQueriesTest {

    private static final PlayerRef STEVE =
            new PlayerRef(UUID.fromString("5627dd98-e6be-3c21-b8a8-e92344183641"), "Steve");
    private static final PlayerRef STEVE_LOWER =
            new PlayerRef(UUID.fromString("b3b1a8f8-6c5e-3b4c-8a3a-2b6f1c0e9d11"), "steve");
    private static final PlayerRef BEDROCK =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"), ".Gamer");

    private final Worker worker = new Worker();
    private final Names names = new Names();
    private final PlayerQueries queries = new PlayerQueries(names, worker);

    @Test
    void aNameIsLookedUpOnAWorkerAndAnswersTheAccountItNames() {
        names.answers.put(".Gamer", NameMatch.found(BEDROCK));

        CompletableFuture<UxmPlayerMatch> answer = queries.byName(" .Gamer ");

        assertThat(answer).isNotDone();
        worker.runAll();
        assertThat(answer.join().player()).contains(new UxmPlayer(BEDROCK.uuid(), ".Gamer"));
    }

    @Test
    void aNameSeveralAccountsHoldOnlyInCaseAnswersAllOfThem() {
        names.answers.put("STEVE", NameMatch.ambiguous("STEVE", List.of(STEVE, STEVE_LOWER)));

        CompletableFuture<UxmPlayerMatch> answer = queries.byName("STEVE");
        worker.runAll();

        assertThat(answer.join()).isInstanceOfSatisfying(UxmPlayerMatch.Ambiguous.class, several -> {
            assertThat(several.name()).isEqualTo("STEVE");
            assertThat(several.candidates())
                    .containsExactly(new UxmPlayer(STEVE.uuid(), "Steve"), new UxmPlayer(STEVE_LOWER.uuid(), "steve"));
        });
        assertThat(answer.join().player()).isEmpty();
    }

    @Test
    void aNameNobodyPlayedUnderAnswersUnknown() {
        CompletableFuture<UxmPlayerMatch> answer = queries.byName("Nobody");
        worker.runAll();

        assertThat(answer.join()).isInstanceOf(UxmPlayerMatch.Unknown.class);
        assertThat(answer.join().candidates()).isEmpty();
    }

    @Test
    void aBlankNameIsUnknownWithoutAskingTheLookup() {
        assertThat(queries.byName("  ").join()).isInstanceOf(UxmPlayerMatch.Unknown.class);
        assertThat(worker.queued).isEmpty();
        assertThat(names.asked).isEmpty();
    }

    /** The name lookup a command uses, answering what each name was set to and nobody otherwise. */
    private static final class Names implements PlayerLookup {
        final Map<String, NameMatch> answers = new HashMap<>();
        final List<String> asked = new ArrayList<>();

        @Override
        public NameMatch matchByName(String name) {
            asked.add(name);
            return answers.getOrDefault(name, NameMatch.unknown());
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** A worker lane that holds its tasks until the test runs them, so where the lookup ran is visible. */
    private static final class Worker implements Scheduler {
        final List<Runnable> queued = new ArrayList<>();

        void runAll() {
            List<Runnable> now = List.copyOf(queued);
            queued.clear();
            now.forEach(Runnable::run);
        }

        @Override
        public void async(Runnable task) {
            queued.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("a name lookup waits for nothing");
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException("a name lookup never needs the global region");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException("a name lookup never needs a region");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException("a name lookup never needs a player's thread");
        }
    }
}
