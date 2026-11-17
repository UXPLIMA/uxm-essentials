package com.uxplima.uxmessentials.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.BusSecret;
import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.junit.jupiter.api.Test;

/**
 * Each backend a player reaches hears, sealed with the network secret, whether they switched on the connection they
 * had or logged in anew; a new login is a new connection.
 */
class ProxyConnectionsTest {

    private static final String SECRET = "k7Q2m9Xv4Lp8Rt1Zw6Ny3Bc5Hd0Fg2Js";
    private static final UUID ADA = new UUID(0L, 7L);

    private final Object session = new Object();
    private final Object nextSession = new Object();

    private final AtomicLong next = new AtomicLong();
    private final FrameSeal seal = new FrameSeal(BusSecret.of(SECRET), () -> 1_700_000_000_000L);
    private final ProxyConnections connections = new ProxyConnections(
            seal,
            MinecraftChannelIdentifier.create(BusChannel.NAMESPACE, BusChannel.NAME),
            () -> new UUID(1L, next.incrementAndGet()));

    @Test
    void aSwitchCarriesTheConnectionOfTheLoginBeforeIt() {
        ProxyConnectionFrame login = read(connections.frame(ADA, session, "lobby", false));
        ProxyConnectionFrame moved = read(connections.frame(ADA, session, "survival", true));

        assertThat(login.switched()).isFalse();
        assertThat(login.server()).isEqualTo("lobby");
        assertThat(moved.switched()).isTrue();
        assertThat(moved.server()).isEqualTo("survival");
        assertThat(moved.connection()).isEqualTo(login.connection());
        assertThat(moved.frameId()).isNotEqualTo(login.frameId());
    }

    @Test
    void aNewLoginIsANewConnection() {
        ProxyConnectionFrame first = read(connections.frame(ADA, session, "lobby", false));
        ProxyConnectionFrame again = read(connections.frame(ADA, session, "lobby", false));

        assertThat(again.connection()).isNotEqualTo(first.connection());
    }

    @Test
    void whatTheProxySendsIsSealedWithTheNetworkSecretAndNothingElse() {
        byte[] sent = connections.frame(ADA, session, "lobby", false);
        FrameSeal other = new FrameSeal(BusSecret.of("sJ2gF0dH5cB3yN6wZ1tR8pL4vX9m2Q7k"), () -> 1_700_000_000_000L);

        assertThat(seal.check(sent)).isEqualTo(FrameSeal.Check.TRUSTED);
        assertThat(other.check(sent)).isEqualTo(FrameSeal.Check.SIGNATURE);
    }

    @Test
    void theOldConnectionEndingLateDoesNotDropTheNewOne() {
        read(connections.frame(ADA, session, "lobby", false));
        ProxyConnectionFrame relogged = read(connections.frame(ADA, nextSession, "lobby", false));

        connections.closed(ADA, session);
        ProxyConnectionFrame moved = read(connections.frame(ADA, nextSession, "survival", true));

        assertThat(moved.connection()).isEqualTo(relogged.connection());
    }

    @Test
    void aSwitchOnAConnectionTheProxyNoLongerKnowsIsNotTheOldConnection() {
        ProxyConnectionFrame login = read(connections.frame(ADA, session, "lobby", false));
        connections.closed(ADA, session);

        ProxyConnectionFrame moved = read(connections.frame(ADA, nextSession, "survival", true));

        assertThat(moved.connection()).isNotEqualTo(login.connection());
    }

    private ProxyConnectionFrame read(byte[] sealed) {
        assertThat(seal.check(sealed)).isEqualTo(FrameSeal.Check.TRUSTED);
        return (ProxyConnectionFrame) NetworkMessageCodec.decode(FrameSeal.frame(sealed));
    }
}
