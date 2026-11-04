package com.uxplima.uxmessentials.guard.adapter.outbound;

import com.uxplima.uxmessentials.api.query.UxmGuardQuery;
import com.uxplima.uxmessentials.api.query.UxmRegionsQuery;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmRegion;
import com.uxplima.uxmessentials.api.view.UxmRegionFlag;
import com.uxplima.uxmessentials.guard.adapter.BukkitSubject;
import com.uxplima.uxmessentials.guard.application.GuardService;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * The public read surface over the guard: {@link UxmRegionsQuery} for lists and lookups, {@link UxmGuardQuery} for
 * protection checks. Everything reads the current snapshot or the in-memory regions, so answers are immediate.
 */
public final class GuardQueries implements UxmRegionsQuery, UxmGuardQuery {

    private final GuardService service;

    public GuardQueries(GuardService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public CompletableFuture<List<UxmRegion>> in(String worldName) {
        return CompletableFuture.completedFuture(service.regions(worldName).stream().map(GuardQueries::view).toList());
    }

    @Override
    public CompletableFuture<Optional<UxmRegion>> region(String worldName, String id) {
        if (!RegionId.valid(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(
                service.region(new RegionKey(worldName, RegionId.of(id))).map(GuardQueries::view));
    }

    @Override
    public CompletableFuture<List<UxmRegion>> at(UxmLocation where) {
        Optional<WorldIndex> index = service.snapshot().world(where.world());
        if (index.isEmpty() || index.get().locked()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(index.get()
                .at(block(where.x()), block(where.y()), block(where.z())).stored().stream()
                .map(GuardQueries::view).toList());
    }

    @Override
    public boolean allowed(UUID player, String action, UxmLocation where) {
        Player online = Bukkit.getPlayer(player);
        GuardSubject subject = online != null ? new BukkitSubject(online) : offline(player);
        return check(action, where, subject);
    }

    @Override
    public boolean allowedWorld(String action, UxmLocation where) {
        return check(action, where, GuardSubject.WORLD);
    }

    @Override
    public List<UxmRegion> within(UxmLocation where, int radius) {
        return service.near(where.world(), block(where.x()), block(where.y()), block(where.z()), Math.max(0, radius))
                .stream().map(GuardQueries::view).toList();
    }

    @Override
    public int claimDistance() {
        return service.config().claimDistance();
    }

    @Override
    public void registerActions(List<String> ids) {
        List<ActionId> parsed = new ArrayList<>();
        for (String id : ids) {
            parsed.add(ActionId.of(id));
        }
        service.registerActions(parsed);
    }

    private boolean check(String action, UxmLocation where, GuardSubject subject) {
        ActionId id;
        try {
            id = ActionId.of(action);
        } catch (IllegalArgumentException invalid) {
            return true;
        }
        return service.snapshot().check(where.world(), block(where.x()), block(where.y()), block(where.z()), id,
                subject) != Verdict.DENY;
    }

    /** A guard region as the public API shows it. */
    public static UxmRegion view(Region region) {
        List<String> owners = new ArrayList<>();
        List<String> members = new ArrayList<>();
        region.roles().forEach((id, role) -> {
            if (role.equals(RoleName.OWNER)) {
                owners.add(id.toString());
            } else {
                members.add(id.toString());
            }
        });
        List<UxmRegionFlag> flags = new ArrayList<>();
        for (Rule rule : region.rules()) {
            flags.add(new UxmRegionFlag(rule.action() + " " + rule.target().text(), rule.effect().word()));
        }
        region.behaviours().asMap().forEach((key, value) -> flags.add(new UxmRegionFlag(key.key(), value)));
        return new UxmRegion(region.world(), region.id().value(), region.priority(), owners, members, flags);
    }

    private static int block(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static GuardSubject offline(UUID id) {
        return new GuardSubject() {
            @Override
            public @Nullable UUID id() {
                return id;
            }

            @Override
            public boolean has(String node) {
                return false;
            }

            @Override
            public boolean meets(String expression) {
                return false;
            }
        };
    }
}
