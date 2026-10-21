package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.holograms.adapter.outbound.DamageIndicatorConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.display.HologramAppearance;
import com.uxplima.uxmlib.packet.display.HologramPackets;
import com.uxplima.uxmlib.packet.display.PacketHologram;
import org.jspecify.annotations.NullMarked;

/**
 * Spawns a short-lived floating number at an entity when it is hurt or healed (the damage-indicator feature, off
 * by default). On a combat hit it shows the final damage. In the crit format when the attacker's state matches a
 * vanilla critical hit, and on a heal it shows the gained health, gated per victim kind (players / mobs) by the
 * {@link DamageIndicatorConfig}. The indicator is a packet hologram: sent to the players near the entity and never
 * spawned, so the world holds no entity for it and a crash or a chunk unload cannot leave one behind. It is taken
 * back after the configured duration: the removal waits off-thread, then hops back onto the anchor's region thread
 * (Folia), the thread the hologram's own frames run on. No per-tick rise or fade: the indicator simply pops and goes.
 */
@NullMarked
public final class DamageIndicatorListener implements Listener {

    private final DamageIndicatorConfig config;
    private final Scheduler scheduler;
    private final com.uxplima.uxmlib.scheduler.Scheduler frames;
    private final HologramPackets packets;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /**
     * @param scheduler this plugin's port, which times the removal
     * @param frames the library's scheduler, which runs each indicator's frames on its anchor's region
     * @param packets the packets an indicator is sent with
     */
    public DamageIndicatorListener(
            DamageIndicatorConfig config,
            Scheduler scheduler,
            com.uxplima.uxmlib.scheduler.Scheduler frames,
            HologramPackets packets) {
        this.config = Objects.requireNonNull(config, "config");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.frames = Objects.requireNonNull(frames, "frames");
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        double amount = event.getFinalDamage();
        Entity victim = event.getEntity();
        if (amount <= 0 || !shouldShow(victim)) {
            return;
        }
        DamageIndicatorConfig.Kind kind =
                isCriticalHit(event.getDamager()) ? DamageIndicatorConfig.Kind.CRIT : DamageIndicatorConfig.Kind.DAMAGE;
        spawn(victim, config.format(amount, kind));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!config.showHeal()) {
            return;
        }
        double amount = event.getAmount();
        Entity victim = event.getEntity();
        if (amount <= 0 || !shouldShow(victim)) {
            return;
        }
        spawn(victim, config.format(amount, DamageIndicatorConfig.Kind.HEAL));
    }

    private boolean shouldShow(Entity entity) {
        if (entity instanceof Player) {
            return config.showForPlayers();
        }
        return entity instanceof LivingEntity && config.showForMobs();
    }

    /**
     * The vanilla critical-hit condition for a melee attacker: a player who is descending (a positive fall
     * distance, which the client resets to zero the moment they land, so it already implies airborne), not
     * riding anything, and not sprinting (a sprint hit is knockback, never a crit). Read entirely from the live
     * attacker, so it stays a cheap heuristic with no NMS.
     */
    private static boolean isCriticalHit(Entity damager) {
        return damager instanceof Player attacker
                && attacker.getFallDistance() > 0.0f
                && attacker.getVehicle() == null
                && !attacker.isSprinting();
    }

    private void spawn(Entity victim, String source) {
        Component text = miniMessage.deserialize(source);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = victim.getLocation()
                .add(random.nextDouble(-0.3, 0.3), victim.getHeight() + 0.4, random.nextDouble(-0.3, 0.3));
        PacketHologram indicator = PacketHologram.show(
                packets,
                frames,
                at,
                HologramAppearance.defaults()
                        .withBillboard(Display.Billboard.CENTER)
                        .withSeeThrough(true),
                viewer -> List.of(text));
        Position pos = BukkitRefs.toPosition(at);
        scheduler.asyncAfter(
                Duration.ofMillis(config.durationTicks() * 50L), () -> scheduler.onRegion(pos, indicator::remove));
    }
}
