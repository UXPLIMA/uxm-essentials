package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.hud.Tablist;
import com.uxplima.uxmlib.hud.scoreboard.Sidebar;
import com.uxplima.uxmlib.hud.scoreboard.SidebarManager;
import org.jspecify.annotations.NullMarked;

/**
 * Renders the per-player sidebar and tablist from the live {@link DisplayContent}, dogfooding uxmLib's
 * {@link SidebarManager} and {@link Tablist}. Each source string is run through the same two-step transform the
 * message sink uses — the per-viewer PlaceholderAPI bridge ({@code %papi%} expansion, identity without PlaceholderAPI)
 * then {@link MiniMessage} parse — so operator content may embed third-party placeholders. The sidebar is reused
 * across ticks when it already exists (its {@code lines}/{@code title} diff flicker-free), created on first render,
 * and torn down when the player has hidden it or stands in a blacklisted world.
 *
 * <p>{@link #renderFor(Player)} touches the live player, so the caller must invoke it on the player's region/entity
 * thread — the render timer and the connection listener both hop there first. A blank {@link DisplayContent} (nothing
 * authored) renders nothing and clears any board left over from a prior config.
 */
@NullMarked
public final class ScoreboardRenderer {

    private final SidebarManager sidebars;
    private final Tablist tablist;
    private final MiniMessage miniMessage;
    private final ScoreboardVisibilityStore visibility;
    private final Supplier<DisplayContent> content;

    public ScoreboardRenderer(
            SidebarManager sidebars, ScoreboardVisibilityStore visibility, Supplier<DisplayContent> content) {
        this.sidebars = Objects.requireNonNull(sidebars, "sidebars");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.content = Objects.requireNonNull(content, "content");
        this.tablist = new Tablist();
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Render (or tear down) {@code player}'s display from the live content. Must run on the player's region thread. */
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
        renderTablist(player, live);
    }

    /** Tear down {@code player}'s sidebar and tablist — on hide, on quit, or when the display is suppressed. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        if (sidebars.get(player.getUniqueId()) != null) {
            sidebars.remove(player);
        }
        tablist.clear(player);
    }

    /** Drop {@code uuid}'s sidebar bookkeeping without restoring a prior board — on quit. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        sidebars.forget(player.getUniqueId());
        tablist.clear(player);
    }

    private void renderSidebar(Player player, DisplayContent live) {
        Component title = live.title().map(source -> render(player, source)).orElse(Component.empty());
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            sidebar = sidebars.create(player, title);
        } else {
            sidebar.title(title);
        }
        sidebar.lines(renderAll(player, live.lines()));
    }

    private void renderTablist(Player player, DisplayContent live) {
        if (live.header().isEmpty() && live.footer().isEmpty()) {
            return;
        }
        tablist.set(player, joinLines(player, live.header()), joinLines(player, live.footer()));
    }

    private List<Component> renderAll(Player player, List<String> sources) {
        List<Component> rendered = new ArrayList<>(sources.size());
        for (String source : sources) {
            rendered.add(render(player, source));
        }
        return rendered;
    }

    private Component joinLines(Player player, List<String> sources) {
        return Component.join(JoinConfiguration.newlines(), renderAll(player, sources));
    }

    private Component render(Player player, String source) {
        String expanded =
                PlaceholderApiSupport.messageBridge(player.getUniqueId()).apply(source);
        return miniMessage.deserialize(expanded);
    }
}
