package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class SpawnCodecTest {

    private final WorldRef world = new WorldRef(UUID.randomUUID(), "w");

    @Test
    void encodeThenDecodeRoundTrips() {
        var pos = new Position(world, 10.5, 64, 20.5, 90f, 45f);
        var decoded = SpawnCodec.decode(SpawnCodec.encode(pos), world).orElseThrow();
        assertThat(decoded.x()).isEqualTo(10.5);
        assertThat(decoded.y()).isEqualTo(64.0);
        assertThat(decoded.z()).isEqualTo(20.5);
        assertThat(decoded.yaw()).isEqualTo(90f);
        assertThat(decoded.pitch()).isEqualTo(45f);
    }

    @Test
    void encodeThenParseComponentsRoundTrips() {
        var pos = new Position(world, 10.5, 64, 20.5, 90f, 45f);
        double[] parts = SpawnCodec.parseComponents(SpawnCodec.encode(pos)).orElseThrow();
        assertThat(parts).containsExactly(10.5, 64.0, 20.5, 90.0, 45.0);
    }

    @Test
    void parseComponentsRejectsFourPartString() {
        assertThat(SpawnCodec.parseComponents("1;2;3;4")).isEmpty();
    }

    @Test
    void decodeRejectsFourPartString() {
        assertThat(SpawnCodec.decode("1;2;3;4", world)).isEmpty();
    }
}
