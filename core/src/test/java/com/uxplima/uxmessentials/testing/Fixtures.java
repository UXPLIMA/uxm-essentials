package com.uxplima.uxmessentials.testing;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/** Values shared by the unit tests: players, worlds and a clock that never moves. */
public final class Fixtures {

    public static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final WorldRef WORLD = new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    public static final PlayerRef ALEX = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    public static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");

    private Fixtures() {}

    public static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }
}
