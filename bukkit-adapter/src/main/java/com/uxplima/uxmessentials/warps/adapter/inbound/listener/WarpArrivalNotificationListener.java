package com.uxplima.uxmessentials.warps.adapter.inbound.listener;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry;
import com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry.PendingWarpNotification;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class WarpArrivalNotificationListener implements Listener {

    private final Scheduler scheduler;
    private final ConfigStore config;
    private final WarpTeleportRegistry registry;
    private final MiniMessage miniMessage;

    public WarpArrivalNotificationListener(Scheduler scheduler, ConfigStore config, WarpTeleportRegistry registry) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Optional<PendingWarpNotification> pendingOpt = registry.getAndRemove(uuid);
        if (pendingOpt.isEmpty()) {
            return;
        }
        PendingWarpNotification pending = pendingOpt.get();

        // 1. Play Departure Effects at departure location
        playDepartureEffects(event.getFrom(), pending);

        // 2. Schedule Arrival Effects and Welcome Message 1 tick later at arrival location (Folia-safe)
        PlayerRef ref = BukkitRefs.toRef(player);
        scheduler.onEntity(ref, () -> {
            Player live = Bukkit.getPlayer(uuid);
            if (live == null || !live.isOnline()) {
                return;
            }
            playArrivalEffects(Objects.requireNonNull(live.getLocation()), pending);
            showWelcomeMessages(live, ref, pending);
        });
    }

    private void playDepartureEffects(Location loc, PendingWarpNotification pending) {
        World world = loc.getWorld();
        if (world == null) return;

        // Sound
        String soundFallback =
                config.getString("modules.warps.effects.sound.departure.type", "ENTITY_ENDERMAN_TELEPORT");
        playSound(world, loc, pending.departureSound(), soundFallback);

        // Particle
        String particleFallback = config.getString("modules.warps.effects.particle.departure.type", "PORTAL");
        playParticle(world, loc, pending.departureParticle(), particleFallback);
    }

    private void playArrivalEffects(Location loc, PendingWarpNotification pending) {
        World world = loc.getWorld();
        if (world == null) return;

        // Sound
        String soundFallback = config.getString("modules.warps.effects.sound.arrival.type", "ENTITY_ENDERMAN_TELEPORT");
        playSound(world, loc, pending.arrivalSound(), soundFallback);

        // Particle
        String particleFallback = config.getString("modules.warps.effects.particle.arrival.type", "PORTAL");
        playParticle(world, loc, pending.arrivalParticle(), particleFallback);
    }

    private void playSound(World world, Location loc, Optional<String> soundName, String fallback) {
        Sound sound = BukkitRegistryKeys.resolveSound(soundName.orElse(fallback));
        if (sound == null) {
            sound = BukkitRegistryKeys.resolveSound(fallback);
        }
        if (sound != null) {
            world.playSound(loc, sound, 1.0f, 1.0f);
        }
    }

    private void playParticle(World world, Location loc, Optional<String> particleName, String fallback) {
        Particle particle = BukkitRegistryKeys.resolveParticle(particleName.orElse(fallback));
        if (particle == null) {
            particle = BukkitRegistryKeys.resolveParticle(fallback);
        }
        if (particle != null) {
            world.spawnParticle(particle, loc, 30, 0.5, 0.5, 0.5, 0.1);
        }
    }

    private void showWelcomeMessages(Player player, PlayerRef ref, PendingWarpNotification pending) {
        if (pending.welcomeMessages().isEmpty()) {
            return;
        }

        Component titleComp = Component.empty();
        Component subtitleComp = Component.empty();
        boolean hasTitle = false;
        boolean hasSubtitle = false;

        for (WelcomeMessage msg : pending.welcomeMessages()) {
            if (msg.message().isBlank()) {
                continue;
            }
            String text = msg.message().replace("%player%", player.getName()).replace("%warp%", pending.name());

            Component component = miniMessage.deserialize(text);
            String mode = msg.type().toUpperCase(java.util.Locale.ROOT);

            switch (mode) {
                case "CHAT" -> player.sendMessage(component);
                case "ACTION_BAR" -> player.sendActionBar(component);
                case "TITLE" -> {
                    titleComp = component;
                    hasTitle = true;
                }
                case "SUBTITLE" -> {
                    subtitleComp = component;
                    hasSubtitle = true;
                }
                case "BOSS_BAR" -> {
                    BossBar bar = BossBar.bossBar(component, 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
                    player.showBossBar(bar);
                    // Hide bossbar after 3 seconds (Folia-safe)
                    scheduler.asyncAfter(Duration.ofSeconds(3), () -> {
                        scheduler.onEntity(ref, () -> {
                            Player live = Bukkit.getPlayer(ref.uuid());
                            if (live != null && live.isOnline()) {
                                live.hideBossBar(bar);
                            }
                        });
                    });
                }
                default -> player.sendMessage(component); // Default to chat
            }
        }

        if (hasTitle || hasSubtitle) {
            Title title = Title.title(
                    titleComp,
                    subtitleComp,
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500)));
            player.showTitle(title);
        }
    }
}
