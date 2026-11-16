package com.uxplima.uxmessentials.warps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldBlacklist;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/** A world on the warps {@code world-blacklist} is refused to a move as it is to a create. */
class WarpWorldBlacklistTest {

    private static final WarpName MARKET = WarpName.of("market");
    private static final WorldRef ARENA =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000cccc"), "arena");
    private static final WorldBlacklist NO_ARENA = WorldBlacklist.of(List.of("arena"));

    private final WarpFakes.Warps warps = new WarpFakes.Warps();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final MoveWarp move = new MoveWarp(warps, notifier.notifier(), NO_ARENA, Optional.empty());
    private final SetWarp create =
            new SetWarp(warps, notifier.notifier(), new RecordingEvents(), CLOCK, NO_ARENA, Optional.empty());

    WarpWorldBlacklistTest() {
        warps.put(Warp.create(MARKET, Position.of(WORLD, 0, 64, 0), SAM, NOW));
    }

    private WorldRef worldOf(WarpName name) {
        return warps.find(name).orElseThrow().location().world();
    }

    @Test
    void aMoveIntoABarredWorldIsRefusedWithTheCreateMessage() {
        Result<Unit, WarpError> moved = move.move(SAM, MARKET, Position.of(ARENA, 5, 70, 5));

        assertThat(moved.asError()).contains(WarpError.WORLD_BLACKLISTED);
        assertThat(notifier.sent())
                .last()
                .isEqualTo(new RecordingNotifier.Sent(SAM, "warp.world-blacklisted", Map.of("world", "arena")));
        assertThat(worldOf(MARKET)).as("the warp stays where it was").isEqualTo(WORLD);
    }

    @Test
    void aMoveAndACreateRefuseTheSameWorldTheSameWay() {
        move.move(SAM, MARKET, Position.of(ARENA, 5, 70, 5));
        RecordingNotifier.Sent fromMove = notifier.sent().getLast();

        Result<Unit, WarpError> created = create.set(SAM, WarpName.of("pit"), Position.of(ARENA, 5, 70, 5));

        assertThat(created.asError()).contains(WarpError.WORLD_BLACKLISTED);
        assertThat(notifier.sent()).last().isEqualTo(fromMove);
    }

    @Test
    void aMoveIntoAnotherWorldStillLands() {
        WorldRef plots = new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000dddd"), "plots");

        assertThat(move.move(SAM, MARKET, Position.of(plots, 1, 64, 1)).isOk()).isTrue();
        assertThat(worldOf(MARKET)).isEqualTo(plots);
    }

    @Test
    void aWorldIsMatchedByItsExactName() {
        WorldRef shouted = new WorldRef(ARENA.uid(), "Arena");

        assertThat(move.move(SAM, MARKET, Position.of(shouted, 1, 64, 1)).isOk())
                .as("the list names arena, not Arena")
                .isTrue();
    }
}
