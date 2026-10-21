package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Who may see a hologram. An {@code ALL} hologram is seen by everybody, a {@code PERMISSION} hologram by the holders
 * of its node, and a {@code MANUAL} hologram by the players it was shown to, read through the injected
 * {@code manualViewers} lookup. A player on the hologram's blacklist sees it under no mode.
 *
 * <p>Each hologram asks this for every player near it on every frame, so both lookups must answer from memory.
 */
@NullMarked
public final class HologramViewers {

    private final Permissions permissions;
    private final Function<HologramName, Set<UUID>> manualViewers;
    private final Function<HologramName, Set<UUID>> blacklist;

    public HologramViewers(
            Permissions permissions,
            Function<HologramName, Set<UUID>> manualViewers,
            Function<HologramName, Set<UUID>> blacklist) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.manualViewers = Objects.requireNonNull(manualViewers, "manualViewers");
        this.blacklist = Objects.requireNonNull(blacklist, "blacklist");
    }

    /** Whether {@code viewer} is on {@code hologram}'s blacklist: hidden from it regardless of visibility mode. */
    boolean isBlacklisted(Hologram hologram, Player viewer) {
        return blacklist.apply(hologram.name()).contains(viewer.getUniqueId());
    }

    /** Whether {@code viewer} may currently see {@code hologram} under its visibility and shown-viewer set. */
    boolean maySee(Hologram hologram, Player viewer, Set<UUID> shown) {
        return maySee(permissions, hologram.visibility(), BukkitRefs.toRef(viewer), shown);
    }

    /** The manual shown-viewer set for a MANUAL hologram, queried lazily; empty for any other mode. */
    Set<UUID> shownViewersFor(Hologram hologram) {
        if (!hologram.visibility().isManual()) {
            return Set.of();
        }
        return manualViewers.apply(hologram.name());
    }

    /**
     * Whether {@code who} may see a hologram with this {@code visibility}: everyone for {@code ALL}, only a holder
     * of the gating node for {@code PERMISSION}, and only a member of {@code shownViewers} for {@code MANUAL}.
     */
    static boolean maySee(Permissions permissions, Visibility visibility, PlayerRef who, Set<UUID> shownViewers) {
        if (visibility.isManual()) {
            return shownViewers.contains(who.uuid());
        }
        if (!visibility.isPermissionGated()) {
            return true;
        }
        String node = visibility.permission();
        return node != null && permissions.has(who, node);
    }
}
