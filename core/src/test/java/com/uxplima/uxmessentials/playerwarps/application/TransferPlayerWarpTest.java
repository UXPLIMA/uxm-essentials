package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Transfer is owner-only by the capability matrix; a delegate can never move the warp out from under its owner. */
class TransferPlayerWarpTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Sink sink;
    private TransferPlayerWarp transfer;
    private PlayerRef owner;
    private PlayerRef coOwner;
    private PlayerRef newOwner;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        sink = new PlayerWarpTestSupport.Sink();
        transfer = new TransferPlayerWarp(
                repository,
                new WarpAuthorization(members),
                PlayerWarpTestSupport.notifier(sink),
                PlayerWarpTestSupport.CLOCK);
        owner = PlayerWarpTestSupport.ref("Owner");
        coOwner = PlayerWarpTestSupport.ref("CoOwner");
        newOwner = PlayerWarpTestSupport.ref("Heir");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), coOwner.uuid(), WarpRole.CO_OWNER);
    }

    @Test
    void theOwnerTransfersOwnershipInPlace() {
        Result<Unit, PlayerWarpError> result = transfer.transfer(owner, HUB, newOwner);

        assertThat(result.isOk()).isTrue();
        PlayerWarp saved = repository.stored("hub");
        assertThat(saved.owner().uuid()).isEqualTo(newOwner.uuid());
        assertThat(saved.ownerName()).isEqualTo("Heir");
        assertThat(saved.id()).isEqualTo(warp.id());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.transferred"));
    }

    @Test
    void aCoOwnerMayNotTransfer() {
        Result<Unit, PlayerWarpError> result = transfer.transfer(coOwner, HUB, newOwner);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(repository.stored("hub").owner().uuid()).isEqualTo(owner.uuid());
    }

    @Test
    void transferringAMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result = transfer.transfer(owner, PlayerWarpName.of("ghost"), newOwner);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
    }
}
