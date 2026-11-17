package com.uxplima.uxmessentials.security.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps whatever a player types into chat while they are being asked for a PIN or code out of chat.
 *
 * <p>A player looking at a PIN prompt, or waiting for the next one after a wrong entry, may type the PIN into chat by
 * mistake, and a chat line is not private: a chat logger or a Discord bridge sees it. When any plugin listens to the
 * legacy chat event, Paper raises that one before the modern one, so cancelling only the modern event would be too
 * late. This cancels both, first, for the whole of a freeze and for as long as a re-auth is asking, whatever the
 * freeze's chat restriction says. A line typed into chat is never how a PIN is entered here, so nothing is lost.
 */
@NullMarked
@SuppressWarnings("deprecation") // the legacy chat event is the one a chat logger may still listen to
public final class PinEntryChatGuard implements Listener {

    private final Predicate<UUID> asking;
    private final Messages messages;
    private final MessageSink sink;

    /** @param asking whether a player is frozen for verification or is being asked for a PIN or code right now */
    public PinEntryChatGuard(Predicate<UUID> asking, Messages messages, MessageSink sink) {
        this.asking = Objects.requireNonNull(asking, "asking");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    // Sees cancelled lines too: another plugin's listener at this priority may have cancelled the line before this one
    // ran, and cancelling it here as well is what keeps it out if that plugin lets it through again later.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (asking.test(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Sees cancelled lines too, for the reason onLegacyChat gives.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!asking.test(player.getUniqueId())) {
            return;
        }
        if (!event.isCancelled()) {
            PlayerRef ref = BukkitRefs.toRef(player);
            sink.deliver(ref, messages.resolve(ref, SecurityMessageKey.SECURITY_VERIFY_MUST_VERIFY, Map.of()));
        }
        event.setCancelled(true);
    }
}
