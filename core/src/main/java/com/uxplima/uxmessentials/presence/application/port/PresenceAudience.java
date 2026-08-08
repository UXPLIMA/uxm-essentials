package com.uxplima.uxmessentials.presence.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that enumerates the online players who should see an AFK away/back broadcast. The AFK use
 * cases ask for the audience and the {@link com.uxplima.uxmessentials.shared.application.message.Notifier}
 * fans the line out to each; the adapter resolves online players through the kernel without the application
 * iterating {@code Bukkit.getOnlinePlayers()} itself. Mirrors the messaging context's {@code StaffAudience}.
 */
public interface PresenceAudience {

    /** Every player currently online, the recipients of an AFK away/back broadcast. */
    List<PlayerRef> online();
}
