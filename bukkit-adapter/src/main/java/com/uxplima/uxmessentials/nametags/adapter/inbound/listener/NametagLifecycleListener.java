package com.uxplima.uxmessentials.nametags.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.nametags.adapter.outbound.NametagRenderer;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a wearer's nametag in step with the connection lifecycle: spawn it on join so it appears at once rather than
 * after a full refresh interval, despawn it on quit so a dropped player leaves no orphan display entity, and
 * despawn-then-respawn on a world change because the entity belongs to a different region/world afterwards.
 *
 * <p>The join and world-change handlers hop onto the player's entity thread through the {@link Scheduler} port before
 * touching the live player or its display entity (Folia). The quit handler only removes a tracked entry, and the
 * renderer's {@code despawn} itself hops onto the entity's spawn-region thread to remove the display, so the quit path
 * needs no hop here. The render frame is read from the shared {@link AnimationRegistry} so the first paint matches the
 * loop's clock.
 */
@NullMarked
public final class NametagLifecycleListener implements Listener {

    private final NametagRenderer renderer;
    private final Scheduler scheduler;
    private final AnimationRegistry animations;

    public NametagLifecycleListener(NametagRenderer renderer, Scheduler scheduler, AnimationRegistry animations) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.animations = Objects.requireNonNull(animations, "animations");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.onEntity(BukkitRefs.toRef(player), () -> renderer.spawn(player, animations.tick()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        renderer.despawn(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // The display entity lived in the previous world's region; despawn it there (the renderer hops to its spawn
        // region), then re-spawn in the new world's region on the player's entity thread.
        renderer.despawn(player.getUniqueId());
        scheduler.onEntity(BukkitRefs.toRef(player), () -> renderer.spawn(player, animations.tick()));
    }
}
