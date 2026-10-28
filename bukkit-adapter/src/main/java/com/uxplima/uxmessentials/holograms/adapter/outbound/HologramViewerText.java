package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import org.jspecify.annotations.NullMarked;

/**
 * A text hologram's words, as one viewer reads them: an {@code @key} line in their language, the {@code {player}},
 * {@code {page}} and {@code {pages}} tokens, their own PlaceholderAPI values, the server's global tags and the
 * animation frame, joined into one panel the way the hologram is drawn.
 *
 * <p>Resolving reads the viewer's live state, so it runs on the viewer's own thread. A hologram that carries none of
 * that (no token, no catalogue line, one page) reads the same for everybody, and {@link #hasPerViewerText} says so:
 * it is resolved once rather than once per viewer.
 */
@NullMarked
public final class HologramViewerText {

    private final Function<UUID, UnaryOperator<String>> bridgeFactory;
    private final ParsedText parsed;
    private final Supplier<TagResolver> globalTags;
    private final HologramPageState pageState;

    public HologramViewerText(
            Function<UUID, UnaryOperator<String>> bridgeFactory,
            MiniMessage miniMessage,
            Supplier<TagResolver> globalTags,
            HologramPageState pageState) {
        this.bridgeFactory = Objects.requireNonNull(bridgeFactory, "bridgeFactory");
        this.parsed = new ParsedText(Objects.requireNonNull(miniMessage, "miniMessage"));
        this.globalTags = Objects.requireNonNull(globalTags, "globalTags");
        this.pageState = Objects.requireNonNull(pageState, "pageState");
    }

    /**
     * Whether {@code hologram} reads differently from one viewer to the next: a {@link HologramType#TEXT} hologram
     * that has several pages, or a line with a {@code %...%} placeholder, a {@code {player}}, {@code {page}} or
     * {@code {pages}} token, or an {@code @key} catalogue line. Item, block, head and entity holograms draw their
     * model only, so a label line on one is never a per-viewer target.
     */
    boolean hasPerViewerText(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (hologram.type() != HologramType.TEXT) {
            return false;
        }
        if (hologram.isMultiPage()) {
            return true;
        }
        for (HologramLine line : hologram.lines()) {
            if (PlaceholderApiSupport.hasPlaceholder(line.value())
                    || HologramTokens.hasToken(line.value())
                    || HudText.isCatalogLine(line.value())) {
                return true;
            }
        }
        return false;
    }

    /** The page {@code viewer} is on, zero for a hologram of one page. */
    int pageOf(Player viewer, Hologram hologram) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(hologram, "hologram");
        return hologram.isMultiPage()
                ? pageState.currentPage(hologram.name().value(), viewer.getUniqueId(), hologram.pageCount())
                : 0;
    }

    /**
     * {@code hologram}'s lines for {@code viewer} at animation frame {@code phase}, joined with newlines. Their
     * current page is read for a hologram of several pages. Call it on the viewer's own thread.
     */
    Component textFor(Player viewer, Hologram hologram, int phase) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(hologram, "hologram");
        UUID uuid = viewer.getUniqueId();
        UnaryOperator<String> bridge = bridgeFactory.apply(uuid);
        TagResolver tags = globalTags.get();
        int pages = hologram.pageCount();
        int page = pageOf(viewer, hologram);
        List<HologramLine> lines = hologram.isMultiPage() ? hologram.pageLines(page) : hologram.lines();
        List<Component> resolved = new ArrayList<>(lines.size());
        for (HologramLine line : lines) {
            // An @key line is the catalogue entry in this viewer's language, and its own tokens resolve as written.
            String written = HudText.lineFor(uuid, line.value());
            String tokens = HologramTokens.resolve(written, viewer.getName(), page + 1, pages);
            String source = HologramAnimations.expand(bridge.apply(tokens), phase);
            resolved.add(parsed.parse(source, tags));
        }
        return Component.join(JoinConfiguration.newlines(), resolved);
    }

    /**
     * {@code hologram}'s lines as everybody reads them, at animation frame {@code phase}: for a hologram with no
     * per-viewer text, which is resolved once for all its viewers. {@code globalBridge} resolves the server-wide
     * placeholders.
     */
    Component sharedText(Hologram hologram, UnaryOperator<String> globalBridge, int phase) {
        Objects.requireNonNull(hologram, "hologram");
        Objects.requireNonNull(globalBridge, "globalBridge");
        TagResolver tags = globalTags.get();
        List<Component> resolved = new ArrayList<>(hologram.lines().size());
        for (HologramLine line : hologram.lines()) {
            String source = HologramAnimations.expand(globalBridge.apply(line.value()), phase);
            resolved.add(parsed.parse(source, tags));
        }
        return Component.join(JoinConfiguration.newlines(), resolved);
    }
}
