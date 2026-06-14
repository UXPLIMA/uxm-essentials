package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.BuiltinTokens;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmessentials.tablist.domain.TablistFiller;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistFormatConfig;
import com.uxplima.uxmessentials.tablist.domain.TablistLayout;
import com.uxplima.uxmlib.hud.Tablist;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import org.jspecify.annotations.NullMarked;

/**
 * Renders the per-player tablist from the live {@link TablistFormatConfig}, dogfooding uxmLib's {@link Tablist}. Each
 * viewer is offered the format {@link TablistFormatConfig#select selected} for them: the highest-priority
 * {@link TablistFormat} whose {@link com.uxplima.uxmessentials.shared.display.DisplayCondition condition} matches, the
 * condition evaluated against a {@link ConditionContext} built from the live player (permission check, world, gamemode,
 * per-viewer PlaceholderAPI bridge) so a {@code %papi% >= 10} or {@code permission:uxmessentials.staff} condition sees
 * real values. When no format matches, the header/footer is cleared and any list name/order/skin/fillers this renderer
 * applied is reset to vanilla.
 *
 * <p>A selected format contributes four independent things:
 *
 * <ul>
 *   <li><strong>Header/footer.</strong> The {@link TablistContent} line lists, each source rendered through the shared
 *       pipeline ({@link AnimationRegistry} {@code %anim_<name>%} expansion → {@link BuiltinTokens} {@code {player}} /
 *       {@code {online}} / {@code {world}} → {@link HudText} PlaceholderAPI + MiniMessage) and joined with newlines. An
 *       <em>empty</em> header and footer (a name-only / order-only format) is left untouched rather than sent, because
 *       uxmLib's {@link Tablist#set} ships both in one native call and an empty pair would wipe whatever vanilla or
 *       another plugin set; {@link #appliedHeaderFooter} tracks who this renderer last sent one to so a switch away from
 *       a header-having format clears its own header/footer instead of leaving it stale.</li>
 *   <li><strong>Name, order, and skin.</strong> How the real player themselves appears in the tab — the list name, the
 *       sort order, and, when the format carries one, a custom-skin texture (the one thing native Paper cannot do, so it
 *       goes through a packet) — delegated to {@link RealPlayerRowPainter}, called from {@link #renderFor},
 *       {@link #clear}, {@link #forget}, and {@link #repaintSkinsFor}. The real players keep the early slots: the painter
 *       gives them the layout's {@link TablistLayout#realPlayerOrder() real-player order} (above every filler) unless the
 *       format authored an explicit sort order, which wins.</li>
 *   <li><strong>Filler grid.</strong> A {@link TablistLayout} of synthetic {@link TablistFiller} rows filling the cells
 *       the real players do not, delegated to {@link FillerPainter} (called from {@link #renderFor}, {@link #clear}, and
 *       {@link #forget}). Real-player suppression is deliberately not done; real players still show.</li>
 * </ul>
 *
 * <p>{@link #renderFor(Player)} touches the live player, so the caller must invoke it on the player's region/entity
 * thread — the render timer and the connection listener both hop there first.
 */
@NullMarked
public final class TablistRenderer {

    private final Tablist tablist;
    private final Supplier<TablistFormatConfig> formats;
    private final AnimationRegistry animations;

    /**
     * Whether this renderer currently has a header/footer applied for each player, keyed by player UUID. A {@code true}
     * value means the last selected format authored a header/footer and we sent it, so a switch to a name-only/order-only
     * format must clear it rather than leave it stale. An absent key means we never sent one, so a blank-content format
     * leaves the player's tab untouched. A {@link ConcurrentHashMap} guards the connect-while-rendering race and keeps
     * the project's "every player-keyed map is concurrent" convention; every mutation otherwise runs on the player's
     * region/entity thread.
     */
    private final Map<UUID, Boolean> appliedHeaderFooter = new ConcurrentHashMap<>();

    /** Paints the real player's name/order/skin row a selected format may carry; see {@link RealPlayerRowPainter}. */
    private final RealPlayerRowPainter rowPainter;

    /** Paints the fixed-slot {@link TablistLayout filler grid} a selected format may carry; see {@link FillerPainter}. */
    private final FillerPainter fillerPainter;

    /** Build a renderer with the full packet path. {@code viewers} supplies who a skin packet is broadcast to. */
    public TablistRenderer(
            Supplier<TablistFormatConfig> formats,
            AnimationRegistry animations,
            TabListPackets packets,
            TablistSkinResolver skinResolver,
            Supplier<? extends Collection<? extends Player>> viewers) {
        this.formats = Objects.requireNonNull(formats, "formats");
        this.animations = Objects.requireNonNull(animations, "animations");
        Objects.requireNonNull(packets, "packets");
        Objects.requireNonNull(skinResolver, "skinResolver");
        Objects.requireNonNull(viewers, "viewers");
        this.tablist = new Tablist();
        this.rowPainter = new RealPlayerRowPainter(packets, skinResolver, this::render, animations::tick, viewers);
        this.fillerPainter = new FillerPainter(packets, skinResolver, this::render);
    }

