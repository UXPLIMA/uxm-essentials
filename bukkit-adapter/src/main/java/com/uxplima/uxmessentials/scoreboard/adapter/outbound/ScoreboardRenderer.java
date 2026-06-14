package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * the sidebar objective. uxmLib's {@code Sidebar} owns its objective on its own native scoreboard and does not expose
 * it, so the objective is reached through the player's now-active scoreboard ({@code getObjective(DisplaySlot.SIDEBAR)});
 * the format is applied once when the board is first created, never re-applied on a steady-state tick.
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
        if (sidebars.get(player.getUniqueId()) != null) {
            sidebars.remove(player);
        }
    }

    /** Drop {@code uuid}'s sidebar bookkeeping without restoring a prior board — on quit. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
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
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            sidebar = sidebars.create(player, title);
            applyNumberFormat(player, live);
        } else {
            sidebar.title(title);
        }
        sidebar.lines(renderLines(player, live.lines(), ctx, tick));
    }

    /**
     * Apply the operator's number-format choice to the freshly created sidebar objective. uxmLib's {@code Sidebar}
     * keeps its objective private and shows its own scoreboard on {@code create}, so the live objective is read back
     * from the player's now-active scoreboard. Done once at creation — a steady-state tick reuses the same board and
     * the same format, so there is nothing to re-apply.
     */
    private void applyNumberFormat(Player player, DisplayContent live) {
        if (!live.hideScoreNumbers()) {
            return;
        }
        Objective objective = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        if (objective != null) {
            objective.numberFormat(NumberFormat.blank());
        }
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
