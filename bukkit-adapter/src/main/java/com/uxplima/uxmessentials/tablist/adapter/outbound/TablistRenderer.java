package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistFormatConfig;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmlib.hud.Tablist;
import com.uxplima.uxmlib.packet.tablist.TabEntry;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;

/**
 * Renders the per-player tablist from the live {@link TablistFormatConfig}, dogfooding uxmLib's {@link Tablist}. Each
 * viewer is offered the format {@link TablistFormatConfig#select selected} for them: the highest-priority
 * {@link TablistFormat} whose {@link com.uxplima.uxmessentials.shared.display.DisplayCondition condition} matches. The
 * condition is evaluated against a {@link ConditionContext} built from the live player — their permission check, world,
 * gamemode, and the per-viewer PlaceholderAPI bridge — so a {@code %papi% >= 10} or {@code permission:uxmessentials.staff}
 * condition sees real values. When no format matches, the header/footer is cleared and any list name/order this renderer
 * applied is reset to vanilla.
 *
 * <p><strong>Empty header/footer leave the tab untouched.</strong> A format may carry a name format and/or sort order
 * but an empty header and footer (a name-only / order-only format) — a supported case. uxmLib's {@link Tablist#set}
 * sends both the header and the footer in one native call, so blindly rendering an empty pair would wipe whatever header
 * and footer vanilla or another plugin had set for the player. So the renderer only sends the header/footer when the
 * selected format actually authors one, and tracks which players it last sent one to ({@link #appliedHeaderFooter}): a
 * player who switches <em>from</em> a header-having format <em>to</em> a name-only one has the renderer's own
 * header/footer cleared (it would otherwise go stale), while a player who never had one keeps the tab the operator never
 * authored.
 *
 * <p>A selected format contributes three things, each independent:
 *
 * <ul>
 *   <li>its {@link TablistContent} header/footer line lists, each source first run through the {@link AnimationRegistry}
 *       (expanding any {@code %anim_<name>%} token to the named animation's current frame at the global render tick) then
 *       through {@link HudText} (the per-viewer PlaceholderAPI bridge then MiniMessage parse) and joined with newlines.
 *       The animation token is expanded <em>before</em> PlaceholderAPI and MiniMessage so a frame may itself carry colour
 *       tags or placeholders, the same two-step transform the scoreboard sidebar uses;</li>
 *   <li>its {@link TablistFormat#nameFormat() name format}, when present, applied as the viewer's tab-list name via
 *       {@link Player#playerListName(Component)} — how the viewer appears to everyone. The {@code {player}} token is
 *       substituted with the viewer's name before the {@link HudText} transform, so {@code "<red>[Staff] {player}"}
 *       renders the player's name; PlaceholderAPI tokens (e.g. {@code %player_name%}) are expanded by {@code HudText}
 *       as usual;</li>
 *   <li>its {@link TablistFormat#sortOrder() sort order}, when present, applied via {@link Player#setPlayerListOrder(int)}
 *       (Paper 1.21.2+). The value is a positive integer; a higher value sorts the player higher in the tab list, ties
 *       broken alphabetically by name by the client. An absent order leaves the vanilla order untouched.</li>
 * </ul>
 *
 * <p><strong>Custom skins go through packets.</strong> The one thing native Paper cannot do is put a custom texture on a
 * tab row. When a selected format carries a {@link TablistFormat#skin() skin source}, the renderer resolves it to a
 * {@link TabSkin} and delivers the row to <em>every</em> viewer through uxmLib's {@link TabListPackets#addOrUpdate} —
 * re-adding the player's entry with the texture seated on the profile — <em>instead of</em> the native list-name/order
 * setters for that player. The rendered name and order are the same ones native would apply, so nothing regresses except
 * the (added) texture. When the format carries <em>no</em> skin (the default) the native path above is taken unchanged.
 * Re-adding a real online player's entry every refresh tick would flicker, so the packet — like the native name/order —
 * is sent only when the applied tuple (name source, order, skin source) <em>changes</em> for the player; a steady-state
 * tick re-sends nothing. On a switch away from a skin format the entry is re-added once with the player's own real
 * texture so the custom skin reverts.
 *
 * <p><strong>Apply-only-on-change.</strong> The header/footer diff flicker-free inside uxmLib's {@code Tablist}, but the
 * list name and order are re-sent to the client on every setter call, so the renderer applies them only when the value
 * actually changes. It remembers the last name-format <em>source string</em> and the last order it applied per player
 * ({@link #appliedNameFormat} / {@link #appliedOrder}); a steady-state tick with no format switch re-applies neither.
 * The remembered name-format is the raw source (not the rendered component) because the source is the operator's intent
 * — a per-viewer placeholder expansion changing between ticks does not warrant a re-send, only an operator authoring a
 * new format does. The remembered values are dropped on clear/quit so a re-selected format re-applies from scratch.
 *
 * <p>{@link #renderFor(Player)} touches the live player, so the caller must invoke it on the player's region/entity
 * thread — the render timer and the connection listener both hop there first.
 */
