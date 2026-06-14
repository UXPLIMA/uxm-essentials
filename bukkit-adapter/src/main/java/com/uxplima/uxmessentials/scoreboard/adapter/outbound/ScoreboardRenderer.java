package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import io.papermc.paper.scoreboard.numbers.NumberFormat;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarBoard;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.hud.scoreboard.Sidebar;
import com.uxplima.uxmlib.hud.scoreboard.SidebarManager;
import org.jspecify.annotations.NullMarked;

/**
 * Renders the per-player sidebar from the live {@link SidebarConfig}, dogfooding uxmLib's {@link SidebarManager}. Each
 * viewer is offered the board {@link SidebarConfig#select selected} for them: the highest-priority {@link SidebarBoard}
 * whose {@link com.uxplima.uxmessentials.shared.display.DisplayCondition condition} matches. The condition is evaluated
 * against a {@link ConditionContext} built from the live player — their permission check, world, gamemode, and the
 * per-viewer PlaceholderAPI bridge — so a {@code %papi% >= 10} or {@code permission:uxmessentials.staff} condition sees
 * real values. When no board matches, the sidebar is torn down.
 *
 * <p>Each source string of the selected board is first run through the {@link AnimationRegistry} — expanding any
 * {@code %anim_<name>%} token to the named animation's current frame at the global render tick — and then through
 * {@link HudText} — the per-viewer PlaceholderAPI bridge ({@code %papi%} expansion, identity without PlaceholderAPI)
 * then {@code MiniMessage} parse, the same two-step transform the message sink uses — so operator content may embed both
 * animations and third-party placeholders. The animation token is expanded <em>before</em> PlaceholderAPI and
 * MiniMessage so an animation frame may itself carry colour tags or placeholders. The sidebar is reused across ticks
 * when it already exists (its {@code lines}/{@code title} diff flicker-free), created on first render, and torn down
 * when the player has hidden it, no board matches, or the selected board blacklists their world.
 *
 * <p>A line source may carry a per-viewer condition with the literal {@code " | "} separator —
 * {@code "<condition> | <text>"}. The condition (parsed by {@link ConditionParser}) is evaluated against the same
 * per-viewer {@link ConditionContext}; when it does not match, the line is dropped from that viewer's board entirely
 * rather than rendered blank. Only the first {@code " | "} splits — a line that legitimately contains {@code " | "} in
 * its text keeps everything after the first separator as the text. Lines without the separator render unconditionally.
 * The title is never conditional: a selected board always shows its title.
 *
 * <p>When the selected board's {@link DisplayContent#hideScoreNumbers()} is set the renderer drops the red per-line
 * score numbers vanilla draws down the right edge by applying a {@linkplain NumberFormat#blank() blank number format} to
 * the sidebar objective; when it is unset the format is reset to the vanilla default ({@code numberFormat(null)}) so the
 * red numbers show again. uxmLib's {@code Sidebar} owns its objective on its own native scoreboard and does not expose
 * it, so the objective is reached through the player's now-active scoreboard ({@code getObjective(DisplaySlot.SIDEBAR)}).
 * Because the same sidebar is reused across ticks and even across a board switch — a viewer can move from a board that
 * hides numbers to one that shows them with no teardown in between — the format must track the <em>currently</em>
 * selected board, not the one the sidebar was created for. The renderer remembers the last value it applied per player
 * ({@link #appliedHideNumbers}) and re-applies only when the sidebar is freshly created or the selected board's choice
 * changed; a steady-state tick with no switch never re-calls the setter (re-applying sends a client update and risks
 * flicker). The remembered value is dropped on tear-down so a recreated board re-applies from scratch.
 *
 * <p>The tablist header/footer is a separate context now: {@code tablist} owns it through its own renderer and refresh
 * timer, so this renderer touches only the sidebar.
 *
 * <p>{@link #renderFor(Player)} touches the live player, so the caller must invoke it on the player's region/entity
 * thread — the render timer and the connection listener both hop there first. An empty {@link SidebarConfig} (nothing
 * authored) or a viewer no board matches renders nothing and clears any board left over from a prior config or a prior
 * selection.
 */
@NullMarked
public final class ScoreboardRenderer {

    /** The literal separator between a per-line condition and its text: {@code "<condition> | <text>"}. */
    private static final String CONDITION_SEPARATOR = " | ";

    private final SidebarManager sidebars;
    private final ScoreboardVisibilityStore visibility;
    private final Supplier<SidebarConfig> boards;
    private final AnimationRegistry animations;

    /**
     * The last {@link DisplayContent#hideScoreNumbers()} value applied to each player's live sidebar objective, keyed by
     * player UUID. Owned by the player's region/entity thread (every mutation runs there), so a plain map under
     * {@code compute}/{@code remove} would suffice; a {@link ConcurrentHashMap} guards the connect-while-rendering race
     * and keeps the project's "every player-keyed map is concurrent" convention. An absent key means no format has been
     * applied for the player's current board, so the next render applies from scratch.
     */
    private final Map<UUID, Boolean> appliedHideNumbers = new ConcurrentHashMap<>();

    public ScoreboardRenderer(
            SidebarManager sidebars,
            ScoreboardVisibilityStore visibility,
            Supplier<SidebarConfig> boards,
            AnimationRegistry animations) {
        this.sidebars = Objects.requireNonNull(sidebars, "sidebars");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.boards = Objects.requireNonNull(boards, "boards");
        this.animations = Objects.requireNonNull(animations, "animations");
    }

