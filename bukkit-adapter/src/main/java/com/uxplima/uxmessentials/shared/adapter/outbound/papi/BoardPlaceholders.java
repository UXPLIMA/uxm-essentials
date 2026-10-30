package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The boards in the grammar every UXM plugin shares, written in the conventions' Leaderboards section:
 * {@code top_<board>_<period>_<n>_name|value|head}, {@code rank_<board>_<period>}, {@code value_<board>_<period>} and
 * {@code resets_<period>}. A hologram line written for another plugin's board works for these.
 *
 * <p>The boards the modules define on uxmLib's shared board module (playtime, trades, prestige, votes and the money
 * boards) answer first, through {@link com.uxplima.uxmlib.board.BoardAnswers}, and so does {@code resets_<period>}.
 * One older board answers after them: {@code balance} ranks the default currency, for {@code alltime} only, since a
 * balance is what a player holds now rather than what they earned in a window. It reads the snapshot {@code /baltop}
 * already holds. The older {@code economy_baltop_*} and {@code votes_top_*} families keep answering beside these.
 *
 * <p>A place nobody holds still answers, in the reader's words: the name is the word for nobody, the value and a
 * missing rank the word for none, and the head the game's own Steve, so a {@code <head:...>} tag around it draws.
 */
final class BoardPlaceholders {

    /** What a {@code <head:...>} tag draws for a place nobody holds. */
    static final String EMPTY_HEAD = "entity/player/wide/steve";

    private static final String BALANCE = "balance";
    private static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /** One line of the reader's own catalogue, as plain text. */
    @FunctionalInterface
    interface Words {
        String of(PlayerRef reader, MessageKey key, Map<String, String> values);
    }

    private final PlaceholderContexts contexts;
    private final Words words;

    BoardPlaceholders(PlaceholderContexts contexts, Words words) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.words = Objects.requireNonNull(words, "words");
    }

    /**
     * Answer {@code key}, already without the {@code uxmessentials_} prefix and in lower case, or empty when it is
     * not a board placeholder: {@code rank_next} and the other rank keys of the ranks module fall through to them.
     */
    Optional<String> resolve(PlayerRef reader, String key) {
        Optional<String> shared = contexts.boardAnswers()
                .filter(answers -> answers.handles(key) && !key.equals("head"))
                .map(answers -> answers.answer(reader.uuid(), reader.name(), key));
        if (shared.isPresent()) {
            return shared;
        }
        List<String> parts = List.of(key.split("_", -1));
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return switch (parts.get(0)) {
            case "top" ->
                parts.size() == 5
                        ? top(reader, parts.get(1), parts.get(2), parts.get(3), parts.get(4))
                        : Optional.empty();
            case "rank" -> parts.size() == 3 ? rank(reader, parts.get(1), parts.get(2)) : Optional.empty();
            case "value" -> parts.size() == 3 ? value(reader, parts.get(1), parts.get(2)) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private Optional<String> top(PlayerRef reader, String board, String period, String placeWritten, String field) {
        OptionalInt place = placeOf(placeWritten);
        if (place.isEmpty() || !List.of("name", "value", "head").contains(field)) {
            return Optional.empty();
        }
        if (BALANCE.equals(board) && "alltime".equals(period)) {
            return contexts.economy()
                    .map(economy -> {
                        Optional<BaltopRow> row = economy.baltopRow(economy.defaultCurrency(), place.getAsInt());
                        return field(
                                reader,
                                field,
                                row.map(BaltopRow::owner),
                                row.map(held -> MoneyFormat.withSymbol(held.balance())));
                    })
                    .or(() -> Optional.of(PlaceholderResolver.EMPTY));
        }
        return Optional.empty();
    }

    private String field(PlayerRef reader, String field, Optional<PlayerRef> holder, Optional<String> shown) {
        return switch (field) {
            case "name" -> holder.map(PlayerRef::name).orElseGet(() -> word(reader, SharedMessageKey.BOARD_NOBODY));
            case "value" -> shown.orElseGet(() -> word(reader, SharedMessageKey.BOARD_NONE));
            default -> holder.map(BoardPlaceholders::headOf).orElse(EMPTY_HEAD);
        };
    }

    private Optional<String> rank(PlayerRef reader, String board, String period) {
        if (BALANCE.equals(board) && "alltime".equals(period)) {
            return Optional.of(contexts.economy()
                    .map(economy -> placeOrNone(reader, economy.baltopPosition(reader)))
                    .orElse(PlaceholderResolver.EMPTY));
        }
        return Optional.empty();
    }

    private Optional<String> value(PlayerRef reader, String board, String period) {
        if (BALANCE.equals(board) && "alltime".equals(period)) {
            return Optional.of(
                    contexts.economy().map(economy -> economy.formatted(reader)).orElse(PlaceholderResolver.EMPTY));
        }
        return Optional.empty();
    }

    private String placeOrNone(PlayerRef reader, OptionalInt place) {
        return place.isPresent() ? Integer.toString(place.getAsInt()) : word(reader, SharedMessageKey.BOARD_NONE);
    }

    private String word(PlayerRef reader, MessageKey key) {
        return words.of(reader, key, Map.of());
    }

    private static OptionalInt placeOf(String written) {
        try {
            int place = Integer.parseInt(written);
            return place >= 1 ? OptionalInt.of(place) : OptionalInt.empty();
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }

    /** The name when an account can have it, so the account's own skin is drawn; the id otherwise. */
    private static String headOf(PlayerRef player) {
        return ACCOUNT_NAME.matcher(player.name()).matches()
                ? player.name()
                : player.uuid().toString();
    }
}
