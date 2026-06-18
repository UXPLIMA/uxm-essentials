package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The built-in per-viewer tokens a hologram line may carry, resolved at render with no PlaceholderAPI:
 * {@code {player}} (the viewer's name), {@code {page}} (their current page, 1-based) and {@code {pages}} (the
 * hologram's page count). Plain literal substitution, so a line may freely combine them with MiniMessage tags and
 * {@code %papi%} placeholders. Because {@code {player}} and {@code {page}} differ per viewer, a line carrying any
 * of these tokens makes the hologram render per viewer through the text-override path.
 */
@NullMarked
final class HologramTokens {

    private static final String PLAYER = "{player}";
    private static final String PAGE = "{page}";
    private static final String PAGES = "{pages}";

    private HologramTokens() {}

    /** Whether {@code source} carries any built-in token, so the hologram must render per viewer. */
    static boolean hasToken(String source) {
        return source.contains(PLAYER) || source.contains(PAGE) || source.contains(PAGES);
    }

    /** Substitute the built-in tokens for one viewer; {@code page} is 1-based, {@code pages} the page count. */
    static String resolve(String source, String player, int page, int pages) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(player, "player");
        // {pages} is replaced before {page} so the shorter token never clips the longer one (they do not
        // overlap as literals, but the order keeps the intent explicit).
        return source.replace(PLAYER, player)
                .replace(PAGES, Integer.toString(pages))
                .replace(PAGE, Integer.toString(page));
    }
}
