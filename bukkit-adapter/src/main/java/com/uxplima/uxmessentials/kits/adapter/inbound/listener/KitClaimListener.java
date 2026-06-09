package com.uxplima.uxmessentials.kits.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.event.KitClaimed;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Listens for the {@link KitClaimed} domain event to execute custom claim effects (sound, particles)
 * and claim commands (player/console) on the recipient's entity thread. Supports wait delay and target slots.
 */
@NullMarked
public final class KitClaimListener {

    private final Plugin plugin;
    private final KitRepository repository;
    private final Scheduler scheduler;
    private final Logger log;

    public KitClaimListener(Plugin plugin, KitRepository repository, Scheduler scheduler, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Dispatch sound, particles, and commands for the claimed kit on the recipient's entity thread.
     */
    public void onClaim(KitClaimed event) {
        Optional<KitDefinition> kitOpt = repository.find(event.kit());
        if (kitOpt.isEmpty()) {
            return;
        }
        KitDefinition kit = kitOpt.get();
        PlayerRef recipientRef = event.recipient();
        scheduler.onEntity(recipientRef, () -> {
            Player player = Bukkit.getPlayer(recipientRef.uuid());
            if (player == null || !player.isOnline()) {
                return;
            }

            org.bukkit.Location location = java.util.Objects.requireNonNull(player.getLocation(), "player location");

            // 1. Play Sound
            kit.sound().ifPresent(soundName -> {
                try {
                    org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(
                            soundName.toLowerCase(java.util.Locale.ROOT).trim());
                    Sound sound = org.bukkit.Registry.SOUNDS.get(key);
                    if (sound != null) {
                        player.playSound(location, sound, 1.0f, 1.0f);
                    } else {
                        log.warn("Invalid sound name in kit definition "
                                + kit.id().value() + ": " + soundName);
                    }
                } catch (Exception e) {
                    log.warn("Invalid sound name in kit definition " + kit.id().value() + ": " + soundName);
                }
            });

            // 2. Play Particles
            kit.particles().ifPresent(particleName -> {
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(
                        particleName.toLowerCase(java.util.Locale.ROOT).trim());
                Particle particle = org.bukkit.Registry.PARTICLE_TYPE.get(key);
                if (particle != null) {
                    player.getWorld().spawnParticle(particle, location.clone().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                } else {
                    log.warn("Invalid particle name in kit definition "
                            + kit.id().value() + ": " + particleName);
                }
            });

            // 3. Execute Commands
            for (String cmd : kit.commands()) {
                if (cmd.isBlank()) {
                    continue;
                }

                String processedCmd = cmd.trim();

                // Parse delay prefix: delay:X
                long delayTicks = 0;
                if (processedCmd.startsWith("delay:")) {
                    int space = processedCmd.indexOf(' ');
                    if (space != -1) {
                        String delayStr = processedCmd.substring("delay:".length(), space);
                        try {
                            delayTicks = Long.parseLong(delayStr);
                            processedCmd = processedCmd.substring(space + 1).trim();
                        } catch (NumberFormatException ignored) {
                            // ignore invalid delay formatting, fallback to 0
                        }
                    }
                }

                // Parse slot prefix: slot:X
                int forceSlot = -1;
                if (processedCmd.startsWith("slot:")) {
                    int space = processedCmd.indexOf(' ');
                    if (space != -1) {
                        String slotStr = processedCmd.substring("slot:".length(), space);
                        try {
                            int slotVal = Integer.parseInt(slotStr);
                            if (slotVal >= 0 && slotVal < 36) {
                                forceSlot = slotVal;
                                processedCmd = processedCmd.substring(space + 1).trim();
                            }
                        } catch (NumberFormatException ignored) {
                            // ignore invalid slot formatting, fallback to -1
                        }
                    }
                }

                String finalCmd = processedCmd;
                int finalSlot = forceSlot;

                if (delayTicks > 0) {
                    player.getScheduler()
                            .execute(
                                    plugin,
                                    () -> {
                                        if (player.isOnline()) {
                                            executeCommandString(player, finalCmd, finalSlot);
                                        }
                                    },
                                    null,
                                    delayTicks);
                } else {
                    executeCommandString(player, finalCmd, finalSlot);
                }
            }
        });
    }

    private void executeCommandString(Player player, String cmd, int forceSlot) {
        String processed = cmd;
        if (PlaceholderApiSupport.isPresent()) {
            processed =
                    PlaceholderApiSupport.messageBridge(player.getUniqueId()).apply(processed);
        }
        processed = processed
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString());

        boolean console = true;
        if (processed.startsWith("player:")) {
            console = false;
            processed = processed.substring("player:".length()).trim();
        } else if (processed.startsWith("console:")) {
            processed = processed.substring("console:".length()).trim();
        }

        if (forceSlot >= 0 && forceSlot < 36) {
            dispatchToSlot(player, processed, console, forceSlot);
        } else {
            dispatch(player, processed, console);
        }
    }

    private void dispatch(Player player, String command, boolean console) {
        if (console) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            player.performCommand(command);
        }
    }

    /**
     * Run {@code command} and move the stack it adds into {@code forceSlot}, without ever emptying the rest of
     * the inventory. The old approach swapped all 36 slots for barrier dummies and restored them in a finally
     * block, which lost or exposed the player's items whenever the dispatched command teleported, kicked, or
     * opened an inventory, or the player disconnected mid-dispatch. Here the inventory is only ever read before
     * the dispatch (a snapshot of the main 36 slots) and the single added stack is relocated afterwards, so any
     * early exit during the command leaves the inventory exactly as the command left it — items are never put
     * at risk by the slot-forcing itself.
     */
    private void dispatchToSlot(Player player, String command, boolean console, int forceSlot) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack[] before = snapshotMainInventory(inv);

        dispatch(player, command, console);

        if (!player.isOnline()) {
            return;
        }
        int added = firstAddedSlot(inv, before, forceSlot);
        if (added < 0) {
            return;
        }
        relocate(inv, before, added, forceSlot);
    }

    private static ItemStack[] snapshotMainInventory(org.bukkit.inventory.PlayerInventory inv) {
        ItemStack[] snapshot = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            snapshot[i] = item == null ? null : item.clone();
        }
        return snapshot;
    }

    /** The first main-inventory slot (other than {@code forceSlot}) the command added or grew a stack in. */
    private static int firstAddedSlot(org.bukkit.inventory.PlayerInventory inv, ItemStack[] before, int forceSlot) {
        for (int i = 0; i < 36; i++) {
            if (i == forceSlot) {
                continue;
            }
            ItemStack now = inv.getItem(i);
            if (now == null || now.getType().isAir()) {
                continue;
            }
            ItemStack was = before[i];
            boolean grew =
                    was == null || was.getType().isAir() || !was.isSimilar(now) || now.getAmount() > was.getAmount();
            if (grew) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Move the stack the command landed in {@code addedSlot} into {@code forceSlot}, parking whatever the
     * player already had in {@code forceSlot} into the now-vacated {@code addedSlot} (a one-for-one swap of two
     * slots). When {@code forceSlot} was empty the displaced stack is air and nothing is parked. Because only
     * these two slots are ever touched, no other item can be lost by the relocation.
     */
    private void relocate(org.bukkit.inventory.PlayerInventory inv, ItemStack[] before, int addedSlot, int forceSlot) {
        ItemStack added = inv.getItem(addedSlot);
        ItemStack displaced = before[forceSlot];
        inv.setItem(forceSlot, added);
        inv.setItem(addedSlot, displaced == null || displaced.getType().isAir() ? null : displaced);
    }
}
