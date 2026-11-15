package com.uxplima.uxmessentials.customcommands.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The money side of a priced command. The adapter resolves the configured currency lazily, so a backend that
 * arrives after the module started is still picked up.
 *
 * <p>Whether a priced command runs while the backend does not answer is policy, decided by the use case from
 * {@link #available()}; this port only reports the backend's state, so {@link #canAfford} and {@link #charge} answer
 * false rather than waving a price through.
 */
public interface CommandFee {

    /** Whether the currency backend answers right now; it can come and go while the server runs. */
    boolean available();

    /** Whether {@code who} holds at least {@code amount}. */
    boolean canAfford(PlayerRef who, double amount);

    /** Take {@code amount} from {@code who}; false when the withdrawal did not happen. */
    boolean charge(PlayerRef who, double amount);

    /** {@code amount} rendered the way the currency writes it, for the messages that quote a price. */
    String format(double amount);
}
