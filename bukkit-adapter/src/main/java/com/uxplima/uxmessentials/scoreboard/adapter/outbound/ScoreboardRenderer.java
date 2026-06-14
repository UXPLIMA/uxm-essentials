package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import io.papermc.paper.scoreboard.numbers.NumberFormat;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.hud.scoreboard.Sidebar;
import com.uxplima.uxmlib.hud.scoreboard.SidebarManager;
import org.jspecify.annotations.NullMarked;

/**
 * Renders the per-player sidebar from the live {@link DisplayContent}, dogfooding uxmLib's {@link SidebarManager}.
 * Each source string is run through {@link HudText} — the per-viewer PlaceholderAPI bridge ({@code %papi%} expansion,
 * identity without PlaceholderAPI) then {@code MiniMessage} parse, the same two-step transform the message sink uses —
 * so operator content may embed third-party placeholders. The sidebar is reused across ticks when it already exists
 * (its {@code lines}/{@code title} diff flicker-free), created on first render, and torn down when the player has
 * hidden it or stands in a blacklisted world.
 *
 * <p>When {@link DisplayContent#hideScoreNumbers()} is set the renderer drops the red per-line score numbers vanilla
 * draws down the right edge by applying a {@linkplain NumberFormat#blank() blank number format} to the sidebar
 * objective. uxmLib's {@code Sidebar} owns its objective on its own native scoreboard and does not expose it, so the
 * objective is reached through the player's now-active scoreboard ({@code getObjective(DisplaySlot.SIDEBAR)}); the
 * format is applied once when the board is first created, never re-applied on a steady-state tick.
 *
 * <p>The tablist header/footer is a separate context now: {@code tablist} owns it through its own renderer and refresh
 * timer, so this renderer touches only the sidebar.
 *
 * <p>{@link #renderFor(Player)} touches the live player, so the caller must invoke it on the player's region/entity
 * thread — the render timer and the connection listener both hop there first. A blank {@link DisplayContent} (nothing
 * authored) renders nothing and clears any board left over from a prior config.
 */
@NullMarked
public final class ScoreboardRenderer {

    private final SidebarManager sidebars;
    private final ScoreboardVisibilityStore visibility;
    private final Supplier<DisplayContent> content;

    public ScoreboardRenderer(
            SidebarManager sidebars, ScoreboardVisibilityStore visibility, Supplier<DisplayContent> content) {
        this.sidebars = Objects.requireNonNull(sidebars, "sidebars");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.content = Objects.requireNonNull(content, "content");
    }

    /** Render (or tear down) {@code player}'s sidebar from the live content. Must run on the player's region thread. */
    public void renderFor(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerRef who = BukkitRefs.toRef(player);
        DisplayContent live = content.get();
        if (live.isBlank()
                || visibility.hidden(who)
                || live.suppressedIn(player.getWorld().getName())) {
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
