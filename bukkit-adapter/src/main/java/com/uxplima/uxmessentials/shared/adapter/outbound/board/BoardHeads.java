package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.board.BoardAnswers;
import org.jspecify.annotations.Nullable;

/**
 * The board heads as MiniMessage tags, for a line drawn as a component: {@code <uxm_essentials_head>} is the reader's
 * own head and {@code <uxm_essentials_top_<board>_<period>_<n>_head>} the player at a place. Each is the
 * PlaceholderAPI key of the same head, written as a tag.
 *
 * <p>A tag puts the head itself in the line, so it can draw a skin the plugin keeps for an offline-mode or Bedrock
 * player, which no {@code <head:...>} argument can name. Until the boards are built, and for a board, period or place
 * the plugin does not have, the tag is left as written.
 */
public final class BoardHeads {

    /** What every head tag starts with: the plugin's placeholder prefix. */
    public static final String PREFIX = "uxm_essentials_";

    private static final String OWN = "head";
    private static final String TOP = "top_";
    private static final String HEAD_FIELD = "_head";

    private final AtomicReference<@Nullable BoardAnswers> answers = new AtomicReference<>();

    /** Answer from {@code built} from now on. */
    void answerFrom(BoardAnswers built) {
        answers.set(Objects.requireNonNull(built, "built"));
    }

    /**
     * The head {@code request} asks for, without the prefix: {@code head} or {@code top_<board>_<period>_<n>_head}.
     * Null before the boards are built, for a request the boards do not answer, and for the reader's own head
     * without a reader.
     */
    public @Nullable Component head(@Nullable UUID reader, @Nullable String readerName, String request) {
        Objects.requireNonNull(request, "request");
        BoardAnswers current = answers.get();
        return current == null ? null : current.headComponent(reader, readerName, request);
    }

    /** The head tags as {@code reader} reads them; without a reader, {@code <uxm_essentials_head>} is left as written. */
    public TagResolver tags(@Nullable UUID reader, @Nullable String readerName) {
        return new HeadTags(this, reader, readerName);
    }

    /** Whether {@code source} may hold a head tag, so a parse kept by its text is not reused for it. */
    public static boolean mentions(String source) {
        return holds(source, "<" + PREFIX);
    }

    /** Whether {@code source} holds the reader's own head, which reads differently for each viewer. */
    public static boolean mentionsReader(String source) {
        return holds(source, "<" + PREFIX + OWN + ">");
    }

    /** Whether {@code source} holds {@code wanted} in any case, read in place since a HUD asks every second. */
    private static boolean holds(String source, String wanted) {
        for (int at = source.indexOf('<'); at >= 0; at = source.indexOf('<', at + 1)) {
            if (source.regionMatches(true, at, wanted, 0, wanted.length())) {
                return true;
            }
        }
        return false;
    }

    /** The request a tag name makes, or null when it is not a head tag. */
    private static @Nullable String requestOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(PREFIX)) {
            return null;
        }
        String request = lower.substring(PREFIX.length());
        boolean head = request.equals(OWN) || (request.startsWith(TOP) && request.endsWith(HEAD_FIELD));
        return head ? request : null;
    }

    /** The resolver one reader's line is parsed with. */
    private record HeadTags(
            BoardHeads heads,
            @Nullable UUID reader,
            @Nullable String readerName) implements TagResolver {

        @Override
        public @Nullable Tag resolve(String name, ArgumentQueue arguments, Context ctx) {
            String request = requestOf(name);
            if (request == null) {
                return null;
            }
            Component head = heads.head(reader, readerName, request);
            return head == null ? null : Tag.selfClosingInserting(head);
        }

        @Override
        public boolean has(String name) {
            return requestOf(name) != null;
        }
    }
}
