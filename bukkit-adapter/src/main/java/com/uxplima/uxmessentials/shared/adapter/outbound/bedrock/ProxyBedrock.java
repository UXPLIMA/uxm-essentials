package com.uxplima.uxmessentials.shared.adapter.outbound.bedrock;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.CarrierReceipts;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.RemoteSyncListener;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.BedrockFormAnswerFrame;
import com.uxplima.uxmessentials.shared.network.BedrockFormRequestFrame;
import com.uxplima.uxmessentials.shared.network.BedrockPresenceFrame;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import com.uxplima.uxmlib.bedrock.BedrockClient;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockFormCodec;
import com.uxplima.uxmlib.bedrock.BedrockFormRequest;
import com.uxplima.uxmlib.bedrock.BedrockFormResponse;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.RelayedBedrockScreen;

/**
 * Bedrock players as the proxy plugin knows them, for a backend with neither Floodgate nor Geyser of its own: Geyser
 * runs on the proxy, and the proxy plugin says which players here are Bedrock players and shows them forms.
 *
 * <p>Who is a Bedrock player comes from a {@link BedrockPresenceFrame} the proxy sends when they connect here. A form
 * goes to the proxy as a {@link BedrockFormRequestFrame} over the player's own connection, and the answer comes back
 * as a {@link BedrockFormAnswerFrame}. Every frame is sealed with the network secret. A frame from the proxy counts
 * only when it names this backend and arrived over the connection of the player it is about, so neither a client nor
 * a frame relayed from another server can make anyone a Bedrock player or answer their form.
 *
 * <p>An answer can hold a secret, such as a PIN. It reaches only the form that asked, once, and is never logged. A
 * player's waiting forms and what the proxy said about them are dropped when they leave this backend, by quitting or
 * by switching to another.
 */
public final class ProxyBedrock implements RemoteSyncListener {

    /** What {@link BedrockDetector#backend()} names this source in the line operators read. */
    static final String BACKEND = "proxy";

    private final String serverId;
    private final CarrierReceipts receipts;
    private final FrameSeal seal;
    private final PlayerConnection connection;
    private final Logger log;
    private final RelayedBedrockScreen screen;
    private final Map<UUID, String> online = new ConcurrentHashMap<>();
    private final Map<UUID, BedrockPresenceFrame> bedrock = new ConcurrentHashMap<>();
    private final List<Consumer<PlayerRef>> arrivals = new CopyOnWriteArrayList<>();
    private final BedrockDetector detector = new Detector();

    /** Carries sealed bytes to the proxy over one player's own connection. */
    @FunctionalInterface
    public interface PlayerConnection {

        /**
         * Sends {@code sealed} over {@code player}'s connection, on their thread, or runs {@code undelivered} when
         * they are no longer here or the send fails.
         */
        void send(UUID player, byte[] sealed, Runnable undelivered);
    }

