package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The ban verbs: a manager may run them, the owner is never bannable, and the BanRecord shape is exact. */
class ManageBansTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Bans bans;
    private PlayerWarpTestSupport.Sink sink;
    private ManageBans manage;
    private PlayerRef owner;
    private PlayerRef manager;
    private PlayerRef stranger;
    private PlayerRef target;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        bans = new PlayerWarpTestSupport.Bans();
        sink = new PlayerWarpTestSupport.Sink();
        manage = new ManageBans(
                repository,
                new WarpAuthorization(members),
                bans,
                PlayerWarpTestSupport.notifier(sink),
                PlayerWarpTestSupport.CLOCK);
        owner = PlayerWarpTestSupport.ref("Owner");
        manager = PlayerWarpTestSupport.ref("Manager");
        stranger = PlayerWarpTestSupport.ref("Stranger");
        target = PlayerWarpTestSupport.ref("Griefer");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), manager.uuid(), WarpRole.MANAGER);
    }

    @Test
    void aManagerImposesAPermanentBanWithTheRightShape() {
        Result<Unit, PlayerWarpError> result =
                manage.ban(manager, HUB, target, Optional.empty(), Optional.of("griefing"));

        assertThat(result.isOk()).isTrue();
        BanRecord record = Objects.requireNonNull(bans.lastBan);
        assertThat(record.player()).isEqualTo(target.uuid());
        // A permanent ban never lifts: the until instant is absent.
        assertThat(record.until()).isEmpty();
        assertThat(record.reason()).contains("griefing");
        assertThat(record.bannedBy()).contains(manager.uuid());
        assertThat(record.bannedAt()).isEqualTo(PlayerWarpTestSupport.CLOCK.instant());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.ban-set"));
    }

    @Test
    void aTimedBanStoresTheAbsoluteExpiry() {
        Result<Unit, PlayerWarpError> result =
                manage.ban(owner, HUB, target, Optional.of(Duration.ofHours(2)), Optional.empty());

        assertThat(result.isOk()).isTrue();
        BanRecord record = Objects.requireNonNull(bans.lastBan);
        assertThat(record.until())
                .contains(PlayerWarpTestSupport.CLOCK.instant().plus(Duration.ofHours(2)));
        assertThat(record.reason()).isEmpty();
        assertThat(record.bannedBy()).contains(owner.uuid());
    }

    @Test
    void theOwnerCannotBeBanned() {
        Result<Unit, PlayerWarpError> result = manage.ban(manager, HUB, owner, Optional.empty(), Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.CANNOT_TARGET_OWNER);
        assertThat(bans.lastBan).isNull();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.cannot-target-owner"));
    }

    @Test
    void aStrangerMayNotBan() {
        Result<Unit, PlayerWarpError> result = manage.ban(stranger, HUB, target, Optional.empty(), Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(bans.lastBan).isNull();
    }

    @Test
    void theOwnerLiftsABan() {
        bans.ban(
                warp.id().orElseThrow(),
                new BanRecord(
                        target.uuid(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(owner.uuid()),
                        PlayerWarpTestSupport.CLOCK.instant()));

        Result<Unit, PlayerWarpError> result = manage.unban(owner, HUB, target);

        assertThat(result.isOk()).isTrue();
        assertThat(bans.lastUnban).isEqualTo(target.uuid());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.ban-lifted"));
    }

    @Test
    void banningOnAMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result =
                manage.ban(owner, PlayerWarpName.of("ghost"), target, Optional.empty(), Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
    }
}
