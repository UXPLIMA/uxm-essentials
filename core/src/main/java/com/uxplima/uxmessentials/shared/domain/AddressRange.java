package com.uxplima.uxmessentials.shared.domain;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;

/**
 * One address, or a range of them written as an address and the number of leading bits a match shares with it
 * ({@code 203.0.113.7}, {@code 198.51.100.0/24}, {@code 2001:db8::/32}).
 *
 * <p>Only an address literal counts. Nothing here resolves a host name, so reading a configured entry never waits on
 * DNS.
 */
public final class AddressRange {

    private final InetAddress base;
    private final byte[] network;
    private final int prefix;

    private AddressRange(InetAddress base, int prefix) {
        this.base = base;
        this.network = base.getAddress();
        this.prefix = prefix;
    }

    /** {@code entry} as a range, or empty when it is not an address literal with an optional prefix length. */
    public static Optional<AddressRange> parse(String entry) {
        Objects.requireNonNull(entry, "entry");
        String text = entry.strip();
        int slash = text.indexOf('/');
        Optional<InetAddress> base = literal(slash < 0 ? text : text.substring(0, slash));
        if (base.isEmpty()) {
            return Optional.empty();
        }
        byte[] bytes = base.get().getAddress();
        int bits = bytes.length * 8;
        int prefix = bits;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(text.substring(slash + 1));
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        }
        return prefix < 0 || prefix > bits ? Optional.empty() : Optional.of(new AddressRange(base.get(), prefix));
    }

    /** {@code address} read as a literal, or empty when it is anything else (a host name, a blank). */
    public static Optional<InetAddress> literal(String address) {
        Objects.requireNonNull(address, "address");
        try {
            return Optional.of(InetAddress.ofLiteral(address.strip()));
        } catch (IllegalArgumentException notALiteral) {
            return Optional.empty();
        }
    }

    /** The one address this range holds, in the form a connection reports it, or empty when it holds more. */
    public Optional<String> single() {
        return prefix == network.length * 8 ? Optional.of(base.getHostAddress()) : Optional.empty();
    }

    /** Whether {@code address} falls in this range; an IPv4 address never falls in an IPv6 range, nor the reverse. */
    public boolean contains(InetAddress address) {
        Objects.requireNonNull(address, "address");
        byte[] bytes = address.getAddress();
        if (bytes.length != network.length
                || (address instanceof Inet4Address) != (network.length == 4)
                || (address instanceof Inet6Address) != (network.length == 16)) {
            return false;
        }
        int whole = prefix / 8;
        for (int i = 0; i < whole; i++) {
            if (bytes[i] != network[i]) {
                return false;
            }
        }
        int rest = prefix % 8;
        if (rest == 0) {
            return true;
        }
        int mask = (0xff << (8 - rest)) & 0xff;
        return (bytes[whole] & mask) == (network[whole] & mask);
    }
}
