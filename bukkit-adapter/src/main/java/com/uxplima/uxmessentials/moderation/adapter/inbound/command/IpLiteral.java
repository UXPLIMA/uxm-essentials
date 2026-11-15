package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Optional;

import com.google.common.net.InetAddresses;
import org.jspecify.annotations.NullMarked;

/**
 * Turns an address a staff member typed into the form a connecting player's address is read in. A ban is
 * matched by exact text against {@code InetAddress#getHostAddress()} of the player, so {@code 2001:DB8::7}
 * typed as is would never match {@code 2001:db8:0:0:0:0:0:7}; storing the canonical form makes every spelling of
 * one address ban the same players.
 *
 * <p>Only a literal is accepted and nothing is ever looked up, so a host name cannot trigger DNS on a command
 * thread. A zero-padded IPv4 octet ({@code 010.0.0.1}) is refused rather than guessed, since some tools read it
 * as octal.
 */
@NullMarked
final class IpLiteral {

    private IpLiteral() {}

    /** The canonical form of {@code typed}, or empty when it is not an IPv4 or IPv6 literal. */
    static Optional<String> canonical(String typed) {
        if (!InetAddresses.isInetAddress(typed)) {
            return Optional.empty();
        }
        return Optional.of(InetAddresses.forString(typed).getHostAddress());
    }

    /** True when {@code argument} was meant as an address rather than a player name. */
    static boolean looksLikeAddress(String argument) {
        return argument.contains(".") || argument.contains(":");
    }
}
