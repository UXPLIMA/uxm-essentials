package com.uxplima.uxmessentials.homes.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
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
 */
@NullMarked
public final class TeleportHomeAdapter implements HomeTeleporter {

    private final TeleportEngine engine;

    public TeleportHomeAdapter(TeleportEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public void teleportTo(PlayerRef who, Home home) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(home, "home");
        engine.launch(who, Destination.at(home.location()), TeleportKind.HOME);
    }
}
