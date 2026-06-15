package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffModeStore;
import org.jspecify.annotations.NullMarked;

/**
 * {@link StaffPlaceholders} over the staff context's {@link StaffModeStore} marker and the live online-player
 * roster. Built during staff wiring from the same mode store the {@code /staffmode} use cases hold, so the
 * mode placeholder matches whether the player is actually in staff mode in game.
 *
 * <p>The online-staff count enumerates the connected players holding the {@code uxmessentials.staff.member}
 * marker — the same node {@code /stafflist} and the presence {@code /staff} roster agree on. The loop is over
 * the online set (bounded by the player cap) and is a plain permission read, so it is cheap on the placeholder
 * path; it counts every staff-marker holder regardless of who asks, since the roster size is server-wide.
 */
@NullMarked
public final class StaffStaffPlaceholders implements StaffPlaceholders {

    private static final String STAFF_MEMBER_NODE = "uxmessentials.staff.member";

    private final StaffModeStore store;
    private final Server server;

    public StaffStaffPlaceholders(StaffModeStore store, Server server) {
        this.store = Objects.requireNonNull(store, "store");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean inStaffMode(PlayerRef who) {
        return store.isActive(Objects.requireNonNull(who, "who"));
    }

    @Override
    public int onlineStaffCount() {
        int count = 0;
        for (Player player : server.getOnlinePlayers()) {
            if (player.hasPermission(STAFF_MEMBER_NODE)) {
                count++;
            }
        }
        return count;
    }
}
