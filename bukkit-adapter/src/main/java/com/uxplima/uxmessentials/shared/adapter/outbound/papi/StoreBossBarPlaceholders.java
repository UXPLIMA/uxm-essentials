package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarRenderer;
import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/** The boss bar seam over the choice {@code /bossbars} keeps and what the renderer last drew. */
@NullMarked
public final class StoreBossBarPlaceholders implements BossBarPlaceholders {

    private final BossBarVisibilityStore visibility;
    private final BossBarRenderer renderer;

    public StoreBossBarPlaceholders(BossBarVisibilityStore visibility, BossBarRenderer renderer) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public boolean visible(PlayerRef who) {
        return !visibility.hidden(Objects.requireNonNull(who, "who"));
    }

    @Override
    public List<String> shown(PlayerRef who) {
        return renderer.shownIds(Objects.requireNonNull(who, "who").uuid());
    }

    @Override
    public Optional<String> remaining(PlayerRef who, String bar) {
        return renderer.remainingWords(Objects.requireNonNull(who, "who").uuid(), Objects.requireNonNull(bar, "bar"));
    }
}
