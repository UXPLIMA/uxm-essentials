package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.servertweaks.domain.ChatReportPolicy;
import org.jspecify.annotations.NullMarked;

/**
 * Carries the no-chat-reports tweak: when a player sends a signed public-chat message, this listener renders the line
 * itself and re-delivers it to every viewer as an <em>unsigned</em> message, then cancels the event so the server's
 * default signed delivery never happens. A message with no signature carries nothing Mojang's chat-reporting system
 * can act on, so public chat handled this way cannot be reported.
 *
 * <p>What the server genuinely controls ends here: it can decline to relay the signature, but it cannot stop a vanilla
 * client from signing its own outgoing messages, so the tweak strips reportability from public chat rather than
 * disabling signing outright. The decision to act is the pure {@link ChatReportPolicy}; only an already-signed message
 * is reworked, so an already-unsigned line (secure chat off, a system-sourced message) flows untouched.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} with {@code ignoreCancelled = true}: any earlier listener that formats the
 * line (installs a {@code ChatRenderer}) or cancels it (a mute, a chat lock, a proxy veto) has already acted, so the
 * final renderer is used and a message someone else already dropped is not resurrected. {@code AsyncChatEvent} fires
 * off the main thread; rendering and {@code sendMessage} are Adventure calls Paper already performs on that thread for
 * ordinary chat, so the async-listener contract holds.
 */
@NullMarked
public final class UnsignedChatListener implements Listener {

    private final ChatReportPolicy policy;

    public UnsignedChatListener(ChatReportPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Re-deliver a signed public-chat line unsigned, unless the tweak is off or the message already arrived unsigned. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!policy.shouldDeliverUnsigned(event.signedMessage().isSystem())) {
            return;
        }
        Player source = event.getPlayer();
        ChatRenderer renderer = event.renderer();
        Component displayName = source.displayName();
        Component message = event.message();
        // Copy the viewer set: it is delivered to outside the event and must not be iterated while the server mutates
        // it.
        for (Audience viewer : List.copyOf(event.viewers())) {
            viewer.sendMessage(renderer.render(source, displayName, message, viewer));
        }
        event.setCancelled(true);
    }
}