    /**
     * The Bedrock players of backend {@code serverId}, matching each proxy frame to the connection that carried it
     * through {@code receipts}, and sealing requests with {@code seal}.
     */
    public ProxyBedrock(
            String serverId, CarrierReceipts receipts, FrameSeal seal, PlayerConnection connection, Logger log) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.seal = Objects.requireNonNull(seal, "seal");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.log = Objects.requireNonNull(log, "log");
        this.screen = new RelayedBedrockScreen(this::request);
    }

    /** Who the proxy says is a Bedrock player here: their xuid and client too. Answers from memory. */
    public BedrockDetector detector() {
        return detector;
    }

    /** The screen that shows a Bedrock player here a form through the proxy. */
    public BedrockScreen screen() {
        return screen;
    }

    /**
     * Runs {@code arrived} off the tick for each player the proxy says is a Bedrock player, once they are known here.
     */
    public void onArrival(Consumer<PlayerRef> arrived) {
        arrivals.add(Objects.requireNonNull(arrived, "arrived"));
    }

    /** {@code player} joined this backend. Call it on their thread. */
    public void joined(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        online.put(player.uuid(), player.name());
    }

    /** {@code player} left this backend: what the proxy said about them and their waiting forms no longer hold. */
    public void left(UUID player) {
        Objects.requireNonNull(player, "player");
        online.remove(player);
        bedrock.remove(player);
        screen.forget(player);
    }

    @Override
    public void onRemoteChange(NetworkMessage message) {
        switch (message) {
            case BedrockPresenceFrame presence -> presence(presence);
            case BedrockFormAnswerFrame answer -> answer(answer);
            default -> {
                // Not a frame about Bedrock players.
            }
        }
    }

    private void presence(BedrockPresenceFrame frame) {
        if (!fromProxy(frame.originServer(), frame.frameId(), frame.player())
                || !frame.server().equals(serverId)) {
            return;
        }
        String name = online.get(frame.player());
        if (name == null) {
            // They left before the word arrived; a later visit brings its own.
            return;
        }
        bedrock.put(frame.player(), frame);
        if (!online.containsKey(frame.player())) {
            bedrock.remove(frame.player());
            return;
        }
        PlayerRef arrived = new PlayerRef(frame.player(), name);
        for (Consumer<PlayerRef> listener : arrivals) {
            try {
                listener.accept(arrived);
            } catch (RuntimeException failed) {
                log.warn("event=bedrock_proxy_arrival_failed player={} reason={}", name, failed.toString());
            }
        }
    }

    private void answer(BedrockFormAnswerFrame frame) {
        if (!fromProxy(frame.originServer(), frame.frameId(), frame.player())
                || !frame.toServer().equals(serverId)) {
            return;
        }
        BedrockFormResponse response;
        try {
            response = BedrockFormCodec.decodeResponse(Base64.getDecoder().decode(frame.answer()));
        } catch (IllegalArgumentException unreadable) {
            // The message names what was wrong, never the content.
            log.warn(
                    "event=bedrock_proxy_answer_unreadable player={} reason={}",
                    frame.player(),
                    String.valueOf(unreadable.getMessage()));
            response = BedrockFormResponse.CLOSED;
        }
        screen.receive(frame.player(), frame.requestId(), response);
    }

    /** Whether a frame says it is from the proxy and arrived over the connection of the player it is about. */
    private boolean fromProxy(String origin, UUID frameId, UUID player) {
        if (!origin.equals(ProxyConnectionFrame.PROXY)) {
            return false;
        }
        Optional<CarrierReceipts.Receipt> receipt = receipts.take(frameId);
        return receipt.isPresent() && receipt.get().carrier().equals(player);
    }

    /** The relayed screen's carrier: the form to the proxy over the player's own connection. */
    private boolean request(UUID player, long requestId, BedrockFormRequest request) {
        if (!online.containsKey(player)) {
            return false;
        }
        byte[] frame;
        try {
            String form = Base64.getEncoder().encodeToString(BedrockFormCodec.encode(request));
            frame = NetworkMessageCodec.encode(new BedrockFormRequestFrame(serverId, player, requestId, form));
        } catch (IllegalArgumentException tooLarge) {
            log.warn(
                    "event=bedrock_proxy_form_refused player={} reason={}",
                    player,
                    String.valueOf(tooLarge.getMessage()));
            return false;
        }
        if (frame.length + FrameSeal.TRAILER_BYTES > BusChannel.MAX_FRAME_BYTES) {
            log.warn("event=bedrock_proxy_form_refused player={} reason=form too large for the channel", player);
            return false;
        }
        connection.send(player, seal.seal(frame), () -> screen.receive(player, requestId, BedrockFormResponse.CLOSED));
        return true;
    }

    /** The detector over what the proxy said. */
    private final class Detector implements BedrockDetector {

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock.containsKey(Objects.requireNonNull(player, "player"));
        }

        @Override
        public Optional<String> xuid(UUID player) {
            return Optional.ofNullable(bedrock.get(Objects.requireNonNull(player, "player")))
                    .map(BedrockPresenceFrame::xuid)
                    .filter(xuid -> !xuid.isBlank());
        }

        @Override
        public Optional<BedrockClient> client(UUID player) {
            return Optional.ofNullable(bedrock.get(Objects.requireNonNull(player, "player")))
                    .map(ProxyBedrock::client);
        }

        @Override
        public String backend() {
            return BACKEND;
        }
    }

    /** The client the proxy described, read by Geyser's names; a name this build does not know reads as unknown. */
    static BedrockClient client(BedrockPresenceFrame frame) {
        return new BedrockClient(
                device(frame.platform()),
                input(frame.inputMode()),
                "POCKET".equals(frame.uiProfile()) ? BedrockClient.Layout.POCKET : BedrockClient.Layout.CLASSIC,
                frame.version(),
                frame.language());
    }

    private static BedrockClient.Device device(String platform) {
        return switch (platform) {
            case "GOOGLE" -> BedrockClient.Device.ANDROID;
            case "IOS" -> BedrockClient.Device.IOS;
            case "OSX" -> BedrockClient.Device.MACOS;
            case "AMAZON" -> BedrockClient.Device.FIRE_OS;
            case "UWP", "WIN32" -> BedrockClient.Device.WINDOWS;
            case "PS4" -> BedrockClient.Device.PLAYSTATION;
            case "NX" -> BedrockClient.Device.SWITCH;
            case "XBOX" -> BedrockClient.Device.XBOX;
            case "GEARVR", "HOLOLENS" -> BedrockClient.Device.VR;
            case "UNKNOWN", "" -> BedrockClient.Device.UNKNOWN;
            default -> BedrockClient.Device.OTHER;
        };
    }

    private static BedrockClient.Input input(String mode) {
        return switch (mode) {
            case "KEYBOARD_MOUSE" -> BedrockClient.Input.KEYBOARD_MOUSE;
            case "TOUCH" -> BedrockClient.Input.TOUCH;
            case "CONTROLLER" -> BedrockClient.Input.CONTROLLER;
            case "VR" -> BedrockClient.Input.VR;
            default -> BedrockClient.Input.UNKNOWN;
        };
    }
}