@NullMarked
public final class TablistRenderer {

    /** The token in a name-format substituted with the viewer's own name before the {@link HudText} transform. */
    private static final String PLAYER_TOKEN = "{player}";

    private final Tablist tablist;
    private final Supplier<TablistFormatConfig> formats;
    private final AnimationRegistry animations;
    private final TabListPackets packets;
    private final TablistSkinResolver skinResolver;
    private final Supplier<? extends Collection<? extends Player>> viewers;

    /**
     * The last name-format source string applied to each player's list name, keyed by player UUID. An absent key means
     * no name format is currently applied for the player (vanilla list name). A {@link ConcurrentHashMap} guards the
     * connect-while-rendering race and keeps the project's "every player-keyed map is concurrent" convention; every
     * mutation otherwise runs on the player's region/entity thread.
     */
    private final Map<UUID, String> appliedNameFormat = new ConcurrentHashMap<>();

    /** The last sort order applied to each player, keyed by player UUID. An absent key means no order is applied. */
    private final Map<UUID, Integer> appliedOrder = new ConcurrentHashMap<>();

    /**
     * Whether this renderer currently has a header/footer applied for each player, keyed by player UUID. A {@code true}
     * value means the last selected format authored a header/footer and we sent it, so a switch to a name-only/order-only
     * format must clear it rather than leave it stale. An absent key means we never sent one, so a blank-content format
     * leaves the player's tab untouched. A {@link ConcurrentHashMap} guards the connect-while-rendering race and keeps
     * the project's "every player-keyed map is concurrent" convention; every mutation otherwise runs on the player's
     * region/entity thread.
     */
    private final Map<UUID, Boolean> appliedHeaderFooter = new ConcurrentHashMap<>();

    /**
     * The last skin row this renderer painted through packets for each player, keyed by player UUID. An absent key means
     * the player is on the native path (no skin). The remembered tuple is the name source, order, and skin source so a
     * tick that changes none of them re-sends no packet (no flicker on a steady-state tick), while a format switch — or
     * an offline skin fetch completing — re-paints. Cleared on revert/clear/quit so a re-selected skin format re-paints.
     */
    private final Map<UUID, AppliedSkin> appliedSkin = new ConcurrentHashMap<>();

    /** Build a renderer with the full packet path. {@code viewers} supplies who a skin packet is broadcast to. */
    public TablistRenderer(
            Supplier<TablistFormatConfig> formats,
            AnimationRegistry animations,
            TabListPackets packets,
            TablistSkinResolver skinResolver,
            Supplier<? extends Collection<? extends Player>> viewers) {
        this.formats = Objects.requireNonNull(formats, "formats");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.skinResolver = Objects.requireNonNull(skinResolver, "skinResolver");
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.tablist = new Tablist();
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
        applyRow(player, format, tick);
    }

    /**
     * Re-send every currently-skinned online player's packet entry to a single newly-joined {@code viewer}, so a late
     * joiner sees the custom skins the steady-state tick would not repaint for them.
     *
     * <p>Native Paper replicates a player's list name and sort order to every viewer, including late joiners, so those
     * need no special handling. The packet skin path does <em>not</em>: the server adds an already-online skinned player
     * to the joiner's tab with that player's <em>real</em> profile, and the renderer's steady-state tick re-sends nothing
     * for an unchanged tuple — so without this the joiner would see real skins forever. For each skinned target still
     * online (including the joining viewer themselves, whose own format may carry a skin) the same tuple the steady state
     * holds ({@link AppliedSkin}) is rebuilt into a {@link TabEntry} and sent to the one joining viewer only — never a
     * full broadcast. A target whose skin is still resolving was never recorded in {@link #appliedSkin}, so it is simply
     * skipped here; the next steady tick repaints it to all viewers once the texture lands.
     *
     * <p>Must run on the joining {@code viewer}'s region/entity thread, like {@link #renderFor(Player)} — the connection
     * listener hops there first.
     */
    public void repaintSkinsFor(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        for (Map.Entry<UUID, AppliedSkin> painted : appliedSkin.entrySet()) {
            Player target = Bukkit.getPlayer(painted.getKey());
            if (target == null) {
                // The skinned target logged off between paint and this join; their entry no longer exists to repaint.
                continue;
            }
            repaintSkinFor(viewer, target, painted.getValue());
        }
    }

