package com.uxplima.uxmessentials.scoreboard.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.scoreboard.UxmScoreboardVisibilityEvent;
import com.uxplima.uxmessentials.scoreboard.domain.event.ScoreboardVisibilityToggled;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/** Which Bukkit event the scoreboard fact becomes. */
@NullMarked
public final class ScoreboardEventBridges {

    private ScoreboardEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                ScoreboardVisibilityToggled.class,
                UxmScoreboardVisibilityEvent.getHandlerList(),
                fact -> new UxmScoreboardVisibilityEvent(
                        fact.who().uuid(), fact.who().name(), fact.hidden()),
                fact -> Region.entity(fact.who()));
    }
}
