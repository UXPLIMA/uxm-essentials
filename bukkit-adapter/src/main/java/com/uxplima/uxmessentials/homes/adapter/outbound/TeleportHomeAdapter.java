package com.uxplima.uxmessentials.homes.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.adapter.outbound.PlayerThread;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code HomeTeleporter} implementation that <em>delegates</em> a home teleport to the teleport
 * context's {@link TeleportEngine}. Homes resolves which {@link Home} the player asked for; this adapter
 * hands the home's position to the engine's gated {@code launch}, so the shared teleport cooldown, the
 * move-cancellable warmup (wired to the move listener through the teleport context's tracking warmups), and
 * the region-aware async hop are all the teleport context's concern. The homes context owns no movement
 * code of its own.
 *
 * <p>The hop is attributed to {@link TeleportKind#HOME} so the cooldown/warmup tier and the audit verb are
 * the home ones; the engine's cooldown gate result is intentionally ignored here because the engine already
 * notifies the player when a cooldown is active.
 *
 * <p>The home use cases look the home up on the async lane, but the launch reads where the player stands to arm the
 * move-cancel, and on Folia only the player's own region thread may read that. So the launch always runs on the
 * player's entity thread: at once when the caller is already there, otherwise after one hop.
 */
@NullMarked
public final class TeleportHomeAdapter implements HomeTeleporter {

    private final TeleportEngine engine;
    private final Scheduler scheduler;

    public TeleportHomeAdapter(TeleportEngine engine, Scheduler scheduler) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void teleportTo(PlayerRef who, Home home) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(home, "home");
        // A player who left before the hop lands hears nothing; there is nothing of theirs here to undo.
        PlayerThread.run(
                scheduler, who, () -> engine.launch(who, Destination.at(home.location()), TeleportKind.HOME), () -> {});
    }

    @Override
    public void arrive(PlayerRef who, Home home, Runnable onLanded, Runnable onMissed) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(onLanded, "onLanded");
        Objects.requireNonNull(onMissed, "onMissed");
        // The same destination shape as a launch, so the landing stamps the home cooldown key the origin checked.
        if (engine.arriveImmediately(who, Destination.at(home.location()), TeleportKind.HOME, onLanded, onMissed)
                .isErr()) {
            onMissed.run();
        }
    }
}
