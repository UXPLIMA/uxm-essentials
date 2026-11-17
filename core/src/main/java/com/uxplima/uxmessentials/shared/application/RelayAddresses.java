package com.uxplima.uxmessentials.shared.application;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.AddressRange;

/**
 * The addresses that relay many players onto this server under one address, so the address says nothing about which
 * player is behind it: Geyser running on its own without Floodgate, or a proxy that does not forward player addresses,
 * hands every player it carries its own address.
 *
 * <p>Loopback is always one: whatever connects from this machine connects for somebody else. The operator lists the
 * rest. Floodgate, on this server or on the proxy, puts the Bedrock client's own address on the connection, so a
 * Bedrock player behind it arrives from their own address and needs nothing here.
 *
 * <p>A relay address is no evidence about a player. The IP history does not record it, so it links no alts and counts
 * towards no account cap; a STRICT ban does not fan out to it, an IP ban neither lands on it nor is enforced from it,
 * and a device is not trusted by it.
 */
public final class RelayAddresses {

    /** Only loopback relays, the answer for a server whose operator listed nothing. */
    public static final RelayAddresses LOOPBACK = new RelayAddresses(List.of());

    /** The forms a loopback connection reports its address in. */
    private static final List<String> LOOPBACK_FORMS = List.of("127.0.0.1", "0:0:0:0:0:0:0:1");

    private final List<AddressRange> listed;

    private RelayAddresses(List<AddressRange> listed) {
        this.listed = List.copyOf(listed);
    }

    /** Loopback and every address or range in {@code entries}; an entry it cannot read is warned about and skipped. */
    public static RelayAddresses of(List<String> entries, Logger log) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(log, "log");
        List<AddressRange> ranges = new ArrayList<>();
        for (String entry : entries) {
            Optional<AddressRange> range = AddressRange.parse(entry);
            if (range.isPresent()) {
                ranges.add(range.get());
            } else {
                log.warn("event=relay_address_unreadable entry={} expected=address|address/prefix", entry);
            }
        }
        return new RelayAddresses(ranges);
    }

    /** Whether the operator listed any relay beyond loopback. */
    public boolean listed() {
        return !listed.isEmpty();
    }

    /** Whether {@code address} relays many players. Anything that is not an address literal is not one. */
    public boolean isRelay(String address) {
        Objects.requireNonNull(address, "address");
        Optional<InetAddress> parsed = AddressRange.literal(address);
        if (parsed.isEmpty()) {
            return false;
        }
        InetAddress ip = parsed.get();
        if (ip.isLoopbackAddress() || ip.isAnyLocalAddress()) {
            return true;
        }
        for (AddressRange range : listed) {
            if (range.contains(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The single relay addresses, loopback included, as a connection reports them. Rows recorded before an address was
     * listed hold only its token, so these are what lets a reader recognise them; a listed range has no single form.
     */
    public Set<String> singleAddresses() {
        Set<String> singles = new LinkedHashSet<>(LOOPBACK_FORMS);
        for (AddressRange range : listed) {
            range.single().ifPresent(singles::add);
        }
        return Set.copyOf(singles);
    }
}
