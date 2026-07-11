package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Withdraw pays the bank to the owner (never the triggering co-owner), and no-ops gracefully with no economy. */
class WithdrawEarningsTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Economy economy;
    private PlayerWarpTestSupport.Sink sink;
    private PlayerRef owner;
    private PlayerRef coOwner;
    private PlayerRef manager;
    private PlayerRef stranger;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        economy = new PlayerWarpTestSupport.Economy();
        sink = new PlayerWarpTestSupport.Sink();
        owner = PlayerWarpTestSupport.ref("Owner");
        coOwner = PlayerWarpTestSupport.ref("CoOwner");
        manager = PlayerWarpTestSupport.ref("Manager");
        stranger = PlayerWarpTestSupport.ref("Stranger");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), coOwner.uuid(), WarpRole.CO_OWNER);
        members.grant(warp.id().orElseThrow(), manager.uuid(), WarpRole.MANAGER);
    }

    private WithdrawEarnings withEconomy(Optional<PlayerWarpTestSupport.Economy> maybe) {
        return new WithdrawEarnings(
                repository, new WarpAuthorization(members), PlayerWarpTestSupport.notifier(sink), maybe.map(e ->
                        (com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy) e));
    }

    @Test
    void theOwnerWithdrawsToTheirOwnBalance() {
        Result<Unit, PlayerWarpError> result = withEconomy(Optional.of(economy)).withdraw(owner, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(economy.lastWithdrawWarp).isEqualTo(warp.id().orElseThrow());
        assertThat(economy.lastWithdrawTo).isEqualTo(owner);
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.withdrawn"));
    }

    @Test
    void aCoOwnerWithdrawalStillRoutesToTheOwner() {
        Result<Unit, PlayerWarpError> result = withEconomy(Optional.of(economy)).withdraw(coOwner, HUB);

        assertThat(result.isOk()).isTrue();
        // The bank is the owner's takings: a co-owner may release it, never redirect it to themselves.
        assertThat(economy.lastWithdrawTo).isEqualTo(owner);
        assertThat(economy.lastWithdrawTo).isNotEqualTo(coOwner);
    }

    @Test
    void aManagerMayNotWithdraw() {
        Result<Unit, PlayerWarpError> result = withEconomy(Optional.of(economy)).withdraw(manager, HUB);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(economy.lastWithdrawTo).isNull();
    }

    @Test
    void aStrangerMayNotWithdraw() {
        Result<Unit, PlayerWarpError> result = withEconomy(Optional.of(economy)).withdraw(stranger, HUB);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(economy.lastWithdrawTo).isNull();
    }

    @Test
    void aMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result =
                withEconomy(Optional.of(economy)).withdraw(owner, PlayerWarpName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
        assertThat(economy.lastWithdrawTo).isNull();
    }

    @Test
    void withNoEconomyItIsAGracefulNoOp() {
        Result<Unit, PlayerWarpError> result = withEconomy(Optional.empty()).withdraw(owner, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.nothing-to-withdraw"));
    }

    @Test
    void aProviderFaultSurfacesAsWithdrawFailed() {
        PlayerWarpTestSupport.Economy failing = PlayerWarpTestSupport.Economy.failing(ChargeError.PROVIDER_ERROR);

        Result<Unit, PlayerWarpError> result = withEconomy(Optional.of(failing)).withdraw(owner, HUB);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.WITHDRAW_FAILED);
        // The payout was still targeted at the owner even though the provider rejected it.
        assertThat(failing.lastWithdrawTo).isEqualTo(owner);
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.withdraw-failed"));
    }
}
