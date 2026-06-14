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
 * <p>Each source string of the selected board is run through {@link HudText} — the per-viewer PlaceholderAPI bridge
 * ({@code %papi%} expansion, identity without PlaceholderAPI) then {@code MiniMessage} parse, the same two-step
 * transform the message sink uses — so operator content may embed third-party placeholders. The sidebar is reused
 * across ticks when it already exists (its {@code lines}/{@code title} diff flicker-free), created on first render, and
 * torn down when the player has hidden it, no board matches, or the selected board blacklists their world.
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

    private final SidebarManager sidebars;
    private final ScoreboardVisibilityStore visibility;
    private final Supplier<SidebarConfig> boards;

    public ScoreboardRenderer(
            SidebarManager sidebars, ScoreboardVisibilityStore visibility, Supplier<SidebarConfig> boards) {
        this.sidebars = Objects.requireNonNull(sidebars, "sidebars");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.boards = Objects.requireNonNull(boards, "boards");
    }

    /** Render (or tear down) {@code player}'s sidebar from the selected board. Must run on the player's region thread. */
    public void renderFor(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerRef who = BukkitRefs.toRef(player);
        Optional<SidebarBoard> selected = boards.get().select(conditionContext(player));
        if (selected.isEmpty() || visibility.hidden(who)) {
            clear(player);
            return;
        }
        DisplayContent live = selected.get().content();
        if (live.isBlank() || live.suppressedIn(player.getWorld().getName())) {
            clear(player);
            return;
        }
        renderSidebar(player, live);
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

    private void renderSidebar(Player player, DisplayContent live) {
        Component title = live.title().map(source -> render(player, source)).orElse(Component.empty());
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            sidebar = sidebars.create(player, title);
            applyNumberFormat(player, live);
        } else {
            sidebar.title(title);
        }
        sidebar.lines(renderAll(player, live.lines()));
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

    private List<Component> renderAll(Player player, List<String> sources) {
        List<Component> rendered = new ArrayList<>(sources.size());
        for (String source : sources) {
            rendered.add(render(player, source));
        }
        return rendered;
    }

    private Component render(Player player, String source) {
        return HudText.render(player.getUniqueId(), source);
    }
}
