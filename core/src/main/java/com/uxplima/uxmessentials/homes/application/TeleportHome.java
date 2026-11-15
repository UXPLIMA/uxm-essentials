package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.HomesMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports.Settlement;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /home <slot>}: teleport the player to the home in one of their slots. Execution is
 * <em>delegated</em> to the teleport context through {@link HomeTeleporter}. This use case never moves the
 * player itself, so the shared cooldown, the move-cancellable warmup, and the region-aware async hop are all
 * the teleport context's concern. An empty slot is rejected with {@link HomeError#NOT_FOUND}; an optional
 * economy charge is applied after the home is found but before the hop is handed to the teleport context, and comes
 * back once, through {@link PaidTeleports}, when the teleport then never lands (a move during the warmup, a newer
 * teleport, a missed hop, the player leaving).
 *
 * <p>A home set on another backend, while the homes {@code cross-server} block is on, is handed to the shared
 * cross-server hop through {@link HomeHops} instead: the teleport rules, then the charge, then the hop that carries
 * it, so a refused hop costs nothing and a hop that never lands is refunded. Every caller runs this use case on the
 * async lane, since it reads the store, so the hop's own writes to the shared database stay off the tick too.
 */
public final class TeleportHome {

    private final HomeRepository repository;
    private final HomeTeleporter teleporter;
    private final Notifier notifier;
    private final HomeCharge charge;
    private final HomeHops hops;
    private final PaidTeleports paid;

    public TeleportHome(
            HomeRepository repository,
            HomeTeleporter teleporter,
            Notifier notifier,
            HomeCharge charge,
            HomeHops hops,
            PaidTeleports paid) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.charge = Objects.requireNonNull(charge, "charge");
        this.hops = Objects.requireNonNull(hops, "hops");
        this.paid = Objects.requireNonNull(paid, "paid");
    }

    /** Teleport {@code who} to the home in {@code slot}, or reject when the slot is empty or the player cannot afford the charge. */
    public Result<Unit, HomeError> toSlot(PlayerRef who, HomeSlot slot) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(slot, "slot");
        Optional<Home> home = repository.findSlot(who, slot);
        if (home.isEmpty()) {
            notifier.send(who, HomeError.NOT_FOUND.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.NOT_FOUND);
        }
        if (hops.routes(home.get())) {
            return hops.travel(who, home.get(), () -> chargeTeleport(who));
        }
        Result<Optional<HomeCost>, HomeError> charged = chargeTeleport(who);
        if (charged.isErr()) {
            return Result.err(charged.errorOrThrow());
        }
        // Opened before anything else can fail, so a fault between the charge and the launch gives the money back.
        Optional<Settlement> settlement = charged.orElseThrow().map(cost -> settlementFor(who, home.get(), cost));
        try {
            notifier.send(who, HomesMessageKey.HOME_TELEPORTING, slotPlaceholder(slot));
            CompletionStage<TeleportOutcome> outcome = teleporter.teleportTo(who, home.get());
            settlement.ifPresent(open -> open.follow(outcome));
        } catch (RuntimeException failure) {
            settlement.ifPresent(Settlement::refuse);
            throw failure;
        }
        return Result.ok();
    }

    /** Give {@code who} back what the teleport cost them if it does not land, and tell them once. */
    private Settlement settlementFor(PlayerRef who, Home home, HomeCost cost) {
        String label = HomeHops.labelOf(home);
        return paid.open(
                PaidTeleports.subject("home", label, who, cost.amount(), cost.currencyId()),
                () -> charge.refund(who, cost),
                () -> notifier.send(who, HomesMessageKey.HOME_CROSS_SERVER_REFUNDED, Map.of("home", label)));
    }

    /** Take the teleport cost, telling a player who cannot pay; answers exactly what was taken. */
    private Result<Optional<HomeCost>, HomeError> chargeTeleport(PlayerRef who) {
        Result<Optional<HomeCost>, HomeError> charged = charge.chargeExactly(who, HomeChargeKind.TELEPORT);
        if (charged.isErr()) {
            notifier.send(who, HomeError.CANNOT_AFFORD.messageKey());
            return Result.err(HomeError.CANNOT_AFFORD);
        }
        return charged;
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
