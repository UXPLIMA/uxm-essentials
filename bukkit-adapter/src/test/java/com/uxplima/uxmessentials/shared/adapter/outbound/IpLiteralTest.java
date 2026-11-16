package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

/**
 * A typed address is stored in the form a connecting player's address is read in, so a ban matches the player
 * however the address was written.
 */
class IpLiteralTest {

    @Test
    void everySpellingOfAnIpv6AddressMatchesThePlayerOnIt() throws UnknownHostException {
        String player = connectedFrom(new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7});

        assertThat(IpLiteral.canonical("2001:DB8::7")).contains(player);
        assertThat(IpLiteral.canonical("2001:db8:0:0:0:0:0:7")).contains(player);
        assertThat(IpLiteral.canonical("2001:0db8:0000::0007")).contains(player);
    }

    @Test
    void anIpv4AddressMatchesThePlayerOnIt() throws UnknownHostException {
        String player = connectedFrom(new byte[] {(byte) 203, 0, 113, 7});

        assertThat(IpLiteral.canonical("203.0.113.7")).contains(player);
        assertThat(IpLiteral.canonical("::ffff:203.0.113.7")).contains(player);
    }

    @Test
    void textThatIsNotALiteralIsRefusedWithoutALookup() {
        assertThat(IpLiteral.canonical("localhost")).isEmpty();
        assertThat(IpLiteral.canonical("play.example.com")).isEmpty();
        assertThat(IpLiteral.canonical("203.0.113")).isEmpty();
        assertThat(IpLiteral.canonical("010.000.113.007")).isEmpty();
    }

    /** The text the login check and the live kick read for a player connected from {@code address}. */
    private static String connectedFrom(byte[] address) throws UnknownHostException {
        return InetAddress.getByAddress(address).getHostAddress();
    }
}
