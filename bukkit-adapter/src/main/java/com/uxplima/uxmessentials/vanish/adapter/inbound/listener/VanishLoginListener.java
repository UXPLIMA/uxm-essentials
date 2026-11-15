package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vanish.application.RememberVanish;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reads a connecting player's kept vanish while they are still logging in, so the join can hide them before anybody
 * sees them. The read runs on the async lane and the login waits for it only up to the configured timeout: a slow
 * database lets the player in, hidden for now if they may vanish, and the join then reads the state again itself and
 * decides when it arrives.
 *
 * <p>What was read is held until the join takes it. A login another plugin refuses never joins, so an answer older
 * than {@link #HELD_FOR} is dropped the next time anything is held.
 */
@NullMarked
public final class VanishLoginListener implements Listener {

    /** Far longer than any login takes from pre-login to join, so only abandoned answers reach it. */
    private static final Duration HELD_FOR = Duration.ofMinutes(1);

    /**
     * What the login read for one player: their kept state, empty when they have none, and when it was read. The join
     * judges the evidence at that instant, since a slow join (a forced resource pack) can outlast the hop window.
     */
    public record Read(Optional<StoredVanish> stored, Instant readAt) {

        public Read {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(readAt, "readAt");
        }
    }

    private record Held(Read read, long at) {}

    private final RememberVanish remember;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration timeout;
    private final ConcurrentHashMap<UUID, Held> held = new ConcurrentHashMap<>();

    public VanishLoginListener(RememberVanish remember, Scheduler scheduler, Logger log, Duration timeout) {
        this.remember = Objects.requireNonNull(remember, "remember");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Runs at {@link EventPriority#HIGH}, after the plugins that decide whether the login is allowed, so a refused
     * connection costs no read. It only reads; the event itself is never changed.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID player = event.getUniqueId();
        // An answer left from an earlier attempt that never joined must not stand in for this one.
        held.remove(player);
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        CompletableFuture<Optional<StoredVanish>> answer = new CompletableFuture<>();
        Instant readAt = Instant.now();
        scheduler.async(() -> {
            try {
                answer.complete(remember.load(player));
            } catch (RuntimeException failed) {
                answer.completeExceptionally(failed);
            }
        });
        try {
            hold(player, answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS), readAt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            // Nothing held means the join reads the state again; the login goes ahead either way.
            log.warn("event=vanish_login_read_failed player={} reason={}", event.getName(), failure.toString());
        }
    }

    /** The state read at login for {@code player}, removed as it is taken; {@code null} when none was read in time. */
    public @Nullable Read take(UUID player) {
        Objects.requireNonNull(player, "player");
        Held answer = held.remove(player);
        return answer == null ? null : answer.read();
    }

    /** Drop everything held; called on module stop. */
    public void clear() {
        held.clear();
    }

    private void hold(UUID player, Optional<StoredVanish> stored, Instant readAt) {
        long now = System.currentTimeMillis();
        held.values().removeIf(answer -> now - answer.at() > HELD_FOR.toMillis());
        held.put(player, new Held(new Read(stored, readAt), now));
    }
}
