package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.BoardCountingPlaytime;
import com.uxplima.uxmessentials.playerstate.application.PlaytimeFormat;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.outbound.BoardCountingVotes;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmlib.board.Board;
import com.uxplima.uxmlib.board.BoardPeriod;
import com.uxplima.uxmlib.board.BoardSettings;
import com.uxplima.uxmlib.board.BoardStore;
import com.uxplima.uxmlib.board.BoardText;
import com.uxplima.uxmlib.board.Leaderboard;
import com.uxplima.uxmlib.board.Ranked;
import org.junit.jupiter.api.Test;

class EssentialsBoardsTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void aHeldBoardRanksWhatTheModuleHoldsAndMovesAtOnce() {
        HeldSnapshot prestige = new HeldSnapshot(() -> Map.of(ALEX, 2L, SAM, 5L));

        assertThat(prestige.top(10)).extracting(ranked -> ranked.player()).containsExactly(SAM, ALEX);
        assertThat(prestige.countByScore()).isEqualTo(Map.of(2L, 1, 5L, 1));
        assertThat(prestige.scoreOf(ALEX)).hasValue(2);

        prestige.set(ALEX, 3);
        assertThat(prestige.scoreOf(ALEX)).hasValue(3);
    }

    @Test
    void onlyActiveSecondsAreCountedOnThePlaytimeBoard() {
        List<Long> counted = new ArrayList<>();
        PlaytimeRepository ledger =
                new BoardCountingPlaytime(new NoLedger(), (player, seconds) -> counted.add(seconds));

        ledger.addSeconds(ALEX, LocalDate.EPOCH, 60, 0);
        ledger.addSeconds(ALEX, LocalDate.EPOCH, 0, 60);

        assertThat(counted).containsExactly(60L);
    }

    @Test
    void everyVoteASavedTallyAddsIsCountedOnTheVotesBoard() {
        Map<UUID, VoteTally> tallies = new HashMap<>();
        List<Long> counted = new ArrayList<>();
        VoteRepository votes = new BoardCountingVotes(storedTallies(tallies), (player, added) -> counted.add(added));
        PlayerRef alex = new PlayerRef(ALEX, "Alex");
        Instant now = Instant.parse("2026-10-28T12:00:00Z");

        votes.saveTotals(alex, VoteTally.empty().recordVote(now, ZoneOffset.UTC));
        votes.saveTotals(alex, votes.totalsOf(alex).recordVote(now, ZoneOffset.UTC));
        votes.saveTotals(alex, VoteTally.empty());

        assertThat(counted).as("a reset counts nothing").containsExactly(1L, 1L);
    }

    @Test
    void aBoardIsSeededOnceIntoItsOpenWindows() {
        Board votes = Board.counter("votes").withAllTime(new HeldSnapshot(Map::of));
        MemoryStore store = new MemoryStore();
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
        Leaderboard leaderboard =
                new Leaderboard(store, List.of(votes), UUID::toString, BoardSettings::defaults, clock);
        List<Set<BoardPeriod>> asked = new ArrayList<>();
        EssentialsBoards.Seed seed = periods -> {
            asked.add(periods);
            return Map.of(
                    ALEX, Map.of(BoardPeriod.DAILY, 2L, BoardPeriod.WEEKLY, 5L, BoardPeriod.MONTHLY, 0L),
                    SAM, Map.of(BoardPeriod.DAILY, 0L, BoardPeriod.WEEKLY, 0L, BoardPeriod.MONTHLY, 0L));
        };

        assertThat(EssentialsBoards.seed(store, leaderboard, votes, seed, 0L)).isEqualTo(1);
        assertThat(asked).containsExactly(Set.of(BoardPeriod.DAILY, BoardPeriod.WEEKLY, BoardPeriod.MONTHLY));
        long week = leaderboard.windowOf(BoardPeriod.WEEKLY);
        assertThat(store.top(votes, BoardPeriod.WEEKLY, week, 10)).containsExactly(new Ranked(ALEX, null, 5));
        assertThat(store.top(votes, BoardPeriod.MONTHLY, leaderboard.windowOf(BoardPeriod.MONTHLY), 10))
                .isEmpty();

        assertThat(EssentialsBoards.seed(store, leaderboard, votes, seed, 0L))
                .as("a board that already holds a row is not seeded again")
                .isZero();
        assertThat(store.top(votes, BoardPeriod.WEEKLY, week, 10)).containsExactly(new Ranked(ALEX, null, 5));
    }

    @Test
    void playtimeIsShownAsTimeAndEverythingElseAsANumber() {
        EssentialsBoardText text = new EssentialsBoardText(
                (reader, key, values) -> key.key() + new java.util.TreeMap<>(values),
                Map.of("playtime", seconds -> PlaytimeFormat.compact(java.time.Duration.ofSeconds(seconds))));

        assertThat(text.score(Board.counter("playtime"), 90_061, null)).isEqualTo("1d 1h 1m");
        assertThat(text.score(Board.counter("trades"), 7, null)).isEqualTo("7");
        assertThat(text.resets(null, new BoardText.Left.Days(2, 5))).isEqualTo("board.resets-days{days=2, hours=5}");
        assertThat(text.nobody(null)).isEqualTo("board.nobody{}");
    }

    @Test
    void theLeaderboardBlockIsReadOrDefaulted() {
        Map<String, Object> written = new HashMap<>();
        written.put("leaderboard.size", 5);
        written.put("leaderboard.periods", List.of("alltime", "weekly"));
        BoardSettings read = BoardConfig.read(new FixedConfig(written));

        assertThat(read.size()).isEqualTo(5);
        assertThat(read.periods()).containsExactlyInAnyOrder(BoardPeriod.ALLTIME, BoardPeriod.WEEKLY);
        assertThat(read.emptyHead()).isEqualTo(BoardSettings.STEVE);
        assertThat(BoardConfig.read(new FixedConfig(Map.of())).periods()).hasSize(4);

        assertThatThrownBy(() -> BoardConfig.read(new FixedConfig(Map.of("leaderboard.week-starts", "someday"))))
                .hasMessageContaining("leaderboard.week-starts");
    }

    @Test
    void twoModulesCannotDefineTheSameBoard() {
        EssentialsBoards boards = new EssentialsBoards();
        boards.define(Board.counter("trades"));

        assertThatThrownBy(() -> boards.define(Board.counter("trades"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(boards.words()).containsExactly("trades");
    }

    /** A vote repository that keeps only tallies. */
    private static VoteRepository storedTallies(Map<UUID, VoteTally> tallies) {
        return (VoteRepository) Proxy.newProxyInstance(
                VoteRepository.class.getClassLoader(),
                new Class<?>[] {VoteRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "totalsOf" -> tallies.getOrDefault(((PlayerRef) args[0]).uuid(), VoteTally.empty());
                    case "saveTotals" -> tallies.put(((PlayerRef) args[0]).uuid(), (VoteTally) args[1]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A board store in memory: counters summed per window. */
    private static final class MemoryStore implements BoardStore {

        private final Map<Slot, Map<UUID, Long>> scores = new HashMap<>();

        @Override
        public void add(UUID player, Board board, Map<BoardPeriod, Long> windows, long amount, long at) {
            windows.forEach((period, window) -> scores.computeIfAbsent(
                            new Slot(board.word(), period, window), slot -> new HashMap<>())
                    .merge(player, amount, Long::sum));
        }

        @Override
        public List<Ranked> top(Board board, BoardPeriod period, long window, int limit) {
            return scores.getOrDefault(new Slot(board.word(), period, window), Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                    .limit(limit)
                    .map(held -> new Ranked(held.getKey(), null, held.getValue()))
                    .toList();
        }

        @Override
        public Map<Long, Integer> countByScore(Board board, BoardPeriod period, long window) {
            return Map.of();
        }

        @Override
        public Map<Slot, Long> scoresOf(UUID player, Map<BoardPeriod, Long> windows) {
            return Map.of();
        }

        @Override
        public void forgetBefore(BoardPeriod period, long window) {}

        @Override
        public void erase(UUID player) {}

        @Override
        public void name(UUID player, String name) {}
    }

    /** A config holding a few keys, the rest left to the caller's fallback. */
    private record FixedConfig(Map<String, Object> written) implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return written.get(path) instanceof Boolean value ? value : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return written.get(path) instanceof String value ? value : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return written.get(path) instanceof Integer value ? value : fallback;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<String> getStringList(String path, List<String> fallback) {
            return written.get(path) instanceof List<?> value ? (List<String>) value : fallback;
        }
    }

    /** A ledger that keeps nothing. */
    private static final class NoLedger implements PlaytimeRepository {

        @Override
        public void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta) {}

        @Override
        public PlaytimeSummary summaryOf(UUID uuid, LocalDate today) {
            return PlaytimeSummary.empty();
        }

        @Override
        public Map<UUID, Long> activeTotals() {
            return Map.of();
        }

        @Override
        public void adjust(UUID uuid, long deltaSeconds) {}

        @Override
        public boolean imported(UUID uuid) {
            return false;
        }

        @Override
        public void markImported(UUID uuid) {}

        @Override
        public void reset(UUID uuid) {}

        @Override
        public void resetAll() {}
    }
}
