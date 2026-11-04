package com.uxplima.uxmessentials.guard.adapter.outbound;

import com.uxplima.uxmessentials.guard.adapter.BukkitSubject;
import com.uxplima.uxmessentials.guard.application.GuardService;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import com.uxplima.uxmessentials.guard.domain.engine.RegionSet;
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RegionsPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * The {@code regions_*} placeholders over the guard (I04): the region a player stands in, its owners and members,
 * the player's role there and whether an action is allowed where they stand. The keys are the ones the WorldGuard
 * front end offered, so scoreboards keep working.
 */
public final class GuardRegionsPlaceholders implements RegionsPlaceholders {

    private final Server server;
    private final GuardService service;

    public GuardRegionsPlaceholders(Server server, GuardService service) {
        this.server = Objects.requireNonNull(server, "server");
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Optional<Standing> standingIn(PlayerRef who) {
        return set(who).flatMap(RegionSet::top).map(top -> {
            Region region = top.region();
            List<String> owners = new ArrayList<>();
            List<String> members = new ArrayList<>();
            region.roles().forEach((id, role) -> {
                String name = Optional.ofNullable(server.getOfflinePlayer(id).getName()).orElse(id.toString());
                if (role.equals(RoleName.OWNER)) {
                    owners.add(name);
                } else {
                    members.add(name);
                }
            });
            return new Standing(region.id().value(), region.priority(), owners, members);
        });
    }

    @Override
    public int coveringCount(PlayerRef who) {
        return set(who).map(s -> s.stored().size()).orElse(0);
    }

    @Override
    public int worldCount(PlayerRef who) {
        Player player = server.getPlayer(who.uuid());
        return player == null ? 0 : service.regions(player.getWorld().getName()).size();
    }

    @Override
    public Optional<String> role(PlayerRef who) {
        return set(who).flatMap(RegionSet::top).flatMap(top -> top.region().roleOf(who.uuid())).map(RoleName::value);
    }

    @Override
    public Optional<Boolean> allowed(PlayerRef who, String action) {
        Player player = server.getPlayer(who.uuid());
        Location at = player == null ? null : player.getLocation();
        if (player == null || at == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(service.snapshot().check(player.getWorld().getName(), at.getBlockX(), at.getBlockY(),
                    at.getBlockZ(), ActionId.of(action.replace('_', '.')), new BukkitSubject(player)) != Verdict.DENY);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private Optional<RegionSet> set(PlayerRef who) {
        Player player = server.getPlayer(who.uuid());
        @Nullable Location at = player == null ? null : player.getLocation();
        if (player == null || at == null) {
            return Optional.empty();
        }
        Optional<WorldIndex> index = service.snapshot().world(player.getWorld().getName());
        return index.filter(i -> !i.locked()).map(i -> i.at(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
    }
}
