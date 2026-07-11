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

/** Member management is owner-only; a manager cannot promote delegates, and an owner role or the owner is refused. */
class ManageMembersTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Sink sink;
    private ManageMembers manage;
    private PlayerRef owner;
    private PlayerRef manager;
    private PlayerRef stranger;
    private PlayerRef target;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        sink = new PlayerWarpTestSupport.Sink();
        manage = new ManageMembers(
                repository,
                new WarpAuthorization(members),
                members,
                PlayerWarpTestSupport.notifier(sink),
                PlayerWarpTestSupport.CLOCK);
        owner = PlayerWarpTestSupport.ref("Owner");
        manager = PlayerWarpTestSupport.ref("Manager");
        stranger = PlayerWarpTestSupport.ref("Stranger");
        target = PlayerWarpTestSupport.ref("Recruit");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), manager.uuid(), WarpRole.MANAGER);
    }

    @Test
    void ownerAddsACoOwnerAndItIsStored() {
        Result<Unit, PlayerWarpError> result = manage.addMember(owner, HUB, target, WarpRole.CO_OWNER);

        assertThat(result.isOk()).isTrue();
        assertThat(members.roleOf(warp.id().orElseThrow(), target.uuid())).contains(WarpRole.CO_OWNER);
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.member-added"));
        assertThat(sink.delivered).anyMatch(text -> text.contains("Recruit"));
    }

    @Test
    void ownerRemovesAMember() {
        members.grant(warp.id().orElseThrow(), target.uuid(), WarpRole.MANAGER);

        Result<Unit, PlayerWarpError> result = manage.removeMember(owner, HUB, target);

        assertThat(result.isOk()).isTrue();
        assertThat(members.roleOf(warp.id().orElseThrow(), target.uuid())).isEmpty();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.member-removed"));
    }

    @Test
    void aManagerMayNotAddMembers() {
        Result<Unit, PlayerWarpError> result = manage.addMember(manager, HUB, target, WarpRole.MANAGER);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(members.roleOf(warp.id().orElseThrow(), target.uuid())).isEmpty();
    }

    @Test
    void aStrangerIsDenied() {
        Result<Unit, PlayerWarpError> result = manage.addMember(stranger, HUB, target, WarpRole.MANAGER);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
    }

    @Test
    void grantingTheOwnerRoleIsRefused() {
        Result<Unit, PlayerWarpError> result = manage.addMember(owner, HUB, target, WarpRole.OWNER);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.INVALID_ROLE);
        assertThat(members.roleOf(warp.id().orElseThrow(), target.uuid())).isEmpty();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.invalid-role"));
    }

    @Test
    void addingTheWarpOwnerAsAMemberIsRefused() {
        Result<Unit, PlayerWarpError> result = manage.addMember(owner, HUB, owner, WarpRole.CO_OWNER);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.CANNOT_TARGET_OWNER);
        assertThat(members.roleOf(warp.id().orElseThrow(), owner.uuid())).isEmpty();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.cannot-target-owner"));
    }

    @Test
    void managingAMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result =
                manage.addMember(owner, PlayerWarpName.of("ghost"), target, WarpRole.MANAGER);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
    }
}