    /** Render (or tear down) {@code player}'s sidebar from the selected board. Must run on the player's region thread. */
    public void renderFor(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerRef who = BukkitRefs.toRef(player);
        // The same per-viewer context decides board selection and per-line conditions; build it once. The animation
        // tick is captured once here too, so every line of this paint reads the same global animation frame.
        ConditionContext ctx = conditionContext(player);
        long tick = animations.tick();
        Optional<SidebarBoard> selected = boards.get().select(ctx);
        if (selected.isEmpty() || visibility.hidden(who)) {
            clear(player);
            return;
        }
        DisplayContent live = selected.get().content();
        if (live.isBlank() || live.suppressedIn(player.getWorld().getName())) {
            clear(player);
            return;
        }
        renderSidebar(player, live, ctx, tick);
    }

    /** Tear down {@code player}'s sidebar — on hide, on quit, or when the display is suppressed. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        appliedHideNumbers.remove(player.getUniqueId());
        if (sidebars.get(player.getUniqueId()) != null) {
            sidebars.remove(player);
        }
    }

    /** Drop {@code uuid}'s sidebar bookkeeping without restoring a prior board — on quit. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        appliedHideNumbers.remove(player.getUniqueId());
        sidebars.forget(player.getUniqueId());
    }

    /**
     * Gather everything a board's condition needs from the live player: their permission check, world and gamemode
     * names, and the per-viewer PlaceholderAPI bridge so a {@code %papi%}-comparison condition expands the same way the
     * rendered lines do.
     */
    private ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
    }

    private void renderSidebar(Player player, DisplayContent live, ConditionContext ctx, long tick) {
        Component title =
                live.title().map(source -> render(player, source, tick)).orElse(Component.empty());
        Sidebar existing = sidebars.get(player.getUniqueId());
        boolean created = existing == null;
        Sidebar sidebar;
        if (existing == null) {
            sidebar = sidebars.create(player, title);
        } else {
            sidebar = existing;
            sidebar.title(title);
        }
        applyNumberFormat(player, live, created);
        sidebar.lines(renderLines(player, live.lines(), ctx, tick));
    }

    /**
     * Reconcile the live sidebar objective's number format with the currently selected board's
     * {@link DisplayContent#hideScoreNumbers()} choice. uxmLib's {@code Sidebar} keeps its objective private and shows
     * its own scoreboard on creation, so the live objective is read back from the player's now-active scoreboard.
     *
     * <p>A freshly {@code created} objective already shows the vanilla red numbers, so a brand-new board that does not
     * hide them needs no setter call at all — the baseline is simply recorded. Otherwise the format is touched only when
     * the desired choice differs from the last value applied for this player: a steady-state tick with no board switch
     * leaves the objective alone (re-applying sends a client update and risks flicker), while a switch between two reused
     * boards re-applies. On a hide-numbers board the format is set blank; on a show-numbers board it is reset to the
     * vanilla default with {@code numberFormat(null)} so the red numbers reappear after a switch back.
     */
    private void applyNumberFormat(Player player, DisplayContent live, boolean created) {
        UUID uuid = player.getUniqueId();
        boolean desired = live.hideScoreNumbers();
        Boolean lastApplied = appliedHideNumbers.get(uuid);
        if (created) {
            lastApplied = Boolean.FALSE; // a new objective shows the vanilla numbers until told otherwise
        }
        if (Boolean.valueOf(desired).equals(lastApplied)) {
            appliedHideNumbers.put(uuid, desired);
            return;
        }
        Objective objective = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return;
        }
        objective.numberFormat(desired ? NumberFormat.blank() : null);
        appliedHideNumbers.put(uuid, desired);
    }

    private List<Component> renderLines(Player player, List<String> sources, ConditionContext ctx, long tick) {
        List<Component> rendered = new ArrayList<>(sources.size());
        for (String source : visibleLines(sources, ctx)) {
            rendered.add(render(player, source, tick));
        }
        return rendered;
    }

    /**
     * Drop the lines whose per-line condition does not match {@code ctx} and strip the condition prefix off the rest,
     * leaving only the text each viewer should render. A line carrying the {@code " | "} separator splits on the first
     * occurrence into {@code (condition, text)}; the condition is parsed by {@link ConditionParser} and the line is kept
     * only when it {@link DisplayCondition#matches matches}. A line without the separator is always kept verbatim. Pure
     * over {@code ctx}, so it is unit-testable without a live board.
     */
    static List<String> visibleLines(List<String> sources, ConditionContext ctx) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(ctx, "ctx");
        List<String> visible = new ArrayList<>(sources.size());
        for (String source : sources) {
            int separator = source.indexOf(CONDITION_SEPARATOR);
            if (separator < 0) {
                visible.add(source);
                continue;
            }
            String conditionPart = source.substring(0, separator);
            String text = source.substring(separator + CONDITION_SEPARATOR.length());
            if (ConditionParser.parse(conditionPart).matches(ctx)) {
                visible.add(text);
            }
        }
        return visible;
    }

    private Component render(Player player, String source, long tick) {
        return HudText.render(player.getUniqueId(), animations.resolve(source, tick));
    }
}
