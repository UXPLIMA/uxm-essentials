package com.uxplima.uxmessentials.shared.adapter.inbound.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.settings.PlayerSwitches;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * Takes over the switches an earlier version kept in the player's data on one server only. Each was stored as a flag
 * meaning off; a set flag turns the shared switch off, unless the player already set the shared one. The flag is
 * removed only once the database is known to hold that switch, read at a later login, so a write that did not land
 * takes the flag over again. A player whose switches could not be read is left alone until a login that reads them.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>region-bound</b>. The player's data is read and written on their own thread, during their join. The
 * keys are built once in the constructor.
 */
@NullMarked
public final class LegacySwitches implements Listener {

    private final PlayerSwitches switches;
    private final Map<NamespacedKey, String> offFlags;

    /** @param offFlags the key of each old flag that meant off, and the shared switch it becomes */
    public LegacySwitches(Plugin plugin, PlayerSwitches switches, Map<String, String> offFlags) {
        Objects.requireNonNull(plugin, "plugin");
        this.switches = Objects.requireNonNull(switches, "switches");
        Map<NamespacedKey, String> keys = new HashMap<>();
        offFlags.forEach((key, name) -> keys.put(new NamespacedKey(plugin, key), name));
        this.offFlags = Map.copyOf(keys);
    }

    /** Lowest, so the switch is in place before another join listener reads it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!switches.ready(id)) {
            return;
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        offFlags.forEach((key, name) -> {
            if (!data.has(key)) {
                return;
            }
            if (switches.stored(id, name)) {
                data.remove(key);
            } else if (PdcFlag.get(data, key)) {
                switches.adopt(id, name, false);
            } else {
                data.remove(key);
            }
        });
    }
}
