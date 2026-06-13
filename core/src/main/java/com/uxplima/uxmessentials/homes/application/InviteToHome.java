package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /home invite <slot> <player>}: grant another player access to one of the owner's homes, so they can
 * visit it even while it stays private. A slot the owner has no home in is rejected with
 * {@link HomeError#NOT_FOUND}; otherwise the invited player's UUID is added to the home's guest list and the
 * owner is told who was invited.
 */
public final class InviteToHome {

    private final HomeRepository repository;
    private final HomeInviteRepository invites;
    private final HomeNotifier notifier;

    public InviteToHome(HomeRepository repository, HomeInviteRepository invites, HomeNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.invites = Objects.requireNonNull(invites, "invites");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Invite {@code invited} to {@code owner}'s home in {@code slot}, or reject when the slot is empty. */
    public Result<Unit, HomeError> invite(PlayerRef owner, HomeSlot slot, PlayerRef invited) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(invited, "invited");
        if (repository.findSlot(owner, slot).isEmpty()) {
            notifier.send(owner, HomeError.NOT_FOUND.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.NOT_FOUND);
        }
        invites.addInvite(owner, slot, invited.uuid());
        notifier.send(
                owner,
                HomesMessageKey.HOME_INVITED,
                Map.of("player", invited.name(), "slot", Integer.toString(slot.displayNumber())));
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
