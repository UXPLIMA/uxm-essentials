package com.uxplima.uxmessentials.playerwarps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldBlacklist;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * The limit across every world caps all of an owner's warps, and a {@code pwarp.limit.<world>.<n>} node caps their
 * warps in that one world, counted there alone.
 */
class PlayerWarpLimitTest {

    private static final String LIMIT = "uxmessentials.pwarp.limit";
    private static final WorldRef NETHER =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"), "world_nether");

    private final PlayerWarpFakes.Warps warps = new PlayerWarpFakes.Warps();
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();

    private SetPlayerWarp setWarp(int defaultLimit) {
        return new SetPlayerWarp(
                warps,
                new PlayerWarpQuota(permissions, defaultLimit),
                notifier.notifier(),
                new RecordingEvents(),
                CLOCK,
                WorldBlacklist.none(),
                Optional.empty());
    }

    private static Position in(WorldRef world) {
        return new Position(world, 0, 64, 0, 0f, 0f);
    }

    private Result<Unit, PlayerWarpError> set(SetPlayerWarp use, String name, WorldRef world) {
        return use.set(ALEX, "Alex", PlayerWarpName.of(name), in(world));
    }

    @Test
    void aWorldLimitCountsOnlyTheWarpsInThatWorld() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 1);
        SetPlayerWarp use = setWarp(5);
        set(use, "farm", WORLD);
        set(use, "shop", WORLD);

        assertThat(set(use, "fortress", NETHER).isOk())
                .as("two warps in the overworld leave the nether's one free")
                .isTrue();
    }

    @Test
    void aWorldLimitRefusesTheNextWarpInThatWorld() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 1);
        SetPlayerWarp use = setWarp(5);
        set(use, "fortress", NETHER);

        Result<Unit, PlayerWarpError> second = set(use, "bastion", NETHER);

        assertThat(second.asError()).contains(PlayerWarpError.LIMIT_REACHED);
        assertThat(notifier.sent())
                .last()
                .isEqualTo(new RecordingNotifier.Sent(
                        ALEX, "pwarp.world-limit-reached", Map.of("limit", "1", "world", "world_nether")));
        assertThat(set(use, "farm", WORLD).isOk())
                .as("another world is not held back")
                .isTrue();
    }

    @Test
    void aLargeWorldLimitDoesNotLiftTheLimitAcrossEveryWorld() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 10);
        SetPlayerWarp use = setWarp(2);
        set(use, "farm", WORLD);
        set(use, "shop", WORLD);

        Result<Unit, PlayerWarpError> third = set(use, "fortress", NETHER);

        assertThat(third.asError()).contains(PlayerWarpError.LIMIT_REACHED);
        assertThat(notifier.keys()).last().isEqualTo("pwarp.limit-reached");
    }

    @Test
    void aWorldWithNoNodeOfItsOwnIsHeldOnlyByTheLimitAcrossEveryWorld() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 1);
        SetPlayerWarp use = setWarp(3);

        assertThat(set(use, "farm", WORLD).isOk()).isTrue();
        assertThat(set(use, "shop", WORLD).isOk()).isTrue();
        assertThat(set(use, "mine", WORLD).isOk()).isTrue();
        assertThat(set(use, "fortress", NETHER).asError()).contains(PlayerWarpError.LIMIT_REACHED);
    }

    @Test
    void anUnlimitedWorldNodeLeavesOnlyTheLimitAcrossEveryWorld() {
        permissions.quota(LIMIT + ".world_nether", QuotaResult.unlimited());
        SetPlayerWarp use = setWarp(2);

        assertThat(set(use, "fortress", NETHER).isOk()).isTrue();
        assertThat(set(use, "bastion", NETHER).isOk()).isTrue();
        assertThat(set(use, "outpost", NETHER).asError()).contains(PlayerWarpError.LIMIT_REACHED);
    }

    @Test
    void aWorldNodeIsReadWhateverTheCaseOfTheWorldsName() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 1);
        WorldRef shouted = new WorldRef(NETHER.uid(), "World_Nether");
        SetPlayerWarp use = setWarp(5);
        set(use, "fortress", shouted);

        assertThat(set(use, "bastion", shouted).asError()).contains(PlayerWarpError.LIMIT_REACHED);
    }

    private EditPlayerWarp edit(int defaultLimit) {
        PlayerWarpQuota quota = new PlayerWarpQuota(permissions, defaultLimit);
        return new EditPlayerWarp(
                warps,
                new WarpAuthorization(new PlayerWarpFakes.NoMembers()),
                notifier.notifier(),
                new PlayerWarpFakes.Passwords(),
                CLOCK,
                Optional.empty(),
                new PlayerWarpWorldLimit(quota, warps, notifier.notifier(), WorldBlacklist.none()));
    }

    @Test
    void movingWarpsInOneByOneCannotWalkPastAWorldLimit() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 1);
        SetPlayerWarp use = setWarp(10);
        EditPlayerWarp edit = edit(10);
        set(use, "fortress", NETHER);
        set(use, "farm", WORLD);

        Result<Unit, PlayerWarpError> moved = edit.moveHere(ALEX, PlayerWarpName.of("farm"), in(NETHER));

        assertThat(moved.asError()).contains(PlayerWarpError.LIMIT_REACHED);
        assertThat(notifier.keys()).last().isEqualTo("pwarp.world-limit-reached");
        assertThat(warps.findByName(PlayerWarpName.of("farm"))
                        .orElseThrow()
                        .location()
                        .world())
                .isEqualTo(WORLD);
    }

    @Test
    void reAnchoringAWarpIntoAFullWorldIsRefused() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 1);
        SetPlayerWarp use = setWarp(10);
        set(use, "fortress", NETHER);
        set(use, "farm", WORLD);

        Result<Unit, PlayerWarpError> moved = set(use, "farm", NETHER);

        assertThat(moved.asError()).contains(PlayerWarpError.LIMIT_REACHED);
        assertThat(warps.findByName(PlayerWarpName.of("farm"))
                        .orElseThrow()
                        .location()
                        .world())
                .isEqualTo(WORLD);
    }

    @Test
    void aWarpMovesWithinAFullWorld() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 1);
        SetPlayerWarp use = setWarp(10);
        set(use, "fortress", NETHER);

        assertThat(edit(10).moveHere(ALEX, PlayerWarpName.of("fortress"), new Position(NETHER, 50, 70, 50, 0f, 0f))
                        .isOk())
                .isTrue();
        assertThat(set(use, "fortress", NETHER).isOk()).isTrue();
    }

    @Test
    void aWarpMovesIntoAWorldWithRoomLeft() {
        permissions.holdTier(ALEX, LIMIT + ".world_nether", 2);
        SetPlayerWarp use = setWarp(10);
        set(use, "fortress", NETHER);
        set(use, "farm", WORLD);

        assertThat(edit(10).moveHere(ALEX, PlayerWarpName.of("farm"), in(NETHER))
                        .isOk())
                .isTrue();
    }

    private TransferPlayerWarp transfer(int defaultLimit) {
        PlayerWarpQuota quota = new PlayerWarpQuota(permissions, defaultLimit);
        return new TransferPlayerWarp(
                warps,
                new WarpAuthorization(new PlayerWarpFakes.NoMembers()),
                quota,
                new PlayerWarpWorldLimit(quota, warps, notifier.notifier(), WorldBlacklist.none()),
                notifier.notifier(),
                CLOCK);
    }

    private Result<Unit, PlayerWarpError> setFor(PlayerRef owner, String name, WorldRef world) {
        return setWarp(5).set(owner, owner.name(), PlayerWarpName.of(name), in(world));
    }

    @Test
    void aTransferThatWouldPutTheNewOwnerPastTheirLimitAcrossEveryWorldIsRefused() {
        setFor(SAM, "farm", WORLD);
        setFor(SAM, "shop", WORLD);
        setFor(ALEX, "fortress", NETHER);

        Result<Unit, PlayerWarpError> moved = transfer(2).transfer(ALEX, PlayerWarpName.of("fortress"), SAM);

        assertThat(moved.asError()).contains(PlayerWarpError.LIMIT_REACHED);
        assertThat(notifier.sent())
                .last()
                .isEqualTo(new RecordingNotifier.Sent(
                        ALEX, "pwarp.transfer-limit-reached", Map.of("warp", "fortress", "player", "Sam")));
        assertThat(warps.ownedBy(ALEX)).hasSize(1);
    }

    @Test
    void aTransferIntoAWorldWhereTheNewOwnerIsFullIsRefusedAndAnotherWorldIsNot() {
        permissions.holdTier(SAM, LIMIT + ".world_nether", 1);
        setFor(SAM, "bastion", NETHER);
        setFor(ALEX, "fortress", NETHER);
        setFor(ALEX, "farm", WORLD);

        assertThat(transfer(5)
                        .transfer(ALEX, PlayerWarpName.of("fortress"), SAM)
                        .asError())
                .contains(PlayerWarpError.LIMIT_REACHED);
        assertThat(transfer(5).transfer(ALEX, PlayerWarpName.of("farm"), SAM).isOk())
                .isTrue();
        assertThat(warps.ownedBy(SAM)).hasSize(2);
    }
}
