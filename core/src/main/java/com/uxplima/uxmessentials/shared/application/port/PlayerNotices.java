package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;

/**
 * Tells a player something whether or not they are online here: at once when they are, otherwise when they are next
 * online, on the server they are on now or the one they next join. For a line that must reach its player, such as
 * being paid, unlike a line that only means something while the player is there.
 *
 * <p>Called off the tick: telling a player who is away writes the database.
 */
public interface PlayerNotices {

    /** Tell {@code recipient} {@code line}, now or when they are next online. */
    void tell(PlayerRef recipient, MessageLine line);
}
