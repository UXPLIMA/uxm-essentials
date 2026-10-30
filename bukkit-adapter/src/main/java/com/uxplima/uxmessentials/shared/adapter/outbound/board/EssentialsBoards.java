package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.uxplima.uxmlib.board.Board;
import com.uxplima.uxmlib.board.BoardAnswers;
import com.uxplima.uxmlib.board.BoardPresence;
import com.uxplima.uxmlib.board.BoardRecorder;
import com.uxplima.uxmlib.board.BoardSettings;
import com.uxplima.uxmlib.board.BoardText;
import com.uxplima.uxmlib.board.Leaderboard;
import com.uxplima.uxmlib.board.SqlBoardStore;
import com.uxplima.uxmlib.board.Standings;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * The plugin's boards, gathered while the modules wire and built once they all have.
 *
 * <p>A module that ranks something defines its board here as it wires ({@link #define}, or {@link #held} for a
 * snapshot it keeps in memory) and reports its events through {@link #add}. An event reported before {@link #build}
 * has nothing to land on and is dropped, which only a module's own start could do. A server with no module that
 * defines a board builds nothing.
 */
public final class EssentialsBoards {

    /** What {@link #build} stands up, for the bootstrap to register and stop. */
    public record Built(Leaderboard leaderboard, BoardAnswers answers, BoardPresence presence, Standings standings) {}

    private final Map<String, Board> boards = new LinkedHashMap<>();
    private final Map<String, LongFunction<String>> shows = new LinkedHashMap<>();
    private final Map<String, HeldSnapshot> held = new LinkedHashMap<>();
    private final AtomicReference<BoardRecorder> recorder = new AtomicReference<>();

    /** Add a counter or a record, or a snapshot board that reads its own source, its score shown as a number. */
    public void define(Board board) {
        define(board, Long::toString);
    }

    /** Add a board whose score is shown through {@code show}: a time, a sum of money. */
    public void define(Board board, LongFunction<String> show) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(show, "show");
        if (boards.putIfAbsent(board.word(), board) != null) {
            throw new IllegalArgumentException("two modules define the board " + board.word());
        }
        shows.put(board.word(), show);
    }

    /**
     * Add a snapshot board read whole from {@code source} at every refresh and held in memory. {@code countedToo}
     * makes it a counter whose all-time board is that snapshot, so its periods count events from now on.
     *
     * @param source every player's score above zero; called on the async lane, may block
     */
    public void held(String word, Supplier<Map<UUID, Long>> source, boolean countedToo, LongFunction<String> show) {
        HeldSnapshot snapshot = new HeldSnapshot(source);
        held.put(word, snapshot);
        define(countedToo ? Board.counter(word).withAllTime(snapshot) : Board.snapshot(word, snapshot), show);
    }

    /** Count {@code amount} on the board named {@code word} for {@code player}. */
    public void add(UUID player, String word, long amount) {
        BoardRecorder current = recorder.get();
        Board board = boards.get(word);
        if (current != null && board != null) {
            current.add(player, board, amount);
        }
    }

    /** Move a held snapshot board's score for {@code player} at once, before the next refresh reads it. */
    public void moved(UUID player, String word, long score) {
        HeldSnapshot snapshot = held.get(word);
        if (snapshot != null) {
            snapshot.set(player, score);
        }
    }

    /** How each board shows its score, by word. */
    public Map<String, LongFunction<String>> shows() {
        return Map.copyOf(shows);
    }

    /** The words of the boards defined so far. */
    public List<String> words() {
        return new ArrayList<>(boards.keySet());
    }

    /**
     * Stand the boards up over the plugin's own database, or nothing when no module defined one.
     *
     * @param boardTable the board table's full name, prefix included; {@code namesTable} likewise
     * @param async where a write goes: the plugin's async lane
     * @param names the name of a player no row names; runs on the async lane and must not go to the network
     */
    public Optional<Built> build(
            Database database,
            String boardTable,
            String namesTable,
            Supplier<BoardSettings> settings,
            BoardText text,
            Executor async,
            Function<UUID, String> names,
            Logger logger) {
        if (boards.isEmpty()) {
            return Optional.empty();
        }
        Clock clock = Clock.systemUTC();
        SqlBoardStore store = new SqlBoardStore(database, boardTable, namesTable, logger);
        Leaderboard leaderboard = new Leaderboard(store, List.copyOf(boards.values()), names, settings, clock);
        Standings standings = new Standings(store, leaderboard);
        recorder.set(new BoardRecorder(store, leaderboard, standings, async, clock));
        BoardAnswers answers = new BoardAnswers(
                leaderboard, standings, text, () -> settings.get().emptyHead());
        return Optional.of(new Built(leaderboard, answers, new BoardPresence(standings, store), standings));
    }

    /** Stop counting: an event after this lands nowhere. */
    public void close() {
        recorder.set(null);
    }
}
