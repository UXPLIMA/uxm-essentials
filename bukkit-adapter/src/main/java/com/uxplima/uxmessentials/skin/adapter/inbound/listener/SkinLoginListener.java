package com.uxplima.uxmessentials.skin.adapter.inbound.listener;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.skin.adapter.outbound.PaperSkinView;
import com.uxplima.uxmessentials.skin.application.DressLogin;
import org.jspecify.annotations.NullMarked;

/**
 * Dresses a player while they are still connecting, so they arrive already wearing the right skin.
 *
 * <p>The profile is edited before the player entity exists, which is what makes this the cheap path: no respawn,
 * no re-send, nothing to flicker. It is also the one path that must never cost a login, so the resolution runs on
 * the async pool through the {@link Scheduler} port and is waited on for at most the configured timeout. A slow
 * Mojang, a MineSkin outage or a Geyser endpoint that hangs lets the player in wearing whatever they had, and
 * nothing thrown here reaches the server's login handling.
 */
@NullMarked
public final class SkinLoginListener implements Listener {

    private final DressLogin dressLogin;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration timeout;
    private final BooleanSupplier active;

    public SkinLoginListener(
            DressLogin dressLogin, Scheduler scheduler, Logger log, Duration timeout, BooleanSupplier active) {
        this.dressLogin = Objects.requireNonNull(dressLogin, "dressLogin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.active = Objects.requireNonNull(active, "active");
    }

    /**
     * Runs at {@link EventPriority#HIGH}, after the plugins that decide whether this login is allowed at all: a
     * connection somebody else has already refused is left alone rather than dressed on its way out.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!active.getAsBoolean() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        Optional<DressLogin.Dressed> dressed = resolve(event.getUniqueId(), event.getName());
        if (dressed.isEmpty()) {
            return;
        }
        PlayerProfile profile = event.getPlayerProfile();
        PaperSkinView.of(profile).dress(dressed.get().texture());
        event.setPlayerProfile(profile);
    }

    /** The join order's answer, or empty when it did not arrive in time or failed outright. */
    private Optional<DressLogin.Dressed> resolve(UUID player, String username) {
        CompletableFuture<Optional<DressLogin.Dressed>> answer = new CompletableFuture<>();
        scheduler.async(() -> answer.complete(dressLogin.resolve(player, username)));
        try {
            return answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception failure) {
            log.warn("event=skin_login_lookup_failed player={} reason={}", username, failure.toString());
            return Optional.empty();
        }
    }
}
