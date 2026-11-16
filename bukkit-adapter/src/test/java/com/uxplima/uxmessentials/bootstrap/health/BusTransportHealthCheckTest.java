package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusHealth;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import org.junit.jupiter.api.Test;

/** The bus line says connected only when frames come back from the network, and names what is missing otherwise. */
class BusTransportHealthCheckTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final Duration WINDOW = Duration.ofSeconds(90);

    private static final BusHealth.ProxyLink NOT_STARTED = link(false, OptionalLong.empty(), false);
    private static final BusHealth.ProxyLink NEVER_ANSWERED = link(true, OptionalLong.empty(), false);
    private static final BusHealth.ProxyLink NOBODY_ONLINE = link(true, OptionalLong.empty(), true);
    private static final BusHealth.ProxyLink NO_SECRET =
            new BusHealth.ProxyLink(false, false, OptionalLong.empty(), false, 0, OptionalLong.empty());

    private static BusHealth.ProxyLink link(boolean registered, OptionalLong answer, boolean waitingForPlayer) {
        return new BusHealth.ProxyLink(true, registered, answer, waitingForPlayer, 0, OptionalLong.empty());
    }

    private static BusHealth.ProxyLink answered(long millisAgo) {
        return link(true, OptionalLong.of(NOW - millisAgo), false);
    }

    private static BusHealth.ProxyLink answeredButRefused(long refusedMillisAgo) {
        return new BusHealth.ProxyLink(
                true, true, OptionalLong.of(NOW - 5_000), false, 3, OptionalLong.of(NOW - refusedMillisAgo));
    }

    private static HealthResult check(String transport, Optional<BusHealth.ProxyLink> proxy, Optional<Boolean> redis) {
        BusHealth health = new FakeBusHealth(true, transport, proxy, redis);
        return new BusTransportHealthCheck(health, () -> NOW).check();
    }

    @Test
    void aDisabledBusIsReportedAsLocalOnly() {
        BusHealth health = new FakeBusHealth(false, "velocity", Optional.of(NEVER_ANSWERED), Optional.empty());

        HealthResult result = new BusTransportHealthCheck(health, () -> NOW).check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("disabled");
    }

    @Test
    void aRegisteredChannelWithNoAnswerFromTheProxyIsNotConnected() {
        HealthResult result = check("velocity", Optional.of(NEVER_ANSWERED), Optional.empty());

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message())
                .contains("uxmEssentials-velocity", "update it to the same release")
                .doesNotContain("connected");
    }

    @Test
    void onTheProxyAloneNobodyOnlineIsAWarningThatSaysSoFirst() {
        HealthResult result = check("velocity", Optional.of(NOBODY_ONLINE), Optional.empty());

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message())
                .startsWith("enabled, transport=velocity, proxy idle: no player online")
                .doesNotContain("uxmEssentials-velocity");
    }

    @Test
    void withBothCarriersAProxyIdleForWantOfAPlayerIsNoFaultWhileRedisCarries() {
        HealthResult carried = check("both", Optional.of(NOBODY_ONLINE), Optional.of(true));
        HealthResult uncarried = check("both", Optional.of(NOBODY_ONLINE), Optional.of(false));

        assertThat(carried.status()).isEqualTo(HealthStatus.OK);
        assertThat(carried.message()).contains("proxy idle: no player online", "redis connected");
        assertThat(uncarried.status()).isEqualTo(HealthStatus.WARN);
    }

    @Test
    void aProxyThatAnsweredWithinTheWindowIsConnected() {
        HealthResult result = check("velocity", Optional.of(answered(12_000)), Optional.empty());

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("proxy connected", "12s ago");
    }

    @Test
    void aProxyThatFellSilentLongerThanTheWindowIsNotConnected() {
        HealthResult result = check("velocity", Optional.of(answered(WINDOW.toMillis() + 1)), Optional.empty());

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("NOT ANSWERING for 90s");
    }

    @Test
    void aBusThatHasNotStartedIsNotConnected() {
        HealthResult result = check("velocity", Optional.of(NOT_STARTED), Optional.empty());

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("not registered");
    }

    @Test
    void withoutASecretTheProxyCarrierIsOffAndTheLineNamesTheKey() {
        HealthResult result = check("velocity", Optional.of(NO_SECRET), Optional.empty());

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("proxy OFF", "network.bus.secret").doesNotContain("not registered");
    }

    @Test
    void framesRefusedWithinTheWindowAreAWarningEvenWhileTheProxyAnswers() {
        HealthResult recent = check("velocity", Optional.of(answeredButRefused(10_000)), Optional.empty());
        HealthResult old = check("velocity", Optional.of(answeredButRefused(WINDOW.toMillis() + 1)), Optional.empty());

        assertThat(recent.status()).isEqualTo(HealthStatus.WARN);
        assertThat(recent.message())
                .contains("proxy connected", "REFUSED 3 frame(s), last 10s ago", "network.bus.secret");
        assertThat(old.status()).isEqualTo(HealthStatus.OK);
        assertThat(old.message()).doesNotContain("REFUSED");
    }

    @Test
    void redisIsConnectedWhileItsConnectionIsUp() {
        assertThat(check("redis", Optional.empty(), Optional.of(true)).status()).isEqualTo(HealthStatus.OK);

        HealthResult down = check("redis", Optional.empty(), Optional.of(false));
        assertThat(down.status()).isEqualTo(HealthStatus.WARN);
        assertThat(down.message()).contains("redis DISCONNECTED");
    }

    @Test
    void withBothCarriersALiveRedisDoesNotHideASilentProxy() {
        HealthResult result = check("both", Optional.of(NEVER_ANSWERED), Optional.of(true));

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("redis connected", "proxy NOT ANSWERING");
    }

    @Test
    void withBothCarriersWorkingTheLineIsOk() {
        HealthResult result = check("both", Optional.of(answered(5_000)), Optional.of(true));

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("proxy connected", "redis connected");
    }

    private record FakeBusHealth(
            boolean enabled, String transport, Optional<BusHealth.ProxyLink> proxy, Optional<Boolean> redisConnected)
            implements BusHealth {

        @Override
        public Duration answerWindow() {
            return WINDOW;
        }
    }
}
