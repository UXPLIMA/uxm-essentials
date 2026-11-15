package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.HomesMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /visit <player> <slot>}: teleport an actor to another player's home. Access is by three gates that
 * any one of satisfies. The actor is the owner, the home is public, or the actor is on the home's invite
 * list. A slot the owner has no home in is rejected with {@link HomeError#NOT_FOUND}; a home that fails every
 * gate is refused with {@link HomeError#NOT_ACCESSIBLE}. Execution is then <em>delegated</em> to the teleport
 * context through {@link HomeTeleporter}; this use case never moves the player itself, so the shared
 * cooldown, the move-cancellable warmup, and the region-aware async hop are all the teleport context's
 * concern.
 *
 * <p>A home set on another backend, while the homes {@code cross-server} block is on, is reached through the shared
 * cross-server hop instead, after the same teleport rules; a visit carries no charge. An owner visiting their own
 * home takes {@link TeleportHome}'s path, so the visit command is no way around the teleport cost.
 */
public final class VisitHome {

    private final HomeRepository repository;
    private final HomeInviteRepository invites;
    private final HomeTeleporter teleporter;
    private final Notifier notifier;
    private final HomeHops hops;
    private final TeleportHome ownTeleport;

    public VisitHome(
            HomeRepository repository,
            HomeInviteRepository invites,
            HomeTeleporter teleporter,
            Notifier notifier,
            HomeHops hops,
            TeleportHome ownTeleport) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.invites = Objects.requireNonNull(invites, "invites");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.hops = Objects.requireNonNull(hops, "hops");
        this.ownTeleport = Objects.requireNonNull(ownTeleport, "ownTeleport");
    }

    /** Teleport {@code actor} to {@code owner}'s home in {@code slot} when an access gate admits them. */
    public Result<Unit, HomeError> visit(PlayerRef actor, PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        if (actor.uuid().equals(owner.uuid())) {
            return ownTeleport.toSlot(actor, slot);
        }
        Optional<Home> found = repository.findSlot(owner, slot);
        if (found.isEmpty()) {
            notifier.send(actor, HomeError.NOT_FOUND.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.NOT_FOUND);
        }
        Home home = found.get();
        boolean accessible = home.isPublic() || invites.invites(owner, slot).contains(actor.uuid());
        if (!accessible) {
            notifier.send(actor, HomeError.NOT_ACCESSIBLE.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.NOT_ACCESSIBLE);
        }
        if (hops.routes(home)) {
            return hops.travel(actor, home, () -> Result.ok(Optional.empty()));
        }
        notifier.send(actor, HomesMessageKey.HOME_TELEPORTING, slotPlaceholder(slot));
        teleporter.teleportTo(actor, home);
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
