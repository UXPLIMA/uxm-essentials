package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link MessageToggleStore} implementation. The {@code /msgtoggle} switch is a per-player preference that
 * survives relog, so it rides on a {@link PdcToggle} under its own key. A fresh player who never ran
 * {@code /msgtoggle} accepts messages.
 */
@NullMarked
public final class PdcMessageToggleStore implements MessageToggleStore {

    private final PdcToggle toggle;

    public PdcMessageToggleStore(Plugin plugin) {
        this.toggle = new PdcToggle(Objects.requireNonNull(plugin, "plugin"), "msg-toggle-off");
    }

    @Override
    public boolean acceptsMessages(PlayerRef who) {
        return toggle.accepts(who);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        return toggle.toggle(who);
    }
}