    /** Build a renderer whose viewers are every online player — the production fan-out. */
    public TablistRenderer(
            Supplier<TablistFormatConfig> formats,
            AnimationRegistry animations,
            TabListPackets packets,
            TablistSkinResolver skinResolver) {
        this(formats, animations, packets, skinResolver, Bukkit::getOnlinePlayers);
    }

    /** Render (or clear) {@code player}'s tablist from the selected format. Must run on the player's region thread. */
    public void renderFor(Player player) {
        Objects.requireNonNull(player, "player");
        ConditionContext ctx = conditionContext(player);
        // Capture the global animation tick once for this paint, so the header, footer, and name format all read the
        // same frame — the render task steps the clock once per loop tick before this fan-out.
        long tick = animations.tick();
        Optional<TablistFormat> selected = formats.get().select(ctx);
        if (selected.isEmpty()) {
            clear(player);
            return;
        }
        TablistFormat format = selected.get();
        TablistContent content = format.content();
        if (content.suppressedIn(player.getWorld().getName())) {
            clear(player);
            return;
        }
        applyHeaderFooter(player, content, tick);
        rowPainter.applyRow(player, format, tick);
        fillerPainter.applyFillers(player, format.layout(), tick);
    }

    /**
     * Re-send every currently-skinned online player's packet entry to a single newly-joined {@code viewer} so a late
     * joiner sees the custom skins the steady-state tick would not repaint for them — delegated to
     * {@link RealPlayerRowPainter#repaintSkinsFor}. Native Paper replicates a player's list name and order to every
     * viewer including late joiners, but the skin packet does not, so without this the joiner would see real skins. Must
     * run on the joining {@code viewer}'s region/entity thread, like {@link #renderFor(Player)} — the connection listener
     * hops there first.
     */
    public void repaintSkinsFor(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        rowPainter.repaintSkinsFor(viewer);
    }

    /** Clear {@code player}'s header/footer and reset any list name/order/skin/fillers this renderer applied. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
        appliedHeaderFooter.remove(player.getUniqueId());
        fillerPainter.clear(player);
        rowPainter.resetRow(player);
    }

    /** Clear {@code player}'s header/footer and forget their name/order/skin/filler tracking on quit. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
        appliedHeaderFooter.remove(player.getUniqueId());
        // On quit the player's connection is gone; just drop the tracking. A native reset packet to a closing channel
        // is a no-op, so revert is skipped — only the tracking is forgotten so a relog re-paints from scratch.
        fillerPainter.forget(player);
        rowPainter.forget(player);
    }

    /**
     * Reconcile the player's tab header/footer with the selected format's {@link TablistContent}. An authored
     * (non-blank) content is sent and the player marked as carrying this renderer's header/footer. A blank content (a
     * name-only / order-only format) sends nothing — uxmLib's {@link Tablist#set} would otherwise wipe the player's
     * existing header/footer — but if this renderer previously sent one for the player (a switch from a header-having
     * format) it clears its own to avoid leaving a stale header/footer behind.
     */
    private void applyHeaderFooter(Player player, TablistContent content, long tick) {
        UUID uuid = player.getUniqueId();
        if (content.isBlank()) {
            if (appliedHeaderFooter.remove(uuid) != null) {
                tablist.clear(player);
            }
            return;
        }
        tablist.set(player, joinLines(player, content.header(), tick), joinLines(player, content.footer(), tick));
        appliedHeaderFooter.put(uuid, Boolean.TRUE);
    }

    /**
     * Gather everything a format's condition needs from the live player: their permission check, world and gamemode
     * names, and the per-viewer PlaceholderAPI bridge so a {@code %papi%}-comparison condition expands the same way the
     * rendered lines do. Copied from the scoreboard renderer so both HUD modules select formats identically.
     */
    private ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
    }

    private Component joinLines(Player player, List<String> sources, long tick) {
        return Component.join(JoinConfiguration.newlines(), renderAll(player, sources, tick));
    }

    private List<Component> renderAll(Player player, List<String> sources, long tick) {
        List<Component> rendered = new ArrayList<>(sources.size());
        for (String source : sources) {
            rendered.add(render(player, source, tick));
        }
        return rendered;
    }

    private Component render(Player player, String source, long tick) {
        // Built-in {tokens} ({player}, {online}, {world}, …) resolve here off the live player, BEFORE the
        // PlaceholderAPI
        // bridge and MiniMessage, so the shipped header/footer/name-format show real values with or without
        // PlaceholderAPI. The animation %anim_<name>% pass runs first so a frame may itself carry tokens.
        String withTokens = BuiltinTokens.apply(player, animations.resolve(source, tick));
        return HudText.render(player.getUniqueId(), withTokens);
    }
}
