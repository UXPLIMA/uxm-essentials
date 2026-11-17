package com.uxplima.uxmessentials.velocity;

import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.network.BedrockFormAnswerFrame;
import com.uxplima.uxmessentials.shared.network.BedrockFormRequestFrame;
import com.uxplima.uxmessentials.shared.network.BedrockPresenceFrame;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import com.uxplima.uxmlib.bedrock.BedrockFormCodec;
import com.uxplima.uxmlib.bedrock.BedrockFormRequest;
import com.uxplima.uxmlib.bedrock.BedrockFormResponse;
import com.uxplima.uxmlib.bedrock.CumulusForms;
import org.geysermc.cumulus.form.Form;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Speaks for Geyser on the proxy to backends that have neither Floodgate nor Geyser: it tells a backend which of its
 * players are Bedrock players, and shows those players the forms a backend asks for.
 *
 * <p>Everything it says is sealed with the network secret and sent over the player's own connection to one backend.
 * A request is read only when its seal checks out and it came over the connection of the player it names; it is never
 * relayed to another backend. The answer, which can hold a secret such as a PIN, goes back to the backend that asked
 * and nowhere else: never to the client, never to another server, and not at all once the player switched or left.
 * A player's waiting forms are dropped when they answer, switch or leave, and a player keeps at most
 * {@value #MAX_WAITING} of them.
 */
@NullMarked
final class ProxyBedrockForms {

    /** The most forms one player may have waiting for an answer; past it the oldest is dropped. */
    static final int MAX_WAITING = 8;

    private final FrameSeal seal;
    private final GeyserPlayers geyser;
    private final Connections connections;
    private final Supplier<UUID> ids;
    private final Logger logger;

    /** Waiting forms per player, oldest first. Guarded by {@code this}. */
    private final Map<UUID, LinkedHashMap<Long, String>> waiting = new HashMap<>();

    /** What Geyser on the proxy knows and does. */
    interface GeyserPlayers {

        /** The Bedrock player's presence on {@code server}, or empty for a Java player. */
        Optional<BedrockPresenceFrame> presence(UUID player, String server, UUID frameId);

        /** Shows {@code player} the form; {@code false} when Geyser does not know them. */
        boolean send(UUID player, Form form);
    }

    /** The backend connections of players on the proxy. */
    interface Connections {

        /**
         * Sends {@code sealed} to {@code server} over {@code player}'s connection, only while they are on that
         * server. Never to the client.
         */
        void send(UUID player, String server, byte[] sealed);
    }

    ProxyBedrockForms(
            FrameSeal seal, GeyserPlayers geyser, Connections connections, Supplier<UUID> ids, Logger logger) {
        this.seal = Objects.requireNonNull(seal, "seal");
        this.geyser = Objects.requireNonNull(geyser, "geyser");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** {@code player} just reached {@code server}: tell it when they are a Bedrock player. */
    void connected(UUID player, String server) {
        geyser.presence(player, server, ids.get())
                .ifPresent(
                        presence -> connections.send(player, server, seal.seal(NetworkMessageCodec.encode(presence))));
    }

    /** {@code player} switched backend or left the proxy: no form they were shown answers anywhere any more. */
    synchronized void left(UUID player) {
        waiting.remove(Objects.requireNonNull(player, "player"));
    }

    /**
     * A form request {@code sealed}, carried by {@code carrier}'s connection from {@code server}. Shown only when the
     * seal checks out and the request is for that same player.
     */
    void request(UUID carrier, String server, byte[] sealed) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(sealed, "sealed");
        FrameSeal.Check verdict = seal.check(sealed);
        if (verdict != FrameSeal.Check.TRUSTED) {
            logger.warn("refused a Bedrock form request from {}: {}", server, verdict);
            return;
        }
        BedrockFormRequestFrame frame;
        try {
            if (!(NetworkMessageCodec.decode(FrameSeal.frame(sealed)) instanceof BedrockFormRequestFrame request)) {
                return;
            }
            frame = request;
        } catch (IllegalArgumentException unreadable) {
            logger.warn("dropped an unreadable Bedrock form request from {}", server);
            return;
        }
        if (!frame.player().equals(carrier)) {
            logger.warn("refused a Bedrock form request from {} carried by another player", server);
            return;
        }
        hold(carrier, frame.requestId(), server);
        BedrockFormRequest form;
        try {
            form = BedrockFormCodec.decodeRequest(Base64.getDecoder().decode(frame.form()));
        } catch (IllegalArgumentException unreadable) {
            logger.warn("dropped an unreadable Bedrock form from {}: {}", server, unreadable.getMessage());
            answered(carrier, frame.requestId(), BedrockFormResponse.CLOSED);
            return;
        }
        boolean shown;
        try {
            shown = geyser.send(
                    carrier, CumulusForms.toForm(form, answer -> answered(carrier, frame.requestId(), answer)));
        } catch (RuntimeException failed) {
            logger.warn("Geyser could not show a form from {}: {}", server, failed.toString());
            shown = false;
        }
        if (!shown) {
            answered(carrier, frame.requestId(), BedrockFormResponse.CLOSED);
        }
    }

    /** The player answered: back to the backend that asked, if the form is still waiting there. */
    private void answered(UUID player, long requestId, BedrockFormResponse answer) {
        String server = take(player, requestId);
        if (server == null) {
            return;
        }
        BedrockFormAnswerFrame frame = new BedrockFormAnswerFrame(
                ProxyConnectionFrame.PROXY,
                ids.get(),
                server,
                player,
                requestId,
                Base64.getEncoder().encodeToString(BedrockFormCodec.encode(answer)));
        byte[] body = NetworkMessageCodec.encode(frame);
        if (body.length + FrameSeal.TRAILER_BYTES > BusChannel.MAX_FRAME_BYTES) {
            // Too long for the channel: the backend hears a close rather than waiting for nothing.
            body = NetworkMessageCodec.encode(new BedrockFormAnswerFrame(
                    ProxyConnectionFrame.PROXY,
                    frame.frameId(),
                    server,
                    player,
                    requestId,
                    Base64.getEncoder().encodeToString(BedrockFormCodec.encode(BedrockFormResponse.CLOSED))));
        }
        connections.send(player, server, seal.seal(body));
    }

    private synchronized void hold(UUID player, long requestId, String server) {
        LinkedHashMap<Long, String> theirs = waiting.computeIfAbsent(player, unused -> new LinkedHashMap<>());
        theirs.put(requestId, server);
        if (theirs.size() > MAX_WAITING) {
            Iterator<Long> oldest = theirs.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    private synchronized @org.jspecify.annotations.Nullable String take(UUID player, long requestId) {
        LinkedHashMap<Long, String> theirs = waiting.get(player);
        if (theirs == null) {
            return null;
        }
        String server = theirs.remove(requestId);
        if (theirs.isEmpty()) {
            waiting.remove(player);
        }
        return server;
    }
}