    /** Rebuild {@code target}'s skin row from the tuple the steady state holds and send it to the one {@code viewer}. */
    private void repaintSkinFor(Player viewer, Player target, AppliedSkin painted) {
        Optional<TabSkin> skin = skinResolver.resolve(painted.skinSource());
        if (skin.isEmpty()) {
            // The texture has fallen out of cache since the paint; the next steady tick will repaint it to all viewers.
            return;
        }
        long tick = animations.tick();
        Component name =
                painted.nameSource().isEmpty() ? displayName(target) : renderName(target, painted.nameSource(), tick);
        TabEntry entry = new TabEntry(target.getUniqueId(), name, painted.order(), skin.get(), target.getName());
        packets.send(viewer, packets.addOrUpdate(entry));
    }

    /** Clear {@code player}'s header/footer and reset any list name/order/skin this renderer applied. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
        appliedHeaderFooter.remove(player.getUniqueId());
        resetRow(player);
    }

    /** Clear {@code player}'s header/footer and forget their name/order/skin tracking on quit. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
        appliedHeaderFooter.remove(player.getUniqueId());
        // On quit the player's connection is gone; just drop the tracking. A native reset packet to a closing channel
        // is a no-op, so revert is skipped — only the tracking is forgotten so a relog re-paints from scratch.
        appliedSkin.remove(player.getUniqueId());
        resetNameAndOrder(player);
    }

    /**
     * Apply the name and order a selected format carries. A format with a {@link TablistFormat#skin() skin} delivers the
     * row (name + order + texture) through packets to every viewer instead of the native setters; a format with no skin
     * keeps the native list-name/order path. A switch between the two reverts the path it is leaving so neither lingers.
     */
    private void applyRow(Player player, TablistFormat format, long tick) {
        Optional<TablistSkinSource> skinSource = format.skin();
        if (skinSource.isEmpty()) {
            // No skin: native path, and revert any skin entry this renderer previously painted for the player.
            revertSkin(player);
            applyNameFormat(player, format.nameFormat(), tick);
            applyOrder(player, format.sortOrder());
            return;
        }
        applySkinRow(player, format, skinSource.get(), tick);
    }

