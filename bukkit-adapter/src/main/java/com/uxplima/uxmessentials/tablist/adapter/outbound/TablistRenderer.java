package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmlib.hud.Tablist;
import org.jspecify.annotations.NullMarked;

/**
 * Renders the per-player tablist header and footer from the live {@link TablistContent}, dogfooding uxmLib's
 * {@link Tablist}. Each source string is run through {@link HudText} — the per-viewer PlaceholderAPI bridge
 * ({@code %papi%} expansion, identity without PlaceholderAPI) then {@code MiniMessage} parse, the same two-step
 * transform the message sink uses — so operator content may embed third-party placeholders. The header and footer
 * line lists are joined with newlines before delivery, exactly as the old combined scoreboard renderer did.
 *
 * <p>The tablist is always-on for every viewer when enabled — there is no per-player visibility toggle. A blank
 * {@link TablistContent} (nothing authored) or a blacklisted world clears the player's header/footer.
 *
 * <p>{@link #renderFor(Player)} touches the live player, so the caller must invoke it on the player's region/entity
 * thread — the render timer and the connection listener both hop there first.
 */
@NullMarked
public final class TablistRenderer {

    private final Tablist tablist;
    private final Supplier<TablistContent> content;

    public TablistRenderer(Supplier<TablistContent> content) {
        this.content = Objects.requireNonNull(content, "content");
        this.tablist = new Tablist();
    }

    /** Render (or clear) {@code player}'s tablist from the live content. Must run on the player's region thread. */
    public void renderFor(Player player) {
        Objects.requireNonNull(player, "player");
        TablistContent live = content.get();
        if (live.isBlank() || live.suppressedIn(player.getWorld().getName())) {
            clear(player);
            return;
        }
        tablist.set(player, joinLines(player, live.header()), joinLines(player, live.footer()));
    }

    /** Clear {@code player}'s header/footer — on quit, or when the tablist is blank or suppressed. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
    }

    /** Clear {@code player}'s header/footer on quit; the tablist keeps no per-player state to forget. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        tablist.clear(player);
    }

    private Component joinLines(Player player, List<String> sources) {
        return Component.join(JoinConfiguration.newlines(), renderAll(player, sources));
    }

    private List<Component> renderAll(Player player, List<String> sources) {
        List<Component> rendered = new ArrayList<>(sources.size());
        for (String source : sources) {
            rendered.add(HudText.render(player.getUniqueId(), source));
        }
        return rendered;
    }
}
