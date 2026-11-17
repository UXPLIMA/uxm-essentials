package com.uxplima.uxmessentials.moderation.application;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * Which connecting addresses say something about who a player is. An address many people share says nothing: the
 * loopback a Geyser without Floodgate or a proxy on the same machine relays everybody from, the private ranges of a
 * LAN or a proxy without forwarding, link-local addresses, and the carrier-grade NAT range ({@code 100.64.0.0/10})
 * mobile networks put whole towns behind. The operator adds the rest (their proxy's public address, a known cafe) as
 * single addresses or ranges ({@code 203.0.113.7}, {@code 198.51.100.0/24}, {@code 2001:db8::/32}).
 *
 * <p>Only an address literal counts. Nothing here resolves a host name.
 */
public final class AddressFilter {

    /** No address is ignored beyond the ones that never count. */
    public static final AddressFilter DEFAULT = new AddressFilter(List.of());

    private static final Range CARRIER_NAT = new Range(new byte[] {100, 64, 0, 0}, 10);
    private static final Range UNIQUE_LOCAL =
            new Range(new byte[] {(byte) 0xfc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 7);

    private final List<Range> ignored;

    private AddressFilter(List<Range> ignored) {
        this.ignored = List.copyOf(ignored);
    }

    /** A filter that also ignores every address or range in {@code entries}; an entry it cannot read is warned about. */
    public static AddressFilter of(List<String> entries, Logger log) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(log, "log");
        List<Range> ranges = new ArrayList<>();
        for (String entry : entries) {
            Optional<Range> range = Range.parse(entry);
            if (range.isPresent()) {
                ranges.add(range.get());
            } else {
                log.warn(
                        "event=case_variant_address_ignored_unreadable entry={} expected=address|address/prefix",
                        entry);
            }
        }
        return new AddressFilter(ranges);
    }

    /** Whether {@code address} can tell people apart: a public address literal the operator did not ignore. */
    public boolean counts(String address) {
        Objects.requireNonNull(address, "address");
        Optional<InetAddress> parsed = literal(address);
        if (parsed.isEmpty()) {
            return false;
        }
        InetAddress ip = parsed.get();
        if (ip.isLoopbackAddress()
                || ip.isAnyLocalAddress()
                || ip.isLinkLocalAddress()
                || ip.isSiteLocalAddress()
                || ip.isMulticastAddress()
                || CARRIER_NAT.contains(ip)
                || UNIQUE_LOCAL.contains(ip)) {
            return false;
        }
        for (Range range : ignored) {
            if (range.contains(ip)) {
                return false;
            }
        }
        return true;
    }

    private static Optional<InetAddress> literal(String address) {
        try {
            return Optional.of(InetAddress.ofLiteral(address.strip()));
        } catch (IllegalArgumentException notALiteral) {
            return Optional.empty();
        }
    }

    /** An address and the number of leading bits a match shares with it. */
    private static final class Range {

        private final byte[] network;
        private final int prefix;

        Range(byte[] network, int prefix) {
            this.network = network.clone();
            this.prefix = prefix;
        }

        static Optional<Range> parse(String entry) {
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
            return prefix < 0 || prefix > bits ? Optional.empty() : Optional.of(new Range(bytes, prefix));
        }

        boolean contains(InetAddress address) {
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
}
