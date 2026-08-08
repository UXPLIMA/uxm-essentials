package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ReplyRoutingStore} implementation. The {@code /rtoggle} switch is a per-player preference that
 * survives relog, so it rides on a {@link PdcToggle} under its own key. A fresh player who never ran
 * {@code /rtoggle} takes part in reply routing.
 */
@NullMarked
public final class PdcReplyRoutingStore implements ReplyRoutingStore {

    private final PdcToggle toggle;

    public PdcReplyRoutingStore(Plugin plugin) {
        this.toggle = new PdcToggle(Objects.requireNonNull(plugin, "plugin"), "reply-toggle-off");
    }

    @Override
    public boolean acceptsReplies(PlayerRef who) {
        return toggle.accepts(who);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        return toggle.toggle(who);
    }
}
