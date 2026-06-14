package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
import com.uxplima.uxmlib.hud.Tablist;
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

    /**
     * The last name-format source string applied to each player's list name, keyed by player UUID. An absent key means
     * no name format is currently applied for the player (vanilla list name). A {@link ConcurrentHashMap} guards the
     * connect-while-rendering race and keeps the project's "every player-keyed map is concurrent" convention; every
     * mutation otherwise runs on the player's region/entity thread.
     */
    private final Map<UUID, String> appliedNameFormat = new ConcurrentHashMap<>();

    /** The last sort order applied to each player, keyed by player UUID. An absent key means no order is applied. */
    private final Map<UUID, Integer> appliedOrder = new ConcurrentHashMap<>();

    public TablistRenderer(Supplier<TablistFormatConfig> formats, AnimationRegistry animations) {
        this.formats = Objects.requireNonNull(formats, "formats");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.tablist = new Tablist();
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
        renderHeaderFooter(player, content, tick);
        applyNameFormat(player, format.nameFormat(), tick);
        applyOrder(player, format.sortOrder());
    }

    /** Clear {@code player}'s header/footer and reset any list name/order this renderer applied. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
        resetNameAndOrder(player);
    }

    /** Clear {@code player}'s header/footer and forget their name/order tracking on quit. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
        resetNameAndOrder(player);
    }

    private void renderHeaderFooter(Player player, TablistContent content, long tick) {
        tablist.set(player, joinLines(player, content.header(), tick), joinLines(player, content.footer(), tick));
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
}
