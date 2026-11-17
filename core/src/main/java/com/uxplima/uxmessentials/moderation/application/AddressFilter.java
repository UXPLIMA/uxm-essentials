package com.uxplima.uxmessentials.moderation.application;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.RelayAddresses;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.AddressRange;

/**
 * Which connecting addresses say something about who a player is. An address many people share says nothing: the
 * loopback a proxy on the same machine relays everybody from, the {@linkplain RelayAddresses relays} the operator
 * listed (Geyser without Floodgate, a proxy without forwarding), the private ranges of a LAN, link-local addresses,
 * and the carrier-grade NAT range ({@code 100.64.0.0/10}) mobile networks put whole towns behind. The operator adds
 * the rest (their proxy's public address, a known cafe) as single addresses or ranges ({@code 203.0.113.7},
 * {@code 198.51.100.0/24}, {@code 2001:db8::/32}).
 *
 * <p>Only an address literal counts. Nothing here resolves a host name.
 */
public final class AddressFilter {

    /** No address is ignored beyond the ones that never count. */
    public static final AddressFilter DEFAULT = new AddressFilter(List.of(), RelayAddresses.LOOPBACK);

    private static final AddressRange CARRIER_NAT =
            AddressRange.parse("100.64.0.0/10").orElseThrow();
    private static final AddressRange UNIQUE_LOCAL =
            AddressRange.parse("fc00::/7").orElseThrow();

    private final List<AddressRange> ignored;
    private final RelayAddresses relays;

    private AddressFilter(List<AddressRange> ignored, RelayAddresses relays) {
        this.ignored = List.copyOf(ignored);
        this.relays = relays;
    }

    /** A filter that also ignores every address or range in {@code entries}; an entry it cannot read is warned about. */
    public static AddressFilter of(List<String> entries, Logger log) {
        return of(entries, RelayAddresses.LOOPBACK, log);
    }

    /** Like {@link #of(List, Logger)}, and the {@code relays} never count either. */
    public static AddressFilter of(List<String> entries, RelayAddresses relays, Logger log) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(relays, "relays");
        Objects.requireNonNull(log, "log");
        List<AddressRange> ranges = new ArrayList<>();
        for (String entry : entries) {
            Optional<AddressRange> range = AddressRange.parse(entry);
            if (range.isPresent()) {
                ranges.add(range.get());
            } else {
                log.warn(
                        "event=case_variant_address_ignored_unreadable entry={} expected=address|address/prefix",
                        entry);
            }
        }
        return new AddressFilter(ranges, relays);
    }

    /** Whether {@code address} can tell people apart: a public address literal the operator did not ignore. */
    public boolean counts(String address) {
        Objects.requireNonNull(address, "address");
        Optional<InetAddress> parsed = AddressRange.literal(address);
        if (parsed.isEmpty() || relays.isRelay(address)) {
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
        for (AddressRange range : ignored) {
            if (range.contains(ip)) {
                return false;
            }
        }
        return true;
    }
}
