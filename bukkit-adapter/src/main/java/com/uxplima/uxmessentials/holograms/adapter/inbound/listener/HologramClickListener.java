package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramClickKey;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import org.jspecify.annotations.NullMarked;

/**
 * Runs a clickable hologram's command when a player right-clicks its Interaction hitbox. The hitbox carries the
 * hologram's name in its persistent data (stamped by the renderer under {@link HologramClickKey#PDC_KEY}); this
 * reads it back, looks the hologram up in the in-memory-authoritative repository (no DB read on the click), and
 * runs its stored command as the clicking player on the region thread the event already fires on. A short
 * per-player cooldown debounces the rapid duplicate interactions a single click can produce.
 */
@NullMarked
public final class HologramClickListener implements Listener {

    private static final long COOLDOWN_MS = 250L;

    private final NamespacedKey clickKey;
    private final HologramRepository repository;
    private final ConcurrentHashMap<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public HologramClickListener(Plugin plugin, HologramRepository repository) {
        Objects.requireNonNull(plugin, "plugin");
        this.clickKey = new NamespacedKey(plugin, HologramClickKey.PDC_KEY);
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Interaction box)) {
            return;
        }
        String name = box.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING);
        if (name == null) {
            return;
        }
        Player player = event.getPlayer();
        if (onCooldown(player.getUniqueId())) {
            return;
        }
        repository
                .find(HologramName.of(name))
                .map(Hologram::clickCommand)
                .filter(command -> !command.isBlank())
                .ifPresent(player::performCommand);
    }

    /** True when this player clicked within the debounce window; records the click time otherwise. */
    private boolean onCooldown(UUID player) {
        long now = System.currentTimeMillis();
        Long previous = lastClick.put(player, now);
        return previous != null && now - previous < COOLDOWN_MS;
    }
}
