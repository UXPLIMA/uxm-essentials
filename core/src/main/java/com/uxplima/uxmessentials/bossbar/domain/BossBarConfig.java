package com.uxplima.uxmessentials.bossbar.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.display.ConditionContext;

/**
 * The bars a server has written, in the order they stack on the screen, and the rule that picks the ones a player
 * sees. Every bar whose turn it is shows at once; nothing is chosen between them.
 *
 * @param bars the bars, top first
 */
public record BossBarConfig(List<BossBarDefinition> bars) {

    public BossBarConfig {
        bars = List.copyOf(Objects.requireNonNull(bars, "bars"));
    }

    public static BossBarConfig empty() {
        return new BossBarConfig(List.of());
    }

    /** The bar named {@code id}, if one is written. */
    public Optional<BossBarDefinition> byId(String id) {
        Objects.requireNonNull(id, "id");
        return bars.stream().filter(bar -> bar.id().equals(id)).findFirst();
    }

    /**
     * The bars the player described by {@code ctx} sees at {@code now}, top first. A bar shows when its condition
     * matches and it is announced to the player, or, when it is not an announcement bar, when its window is open or
     * it has none. An announced bar counts its announcement; a timed bar counts its own end.
     *
     * @param announced the announcement window running for a bar id, if any
     */
    public List<Shown> shownTo(ConditionContext ctx, Instant now, Function<String, Optional<BossBarWindow>> announced) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(announced, "announced");
        List<Shown> shown = new ArrayList<>();
        for (BossBarDefinition bar : bars) {
            if (!bar.condition().matches(ctx)) {
                continue;
            }
            Optional<BossBarWindow> announcement = announced.apply(bar.id()).filter(window -> window.activeAt(now));
            if (announcement.isPresent()) {
                shown.add(new Shown(bar, announcement));
            } else if (!bar.announcementOnly()
                    && bar.window().map(window -> window.activeAt(now)).orElse(true)) {
                shown.add(new Shown(bar, bar.window()));
            }
        }
        return List.copyOf(shown);
    }

    /**
     * One bar on a player's screen.
     *
     * @param bar the bar
     * @param clock the window its time and its drain count, if it has one
     */
    public record Shown(BossBarDefinition bar, Optional<BossBarWindow> clock) {

        public Shown {
            Objects.requireNonNull(bar, "bar");
            Objects.requireNonNull(clock, "clock");
        }
    }
}
