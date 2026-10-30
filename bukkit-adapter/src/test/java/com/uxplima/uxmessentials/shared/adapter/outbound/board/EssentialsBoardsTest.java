package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.BoardCountingPlaytime;
import com.uxplima.uxmessentials.playerstate.application.PlaytimeFormat;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmlib.board.Board;
import com.uxplima.uxmlib.board.BoardPeriod;
import com.uxplima.uxmlib.board.BoardSettings;
import com.uxplima.uxmlib.board.BoardText;
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
