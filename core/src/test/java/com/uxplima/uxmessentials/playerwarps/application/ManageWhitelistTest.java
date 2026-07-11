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

/** The whitelist verbs: a manager may run them, a stranger may not, and add/remove hit the whitelist store. */
class ManageWhitelistTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Whitelist whitelist;
    private PlayerWarpTestSupport.Sink sink;
    private ManageWhitelist manage;
    private PlayerRef owner;
    private PlayerRef manager;
    private PlayerRef stranger;
    private PlayerRef target;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        whitelist = new PlayerWarpTestSupport.Whitelist();
        sink = new PlayerWarpTestSupport.Sink();
        manage = new ManageWhitelist(
                repository, new WarpAuthorization(members), whitelist, PlayerWarpTestSupport.notifier(sink));
        owner = PlayerWarpTestSupport.ref("Owner");
        manager = PlayerWarpTestSupport.ref("Manager");
        stranger = PlayerWarpTestSupport.ref("Stranger");
        target = PlayerWarpTestSupport.ref("Guest");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), manager.uuid(), WarpRole.MANAGER);
    }

    @Test
    void aManagerWhitelistsAPlayer() {
        Result<Unit, PlayerWarpError> result = manage.whitelist(manager, HUB, target);

        assertThat(result.isOk()).isTrue();
        assertThat(whitelist.contains(warp.id().orElseThrow(), target.uuid())).isTrue();
        assertThat(whitelist.lastAdded).isEqualTo(target.uuid());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.whitelist-added"));
    }

    @Test
    void theOwnerUnwhitelistsAPlayer() {
        whitelist.add(warp.id().orElseThrow(), target.uuid());

        Result<Unit, PlayerWarpError> result = manage.unwhitelist(owner, HUB, target);

        assertThat(result.isOk()).isTrue();
        assertThat(whitelist.contains(warp.id().orElseThrow(), target.uuid())).isFalse();
        assertThat(whitelist.lastRemoved).isEqualTo(target.uuid());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.whitelist-removed"));
    }

    @Test
    void aStrangerMayNotWhitelist() {
        Result<Unit, PlayerWarpError> result = manage.whitelist(stranger, HUB, target);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(whitelist.contains(warp.id().orElseThrow(), target.uuid())).isFalse();
    }

    @Test
    void whitelistingOnAMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result = manage.whitelist(owner, PlayerWarpName.of("ghost"), target);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
    }
}
