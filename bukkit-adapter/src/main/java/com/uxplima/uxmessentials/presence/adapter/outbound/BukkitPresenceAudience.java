package com.uxplima.uxmessentials.presence.adapter.outbound;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PresenceAudience} implementation: every online player, the recipients of an AFK away/back
 * broadcast. The application layer never iterates {@code Bukkit.getOnlinePlayers()} itself; it asks this
 * adapter, which maps each online player to a {@link PlayerRef}. An AFK transition is an infrequent action, so
 * the per-call scan of the online set is acceptable. Mirrors the messaging context's {@code BukkitStaffAudience}.
 */
@NullMarked
public final class BukkitPresenceAudience implements PresenceAudience {

    @Override
    public List<PlayerRef> online() {
        List<PlayerRef> audience = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            audience.add(BukkitRefs.toRef(player));
        }
        return List.copyOf(audience);
    }
}
