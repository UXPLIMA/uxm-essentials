package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A text about one player, drawn for one reader: the line that sits by a name in the tab list or over a head.
 *
 * <p>Every other player reads such a line, so three things in it read differently for each of them. A line written as a
 * catalogue entry ({@code @custom.tab.name}) reads in the reader's language. The {@code {prefix}} and {@code {suffix}}
 * tokens are the player's own, and a rank written as a catalogue line ({@code @custom.rank.owner}) reads in the
 * reader's language too. A relational placeholder ({@code %rel_...%}) is filled for the reader and the player. A
 * surface asks {@link #dependsOnReader} once, draws one copy for everybody when it is false, and draws per reader when
 * it is true.
 */
@NullMarked
public final class ReaderText {

    /** The prefix and suffix tokens a line may show. */
    public static final String PREFIX_TOKEN = "{prefix}";

    public static final String SUFFIX_TOKEN = "{suffix}";

    /** How a relational placeholder starts: its value depends on who reads it. */
    private static final String RELATIONAL = "%rel_";

    /** A player's prefix and suffix as the permission plugin holds them, before any catalogue line is read. */
    public record Affix(String prefix, String suffix) {

        public static final Affix NONE = new Affix("", "");

        public Affix {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(suffix, "suffix");
        }
    }

    private final Predicate<String> isLine;
    private final BiFunction<UUID, String, String> lines;
    private final Function<UUID, Affix> affixes;
    private final BiFunction<UUID, UUID, UnaryOperator<String>> relational;

    /**
     * @param isLine whether a text names a catalogue line
     * @param lines a catalogue line in one reader's language, any other text as written
     * @param affixes a player's prefix and suffix
     * @param relational the filler of {@code %rel_...%} placeholders for a reader and the player a text is about
     */
    public ReaderText(
            Predicate<String> isLine,
            BiFunction<UUID, String, String> lines,
            Function<UUID, Affix> affixes,
            BiFunction<UUID, UUID, UnaryOperator<String>> relational) {
        this.isLine = Objects.requireNonNull(isLine, "isLine");
        this.lines = Objects.requireNonNull(lines, "lines");
        this.affixes = Objects.requireNonNull(affixes, "affixes");
        this.relational = Objects.requireNonNull(relational, "relational");
    }

    /**
     * The reader text every HUD surface of this plugin uses: the plugin's own catalogue in each reader's language,
     * {@code affixes} for the tokens, and PlaceholderAPI's relational placeholders when it is installed.
     */
    public static ReaderText forServer(Function<UUID, Affix> affixes) {
        return new ReaderText(
                HudText::isCatalogLine, HudText::lineFor, affixes, PlaceholderApiSupport::relationalBridge);
    }

    /** Whether {@code source} about {@code target} reads differently for different readers. */
    public boolean dependsOnReader(UUID target, String source) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        if (isLine.test(source) || source.contains(RELATIONAL)) {
            return true;
        }
        Affix affix = affixes.apply(target);
        return (source.contains(PREFIX_TOKEN) && isLine.test(affix.prefix()))
                || (source.contains(SUFFIX_TOKEN) && isLine.test(affix.suffix()));
    }

    /**
     * {@code source} about {@code target} as {@code reader} reads it: the catalogue line in their language, the
     * target's prefix and suffix filled, and every relational placeholder filled for the two. The server's own copy
     * ({@link PlayerRef#SYSTEM_UUID}) has no reader, so a relational value in it reads as the target would read it.
     */
    public String forReader(UUID target, String source, UUID reader) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reader, "reader");
        String line = lines.apply(reader, source);
        if (line.contains(PREFIX_TOKEN) || line.contains(SUFFIX_TOKEN)) {
            Affix affix = affixes.apply(target);
            line = line.replace(PREFIX_TOKEN, lines.apply(reader, affix.prefix()))
                    .replace(SUFFIX_TOKEN, lines.apply(reader, affix.suffix()));
        }
        UUID relationalReader = PlayerRef.SYSTEM_UUID.equals(reader) ? target : reader;
        return relational.apply(relationalReader, target).apply(line);
    }
}
