package com.uxplima.uxmessentials.playerwarps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
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
 * A world on the playerwarps {@code world-blacklist} is refused to every move, by command, menu or API, as it is to a
 * create.
 */
class PlayerWarpWorldBlacklistTest {

    private static final PlayerWarpName FARM = PlayerWarpName.of("farm");
    private static final WorldRef ARENA =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000cccc"), "arena");
    private static final WorldBlacklist NO_ARENA = WorldBlacklist.of(List.of("arena"));

    private final PlayerWarpFakes.Warps warps = new PlayerWarpFakes.Warps();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final PlayerWarpQuota quota = new PlayerWarpQuota(new FakePermissions(), 10);
    private final PlayerWarpWorldLimit worlds = new PlayerWarpWorldLimit(quota, warps, notifier.notifier(), NO_ARENA);
    private final SetPlayerWarp create = new SetPlayerWarp(
            warps, quota, notifier.notifier(), new RecordingEvents(), CLOCK, NO_ARENA, Optional.empty());
    private final EditPlayerWarp edit = new EditPlayerWarp(
            warps,
            new WarpAuthorization(new PlayerWarpFakes.NoMembers()),
            notifier.notifier(),
            new PlayerWarpFakes.Passwords(),
            CLOCK,
            Optional.empty(),
            worlds);

    PlayerWarpWorldBlacklistTest() {
        create.set(ALEX, "Alex", FARM, in(WORLD));
    }

    private static Position in(WorldRef world) {
        return new Position(world, 0, 64, 0, 0f, 0f);
    }

    private PlayerWarp farm() {
        return warps.findByName(FARM).orElseThrow();
    }

    @Test
    void aMoveIntoABarredWorldIsRefusedWithTheCreateMessage() {
        Result<Unit, PlayerWarpError> moved = edit.moveHere(ALEX, FARM, in(ARENA));

        assertThat(moved.asError()).contains(PlayerWarpError.WORLD_BLACKLISTED);
        assertThat(notifier.sent())
                .last()
                .isEqualTo(new RecordingNotifier.Sent(ALEX, "pwarp.world-blacklisted", Map.of("world", "arena")));
        assertThat(farm().location().world()).as("the warp stays where it was").isEqualTo(WORLD);
    }

    @Test
    void aMoveAndACreateRefuseTheSameWorldTheSameWay() {
        edit.moveHere(ALEX, FARM, in(ARENA));
        RecordingNotifier.Sent fromMove = notifier.sent().getLast();

        Result<Unit, PlayerWarpError> created = create.set(ALEX, "Alex", PlayerWarpName.of("pit"), in(ARENA));

        assertThat(created.asError()).contains(PlayerWarpError.WORLD_BLACKLISTED);
        assertThat(notifier.sent()).last().isEqualTo(fromMove);
    }

    @Test
    void theMenusMoveCheckRefusesABarredWorld() {
        assertThat(worlds.admitsMove(ALEX, farm(), ARENA)).isFalse();
        assertThat(notifier.keys()).last().isEqualTo("pwarp.world-blacklisted");
    }

    @Test
    void aMoveIntoAnotherWorldStillLands() {
        WorldRef plots = new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000dddd"), "plots");

        assertThat(edit.moveHere(ALEX, FARM, in(plots)).isOk()).isTrue();
        assertThat(farm().location().world()).isEqualTo(plots);
    }
}
