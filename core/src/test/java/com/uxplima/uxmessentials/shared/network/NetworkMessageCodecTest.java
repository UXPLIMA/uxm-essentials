package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NetworkMessageCodecTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static Stream<NetworkMessage> messages() {
        return Stream.of(
                new BalanceChanged("survival-1", OWNER, "coins"),
                new HomeChanged("survival-1", OWNER),
                new WarpChanged("lobby-2", "spawn"),
                new VaultChanged("survival-1", OWNER, 3),
                new ServerPing("lobby-2", 1_717_000_000_000L));
    }

    @ParameterizedTest
    @MethodSource("messages")
    void roundTripsEveryVariant(NetworkMessage original) {
        NetworkMessage decoded = NetworkMessageCodec.decode(NetworkMessageCodec.encode(original));

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.originServer()).isEqualTo(original.originServer());
        assertThat(decoded.type()).isEqualTo(original.type());
    }

    @Test
    void everyTypeHasADistinctWireTag() {
        long distinct = Stream.of(NetworkMessage.MessageType.values())
                .map(NetworkMessage.MessageType::wireTag)
                .distinct()
                .count();

        assertThat(distinct).isEqualTo(NetworkMessage.MessageType.values().length);
    }

    @Test
    void rejectsAnEmptyFrame() {
        assertThatThrownBy(() -> NetworkMessageCodec.decode(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownVersion() {
        byte[] frame = NetworkMessageCodec.encode(new HomeChanged("survival-1", OWNER));
        frame[0] = 99;

        assertThatThrownBy(() -> NetworkMessageCodec.decode(frame)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownWireTag() {
        assertThatThrownBy(() -> NetworkMessage.MessageType.fromWireTag((byte) 120))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
