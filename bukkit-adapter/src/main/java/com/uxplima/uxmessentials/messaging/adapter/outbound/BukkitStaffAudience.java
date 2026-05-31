package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link StaffAudience} implementation: every online player holding a permission node — the
 * {@code /helpop} staff audience. The application layer never iterates {@code Bukkit.getOnlinePlayers()}
 * itself; it asks this adapter, which maps each matching online player to a {@link PlayerRef}. A help-op
 * fan-out is an infrequent action, so the per-call scan of the online set is acceptable.
 */
@NullMarked
public final class BukkitStaffAudience implements StaffAudience {

    @Override
    public List<PlayerRef> onlineWith(String permissionNode) {
        Objects.requireNonNull(permissionNode, "permissionNode");
        java.util.List<PlayerRef> audience = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permissionNode)) {
                audience.add(BukkitRefs.toRef(player));
            }
        }
        return List.copyOf(audience);
    }
}
