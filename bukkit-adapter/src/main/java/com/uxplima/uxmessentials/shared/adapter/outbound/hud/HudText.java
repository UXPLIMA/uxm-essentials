package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.CatalogLines;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The shared render transform for operator-authored HUD content (scoreboard sidebar lines, tablist header/footer):
 * the per-viewer PlaceholderAPI bridge ({@code %papi%} expansion, identity without PlaceholderAPI) followed by a
 * {@link MiniMessage} parse, the same two-step transform the message sink applies. Operator content may therefore
 * embed third-party placeholders. Every source string is raw operator MiniMessage, never a {@code MessageKey}, so
 * nothing here is parity-checked.
 *
 * <p>A line written as {@code @key name=value} names a catalogue entry instead, and reads in the viewer's language
 * ({@link CatalogLines}). The entry stands in for the line before anything else runs, so a surface with tokens of
 * its own calls {@link #lineFor} first and the entry's {@code {player}} or {@code %papi%} is read as if the operator
 * had written it on the line.
 */
@NullMarked
public final class HudText {

    /** The catalogue the operator's {@code @key} lines read from: none until the kernel is wired. */
    private static volatile CatalogLines catalogLines = CatalogLines.none();

    /** Each viewer's language: one answer for everybody until the kernel is wired. */
    private static volatile Function<UUID, Locale> languages = viewer -> Locale.ROOT;

    private HudText() {}

    /** Ask {@code resolver} for a viewer's language from now on. */
    public static void useLanguages(Function<UUID, Locale> resolver) {
        languages = Objects.requireNonNull(resolver, "resolver");
    }

    /** The language {@code viewer} reads, so a text drawn per viewer can tell when to draw it again. */
    public static Locale languageOf(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return languages.apply(viewer);
    }

    /** Read {@code @key} lines from {@code lines} from now on. */
    public static void useCatalogLines(CatalogLines lines) {
        catalogLines = Objects.requireNonNull(lines, "lines");
    }

    /** Whether {@code source} names a catalogue entry, so a surface that shares one text knows to draw it per viewer. */
    public static boolean isCatalogLine(String source) {
        return catalogLines.isCatalogLine(source);
    }

    /**
     * {@code source} in the server's own language when it names a catalogue entry, for a text drawn once for
     * everybody before each viewer's own copy reaches them.
     */
    public static String lineForEverybody(String source) {
        return lineFor(PlayerRef.SYSTEM_UUID, source);
    }

    /** {@code source} in {@code viewer}'s language when it names a catalogue entry, otherwise as written. */
    public static String lineFor(UUID viewer, String source) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(source, "source");
        return catalogLines.expand(new PlayerRef(viewer, ""), source);
    }

    /** Render {@code miniMessageSource} for {@code viewer}: expand its placeholders, then parse the MiniMessage. */
    public static Component render(UUID viewer, String miniMessageSource) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(miniMessageSource, "miniMessageSource");
        String expanded = PlaceholderApiSupport.messageBridge(viewer).apply(lineFor(viewer, miniMessageSource));
        return parse(expanded);
    }

    /** Parse already-expanded operator MiniMessage without running PlaceholderAPI a second time. */
    public static Component parse(String miniMessageSource) {
        Objects.requireNonNull(miniMessageSource, "miniMessageSource");
        return StyledText.render(miniMessageSource);
    }
}
