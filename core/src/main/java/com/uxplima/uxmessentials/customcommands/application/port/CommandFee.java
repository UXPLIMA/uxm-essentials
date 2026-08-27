package com.uxplima.uxmessentials.customcommands.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The money side of a priced command. The adapter resolves the configured currency lazily, so a backend that
 * arrives after the module started is still picked up.
 *
 * <p>When the economy module is off, the adapter supplies a free implementation: every command can be afforded,
 * charging is a no-op, and a definition that declares a cost simply runs. An operator who wants the price enforced
 * enables the economy module, which is a clearer contract than silently refusing every priced command.
 */
public interface CommandFee {

    /** Whether {@code who} holds at least {@code amount}. */
    boolean canAfford(PlayerRef who, double amount);

    /** Take {@code amount} from {@code who}; false when the withdrawal did not happen. */
    boolean charge(PlayerRef who, double amount);

    /** {@code amount} rendered the way the currency writes it, for the messages that quote a price. */
    String format(double amount);
}
