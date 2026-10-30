package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.playerstate.application.PlaytimeFormat;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmlib.board.Board;
import com.uxplima.uxmlib.board.BoardText;
import org.jspecify.annotations.Nullable;

/**
 * The words the plugin's boards answer with, from the reader's own catalogue: the shared {@code board.*} keys. A
 * board that counts time shows it the way {@code /playtime} does; every other score is a plain number.
 */
public final class EssentialsBoardText implements BoardText {

    /** One catalogue line as {@code reader} reads it, as plain text; {@code reader} null for no player. */
    @FunctionalInterface
    public interface Words {
        String of(@Nullable UUID reader, MessageKey key, Map<String, String> values);
    }

    private final Words words;
    private final Set<String> timeBoards;

    /** @param timeBoards the words of the boards whose score is a number of seconds */
    public EssentialsBoardText(Words words, Set<String> timeBoards) {
        this.words = Objects.requireNonNull(words, "words");
        this.timeBoards = Set.copyOf(Objects.requireNonNull(timeBoards, "timeBoards"));
    }

    @Override
    public String nobody(@Nullable UUID reader) {
        return words.of(reader, SharedMessageKey.BOARD_NOBODY, Map.of());
    }

    @Override
    public String none(@Nullable UUID reader) {
        return words.of(reader, SharedMessageKey.BOARD_NONE, Map.of());
    }

    @Override
    public String resets(@Nullable UUID reader, Left left) {
        return switch (left) {
            case Left.Days days ->
                line(reader, SharedMessageKey.BOARD_RESETS_DAYS, "days", days.days(), "hours", days.hours());
            case Left.Hours hours ->
                line(reader, SharedMessageKey.BOARD_RESETS_HOURS, "hours", hours.hours(), "minutes", hours.minutes());
            case Left.Minutes minutes ->
                words.of(
                        reader,
                        SharedMessageKey.BOARD_RESETS_MINUTES,
                        Map.of("minutes", Long.toString(minutes.minutes())));
        };
    }

    @Override
    public String score(Board board, long score, @Nullable UUID reader) {
        return timeBoards.contains(board.word())
                ? PlaytimeFormat.compact(Duration.ofSeconds(score))
                : Long.toString(score);
    }

    private String line(@Nullable UUID reader, MessageKey key, String first, long a, String second, long b) {
        return words.of(reader, key, Map.of(first, Long.toString(a), second, Long.toString(b)));
    }
}
