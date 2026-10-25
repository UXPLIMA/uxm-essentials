package com.uxplima.uxmessentials.bossbar.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractHudRenderTask;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/** Draws every player's bars again each {@code refresh-ticks}, spread over the interval like the sidebar's. */
@NullMarked
public final class BossBarRenderTask extends AbstractHudRenderTask {

    private final BossBarRenderer renderer;

    public BossBarRenderTask(
            Scheduler scheduler,
            BossBarRenderer renderer,
            AnimationRegistry animations,
            Logger log,
            Supplier<Duration> interval,
            BooleanSupplier running) {
        super(scheduler, animations, log, interval, running);
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    protected void renderFor(Player player) {
        renderer.renderFor(player);
    }

    @Override
    protected String tickFailureMessage() {
        return "boss bar render tick failed";
    }
}
