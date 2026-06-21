package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import org.junit.jupiter.api.Test;

/**
 * Parse coverage of {@link NetworkConfig} against a map-backed {@link ConfigStore}. The two settings this task
 * adds are the bus transport selector and the canonical {@code network.redis} connection block; the existing
 * {@code enabled}/{@code server-id}/{@code bus-channel} keys keep their B2 defaults so the velocity path is
 * unchanged.
 *
 * <ul>
 *   <li>{@code network.transport} parses {@code velocity}/{@code redis}/{@code both}, case-insensitively, and
 *       defaults to {@code velocity} when absent so an existing install opens no Redis connection;
 *   <li>an unrecognised {@code transport} value resolves to {@code velocity} and is flagged as unrecognised so
 *       the wiring can WARN rather than crash enable;
 *   <li>the {@code network.redis} keys parse with sensible localhost defaults.
 * </ul>
 */
class NetworkConfigTest {

    @Test
    void transportDefaultsToVelocityWhenAbsent() {
        NetworkConfig config = NetworkConfig.from(new FixedConfig(Map.of()));

        assertThat(config.transport()).isEqualTo(NetworkConfig.Transport.VELOCITY);
        assertThat(config.transportRecognized()).isTrue();
    }

    @Test
    void transportParsesVelocityRedisAndBoth() {
        assertThat(NetworkConfig.from(new FixedConfig(Map.of("network.transport", "velocity")))
                        .transport())
                .isEqualTo(NetworkConfig.Transport.VELOCITY);
        assertThat(NetworkConfig.from(new FixedConfig(Map.of("network.transport", "redis")))
                        .transport())
                .isEqualTo(NetworkConfig.Transport.REDIS);
        assertThat(NetworkConfig.from(new FixedConfig(Map.of("network.transport", "both")))
                        .transport())
                .isEqualTo(NetworkConfig.Transport.BOTH);
    }

    @Test
    void transportParsingIsCaseInsensitiveAndTrimmed() {
        assertThat(NetworkConfig.from(new FixedConfig(Map.of("network.transport", "  Redis ")))
                        .transport())
                .isEqualTo(NetworkConfig.Transport.REDIS);
    }

    @Test
    void anUnrecognisedTransportFallsBackToVelocityAndIsFlagged() {
        NetworkConfig config = NetworkConfig.from(new FixedConfig(Map.of("network.transport", "kafka")));

        assertThat(config.transport()).isEqualTo(NetworkConfig.Transport.VELOCITY);
        assertThat(config.transportRecognized())
                .as("an unknown transport is reported so the wiring can WARN")
                .isFalse();
    }

    @Test
    void redisKeysDefaultToLocalhost() {
        NetworkConfig.Redis redis =
                NetworkConfig.from(new FixedConfig(Map.of())).redis();

        assertThat(redis.host()).isEqualTo("127.0.0.1");
        assertThat(redis.port()).isEqualTo(6379);
        assertThat(redis.password()).isEmpty();
        assertThat(redis.channel()).isEqualTo("uxmessentials:bus");
        assertThat(redis.db()).isZero();
    }

    @Test
    void redisKeysParseFromTheNetworkRedisSubtree() {
        NetworkConfig.Redis redis = NetworkConfig.from(new FixedConfig(Map.of(
                        "network.redis.host", "10.0.0.5",
                        "network.redis.port", 6380,
                        "network.redis.password", "secret",
                        "network.redis.channel", "custom:bus",
                        "network.redis.db", 3)))
                .redis();

        assertThat(redis.host()).isEqualTo("10.0.0.5");
        assertThat(redis.port()).isEqualTo(6380);
        assertThat(redis.password()).isEqualTo("secret");
        assertThat(redis.channel()).isEqualTo("custom:bus");
        assertThat(redis.db()).isEqualTo(3);
    }

    @Test
    void theExistingVelocityKeysKeepTheirDefaults() {
        NetworkConfig config = NetworkConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isFalse();
        assertThat(config.serverId()).isEqualTo("server-1");
        assertThat(config.channel()).isEqualTo(BusChannel.FULL);
        assertThat(config.outboundQueueSize()).isEqualTo(256);
    }

    /** A map-backed {@link ConfigStore} addressing keys by their absolute dotted path. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }
}
