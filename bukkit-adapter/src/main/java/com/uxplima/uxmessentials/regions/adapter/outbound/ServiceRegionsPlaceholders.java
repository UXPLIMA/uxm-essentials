package com.uxplima.uxmessentials.regions.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RegionsPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link RegionsPlaceholders} seam over the region service. Every read starts from where the player is
 * standing, so the answers change as they walk; a player who is not connected stands nowhere and reads as being
 * in no region at all.
 *
 * <p>The region lookups are the provider's own in-memory spatial index, which is what makes this cheap enough
 * for a per-refresh HUD line.
 */
@NullMarked
public final class ServiceRegionsPlaceholders implements RegionsPlaceholders {

    private final Server server;
    private final RegionService regions;

    public ServiceRegionsPlaceholders(Server server, RegionService regions) {
        this.server = Objects.requireNonNull(server, "server");
        this.regions = Objects.requireNonNull(regions, "regions");
    }

    @Override
    public boolean available() {
        return regions.available();
    }

    @Override
    public Optional<Standing> standingIn(PlayerRef who) {
        List<RegionRef> covering = covering(who);
        if (covering.isEmpty()) {
            return Optional.empty();
        }
        RegionRef top = covering.get(0);
        return Optional.of(new Standing(top.id(), regions.priority(top), regions.owners(top), regions.members(top)));
    }

    @Override
    public int coveringCount(PlayerRef who) {
        return covering(who).size();
    }

    @Override
    public int worldCount(PlayerRef who) {
        Player player = server.getPlayer(Objects.requireNonNull(who, "who").uuid());
        if (player == null || !regions.available()) {
            return 0;
        }
        return regions.regionsIn(BukkitRefs.toRef(player.getWorld())).size();
    }

    /** Every region covering the player right now, highest priority first; empty when they are not connected. */
    private List<RegionRef> covering(PlayerRef who) {
        Player player = server.getPlayer(Objects.requireNonNull(who, "who").uuid());
        if (player == null || !regions.available()) {
            return List.of();
        }
        return regions.regionsAt(
                Position.of(BukkitRefs.toRef(player.getWorld()), player.getX(), player.getY(), player.getZ()));
    }
}