    /**
     * Paint {@code player}'s row through a packet carrying the resolved skin, sent to every viewer. Applied only when the
     * tuple (name source, order, skin source) changed for the player, so a steady-state tick re-sends nothing and a real
     * online player's entry is not re-added every tick. A skin source that resolves to no texture yet (an offline fetch
     * still in flight) falls back to the native path this tick — the resolver fills its cache and a later tick repaints.
     */
    private void applySkinRow(Player player, TablistFormat format, TablistSkinSource skinSource, long tick) {
        Optional<TabSkin> skin = skinResolver.resolve(skinSource);
        if (skin.isEmpty()) {
            // The texture is not available yet; take the native path so the name/order still apply with no skin.
            revertSkin(player);
            applyNameFormat(player, format.nameFormat(), tick);
            applyOrder(player, format.sortOrder());
            return;
        }
        UUID uuid = player.getUniqueId();
        String nameSource = format.nameFormat().orElse("");
        int order = format.sortOrder().orElse(0);
        AppliedSkin desired = new AppliedSkin(nameSource, order, skinSource);
        if (desired.equals(appliedSkin.get(uuid))) {
            return;
        }
        // Taking ownership of the row through packets, so drop any native name/order this renderer set for the player.
        resetNameAndOrder(player);
        Component name = format.nameFormat()
                .map(source -> renderName(player, source, tick))
                .orElse(displayName(player));
        broadcast(packets.addOrUpdate(new TabEntry(uuid, name, order, skin.get(), player.getName())));
        appliedSkin.put(uuid, desired);
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

    /**
     * Reconcile the player's tab-list name with the selected format's {@link TablistFormat#nameFormat()}. Applies only
     * when the source string changed from the last value applied for this player: a steady-state tick re-sends nothing
     * (the setter pushes a client update every call). An absent name format on a player who currently has one resets it
     * to vanilla with {@code playerListName(null)}.
     *
     * <p>The tracking keys on the raw source, not the rendered name, so an {@code %anim_<name>%} token in a name format
     * resolves to the frame current when the operator last changed the format rather than re-sending the player's name
     * to the client every animation tick (the header/footer carry the animated surface, diffed flicker-free).
     */
    private void applyNameFormat(Player player, Optional<String> nameFormat, long tick) {
        UUID uuid = player.getUniqueId();
        if (nameFormat.isEmpty()) {
            if (appliedNameFormat.remove(uuid) != null) {
                player.playerListName(null);
            }
            return;
        }
        String source = nameFormat.get();
        if (source.equals(appliedNameFormat.get(uuid))) {
            return;
        }
        player.playerListName(renderName(player, source, tick));
        appliedNameFormat.put(uuid, source);
    }

    /**
     * Reconcile the player's tab-list sort order with the selected format's {@link TablistFormat#sortOrder()}. Applies
     * only when the order changed from the last value applied for this player. An absent order on a player who currently
     * has one resets it to the vanilla default ({@code setPlayerListOrder(0)}).
     */
    private void applyOrder(Player player, OptionalInt sortOrder) {
        UUID uuid = player.getUniqueId();
        if (sortOrder.isEmpty()) {
            if (appliedOrder.remove(uuid) != null) {
                player.setPlayerListOrder(0);
            }
            return;
        }
        int order = sortOrder.getAsInt();
        if (Integer.valueOf(order).equals(appliedOrder.get(uuid))) {
            return;
        }
        player.setPlayerListOrder(order);
        appliedOrder.put(uuid, order);
    }

    /**
     * Revert a skin row this renderer previously painted for the player: re-add the entry once with the player's own
     * real texture (read inline from their live profile, no network) and the vanilla name/order, so the custom skin
     * drops back to the player's real skin without removing the server-owned entry. A no-op for a player not on the
     * skin path.
     */
    private void revertSkin(Player player) {
        UUID uuid = player.getUniqueId();
        if (appliedSkin.remove(uuid) == null) {
            return;
        }
        TabSkin own = skinResolver
                .resolve(new TablistSkinSource.PlayerName(player.getName()))
                .orElse(null);
        broadcast(packets.addOrUpdate(new TabEntry(uuid, displayName(player), 0, own, player.getName())));
    }

    /** Reset the player's vanilla list name/order/skin if this renderer set any, and drop their tracking. */
    private void resetRow(Player player) {
        revertSkin(player);
        resetNameAndOrder(player);
    }

    /** Reset the player's vanilla list name/order if this renderer set either, and drop their tracking. */
    private void resetNameAndOrder(Player player) {
        UUID uuid = player.getUniqueId();
        if (appliedNameFormat.remove(uuid) != null) {
            player.playerListName(null);
        }
        if (appliedOrder.remove(uuid) != null) {
            player.setPlayerListOrder(0);
        }
    }

    /** Send a built packet to every current viewer. The same packet object is reused across viewers, never rebuilt. */
    private void broadcast(Object packet) {
        for (Player viewer : viewers.get()) {
            packets.send(viewer, packet);
        }
    }

    /** The player's plain name as a component — the fallback display when a skin row carries no name format. */
    private static Component displayName(Player player) {
        return Component.text(player.getName());
    }

    private Component renderName(Player player, String source, long tick) {
        // The {player} convenience token is ours, so substitute it first; then expand any %anim_<name>% token to the
        // current frame BEFORE HudText runs PlaceholderAPI + MiniMessage, so a frame may itself carry
        // tags/placeholders.
        String withName = source.replace(PLAYER_TOKEN, player.getName());
        return render(player, withName, tick);
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
        return HudText.render(player.getUniqueId(), animations.resolve(source, tick));
    }

    /**
     * The skin row last painted for a player: the name-format source, sort order, and skin source. Two paints with an
     * equal tuple are the same row, so the packet is not re-sent — this is what keeps a real online player's entry from
     * being re-added every refresh tick (the flicker guard). A change in any field re-paints.
     */
    private record AppliedSkin(String nameSource, int order, TablistSkinSource skinSource) {}
}
