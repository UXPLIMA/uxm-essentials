package com.uxplima.uxmessentials.servertweaks.adapter.inbound;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.uxplima.uxmessentials.servertweaks.application.SignedDirectiveQueue;
import com.uxplima.uxmessentials.servertweaks.domain.SignedVelocityFrame;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Receives the Velocity proxy's chat/command rulings on the {@code signedvelocity:main} channel and buffers each into
 * the shared {@link SignedDirectiveQueue}, where the chat and command listeners pick them up as the matching events
 * fire. This is the backend half of the SignedVelocity handshake; a proxy-side SignedVelocity component is what sends
 * these frames, so with no such proxy the channel simply never carries anything and the whole tweak stays inert.
 *
 * <p>The frame identifies its own player, so the carrier the message rode in on is irrelevant. Decoding is the pure
 * {@link SignedVelocityFrame#decode(byte[])}; a malformed frame is logged with context and dropped rather than allowed
 * to derail the handshake.
 */
@NullMarked
public final class SignedVelocityChannelListener implements PluginMessageListener {

    /** The reserved SignedVelocity plugin-message channel shared with the proxy-side component. */
    public static final String CHANNEL = "signedvelocity:main";

    private final SignedDirectiveQueue queue;
    private final Logger log;

    public SignedVelocityChannelListener(SignedDirectiveQueue queue, Logger log) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try {
            queue.offer(SignedVelocityFrame.decode(message));
        } catch (IllegalArgumentException | java.io.UncheckedIOException e) {
            log.warn("dropping malformed SignedVelocity frame: {}", e.toString());
        }
    }
}
