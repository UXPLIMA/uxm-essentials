package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.TablistPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TablistPlaceholders} seam over the live {@link TablistRenderer}, which records the format it drew each
 * player from as it painted. The read is a map lookup, so a scoreboard line refreshing every tick costs nothing and
 * always agrees with the tab the player is looking at.
 */
@NullMarked
public final class RendererTablistPlaceholders implements TablistPlaceholders {

    private final TablistRenderer renderer;

    public RendererTablistPlaceholders(TablistRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public Optional<String> format(PlayerRef who) {
        return renderer.appliedFormat(Objects.requireNonNull(who, "who"));
    }
}
