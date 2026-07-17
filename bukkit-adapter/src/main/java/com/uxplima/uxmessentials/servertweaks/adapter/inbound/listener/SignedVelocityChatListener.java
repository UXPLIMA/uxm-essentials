package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.servertweaks.application.SignedDirectiveQueue;
import com.uxplima.uxmessentials.servertweaks.domain.SignedChatDirective;
import com.uxplima.uxmessentials.servertweaks.domain.SignedSource;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the proxy's chat ruling to the backend's own {@link AsyncChatEvent}, so a signed message vetoed or rewritten
 * at the Velocity proxy is vetoed or rewritten identically here and the client's signed-chat chain stays in sync
 * across the proxy hop.
 *
 * <p>Runs at {@link EventPriority#LOWEST} so the ruling lands before any formatting or lock listener sees the line.
 * The directive is taken from the shared {@link SignedDirectiveQueue} without blocking: the proxy sends its ruling
 * ahead of the forwarded chat on the same ordered connection, so it is normally already queued when this fires; if no
 * ruling is present the message is left alone, which is exactly the behaviour when no SignedVelocity proxy is running.
 * (The reference plugin briefly waits on the event thread to close that race; this backend does not block a server
 * thread and relies on the ahead-of-chat ordering instead.)
 */
@NullMarked
public final class SignedVelocityChatListener implements Listener {

    private final SignedDirectiveQueue queue;

    public SignedVelocityChatListener(SignedDirectiveQueue queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /** Apply the proxy's chat ruling for this speaker, if one has arrived. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        queue.poll(event.getPlayer().getUniqueId(), SignedSource.CHAT).ifPresent(directive -> apply(event, directive));
    }

    private void apply(AsyncChatEvent event, SignedChatDirective directive) {
        if (directive.cancelled()) {
            event.setCancelled(true);
            return;
        }
        directive.modifiedMessage().ifPresent(message -> event.message(Component.text(message)));
    }
}
