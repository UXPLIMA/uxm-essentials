package com.uxplima.uxmessentials.shared.adapter.outbound.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.CarrierReceipts;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.BedrockFormAnswerFrame;
import com.uxplima.uxmessentials.shared.network.BedrockFormRequestFrame;
import com.uxplima.uxmessentials.shared.network.BedrockPresenceFrame;
import com.uxplima.uxmessentials.shared.network.BusSecret;
import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockClient;
import com.uxplima.uxmlib.bedrock.BedrockFormCodec;
import com.uxplima.uxmlib.bedrock.BedrockFormRequest;
import com.uxplima.uxmlib.bedrock.BedrockFormResponse;
import org.junit.jupiter.api.Test;

/**
 * A backend with neither Floodgate nor Geyser learns its Bedrock players from the proxy plugin and shows them forms
 * through it. Only the proxy's word, carried by the player it is about, counts; an answer reaches only its form, once.
 */
class ProxyBedrockTest {

    private static final String SECRET = "vK8pQ2xR7mT4nW9cL3hF6jD1sB5gY0zA";
    private static final UUID MIRA = new UUID(0L, 0x0009_01f2_a3b4_c5d6L);
    private static final UUID ALEX = new UUID(0x5b1e_2f3a_4c5d_4e6fL, 0x8a9b_0c1d_2e3f_4a5bL);

    private long nanos = 1_000L;
    private long frames;
    private final CarrierReceipts receipts = new CarrierReceipts(() -> nanos);
    private final FrameSeal seal = new FrameSeal(BusSecret.of(SECRET), () -> 1_700_000_000_000L);
    private final List<Sent> sent = new ArrayList<>();
    private boolean delivers = true;

    /** What left for the proxy: the player whose connection carried it, and the sealed bytes. */
    private static final class Sent {
        private final UUID player;
        private final byte[] bytes;

        Sent(UUID player, byte[] bytes) {
            this.player = player;
            this.bytes = bytes.clone();
        }

        UUID player() {
            return player;
        }

        byte[] bytes() {
            return bytes.clone();
        }
    }

    private final ProxyBedrock relay = new ProxyBedrock(
            "survival",
            receipts,
            seal,
            (player, bytes, undelivered) -> {
                if (delivers) {
                    sent.add(new Sent(player, bytes));
                } else {
                    undelivered.run();
                }
            },
            new QuietLogger());

    private final Player mira = FakePlayers.player(MIRA, "Mira");
    private final List<String> heard = new ArrayList<>();

    @Test
    void theProxysWordMakesAPlayerHereABedrockPlayerWithTheirXuidAndClient() {
        relay.joined(new PlayerRef(MIRA, "Mira"));
        List<PlayerRef> arrived = new ArrayList<>();
        relay.onArrival(arrived::add);

        relay.onRemoteChange(carried(MIRA, presence(MIRA, "survival")));

        assertThat(relay.detector().isBedrock(MIRA)).isTrue();
        assertThat(relay.detector().xuid(MIRA)).contains("2535412345678901");
        assertThat(relay.detector().client(MIRA)).hasValueSatisfying(client -> {
            assertThat(client.device()).isEqualTo(BedrockClient.Device.ANDROID);
            assertThat(client.layout()).isEqualTo(BedrockClient.Layout.POCKET);
        });
        assertThat(relay.detector().backend()).isEqualTo("proxy");
        assertThat(arrived).containsExactly(new PlayerRef(MIRA, "Mira"));
    }

    @Test
    void aWordNotFromTheProxyNotForThisServerOrNotCarriedByThatPlayerCountsForNothing() {
        relay.joined(new PlayerRef(MIRA, "Mira"));
        relay.joined(new PlayerRef(ALEX, "Alex"));

        relay.onRemoteChange(carried(ALEX, presence(MIRA, "survival")));
        relay.onRemoteChange(carried(MIRA, presence(MIRA, "lobby")));
        relay.onRemoteChange(presence(MIRA, "survival"));
        BedrockPresenceFrame fromABackend = new BedrockPresenceFrame(
                "lobby", frameId(), MIRA, "survival", "1", "GOOGLE", "TOUCH", "POCKET", "1", "en");
        relay.onRemoteChange(carried(MIRA, fromABackend));

        assertThat(relay.detector().isBedrock(MIRA)).isFalse();
    }

    @Test
    void aPlayerWhoLeftBeforeTheWordArrivedIsNotRemembered() {
        relay.joined(new PlayerRef(MIRA, "Mira"));
        relay.left(MIRA);

        relay.onRemoteChange(carried(MIRA, presence(MIRA, "survival")));

        assertThat(relay.detector().isBedrock(MIRA)).isFalse();
    }

    @Test
    void aFormGoesToTheProxyOverThePlayersConnectionAndItsAnswerComesBackOnce() {
        relay.joined(new PlayerRef(MIRA, "Mira"));

        relay.screen()
                .sendInputForm(
                        mira, "PIN", "Your PIN", null, pin -> heard.add("pin " + pin), () -> heard.add("closed"));

        Sent request = sent.getFirst();
        assertThat(request.player()).isEqualTo(MIRA);
        assertThat(seal.check(request.bytes())).isEqualTo(FrameSeal.Check.TRUSTED);
        BedrockFormRequestFrame frame =
                (BedrockFormRequestFrame) NetworkMessageCodec.decode(FrameSeal.frame(request.bytes()));
        assertThat(frame.originServer()).isEqualTo("survival");
        assertThat(frame.player()).isEqualTo(MIRA);
        assertThat(BedrockFormCodec.decodeRequest(Base64.getDecoder().decode(frame.form())))
                .isInstanceOf(BedrockFormRequest.Custom.class);

        BedrockFormAnswerFrame answer = answer(frame.requestId(), "survival", submitted("4821"));
        relay.onRemoteChange(carried(MIRA, answer));
        relay.onRemoteChange(carried(MIRA, answer(frame.requestId(), "survival", submitted("0000"))));

        assertThat(heard).containsExactly("pin 4821");
    }

    @Test
    void anAnswerForAnotherServerOrNotCarriedByThePlayerAnswersNothing() {
        relay.joined(new PlayerRef(MIRA, "Mira"));
        relay.joined(new PlayerRef(ALEX, "Alex"));
        relay.screen().sendInputForm(mira, "PIN", "Your PIN", null, pin -> heard.add("pin " + pin), () -> {});
        long requestId = request(sent.getFirst()).requestId();

        relay.onRemoteChange(carried(MIRA, answer(requestId, "lobby", submitted("1"))));
        relay.onRemoteChange(carried(ALEX, answer(requestId, "survival", submitted("2"))));
        relay.onRemoteChange(answer(requestId, "survival", submitted("3")));

        assertThat(heard).isEmpty();
    }

    @Test
    void aPlayerWhoLeftHearsNoAnswerAndIsNoLongerABedrockPlayerHere() {
        relay.joined(new PlayerRef(MIRA, "Mira"));
        relay.onRemoteChange(carried(MIRA, presence(MIRA, "survival")));
        relay.screen()
                .sendSimpleForm(
                        mira, "Shop", null, List.of(new BedrockButton("Tools", null)), index -> heard.add("tapped"));
        long requestId = request(sent.getFirst()).requestId();

        relay.left(MIRA);
        relay.onRemoteChange(carried(MIRA, answer(requestId, "survival", new BedrockFormResponse.Tapped(0))));

        assertThat(heard).isEmpty();
        assertThat(relay.detector().isBedrock(MIRA)).isFalse();
    }

    @Test
    void aFormThatCannotLeaveClosesAndOneForSomebodyNotHereIsNeverSent() {
        relay.joined(new PlayerRef(MIRA, "Mira"));
        delivers = false;

        relay.screen().sendInputForm(mira, "PIN", "Your PIN", null, pin -> heard.add("pin"), () -> heard.add("closed"));
        relay.screen()
                .sendInputForm(
                        FakePlayers.player(ALEX, "Alex"), "PIN", "Your PIN", null, pin -> {}, () -> heard.add("alex"));

        assertThat(heard).containsExactly("closed");
        assertThat(sent).isEmpty();
    }

    private BedrockPresenceFrame presence(UUID player, String server) {
        return new BedrockPresenceFrame(
                ProxyConnectionFrame.PROXY,
                frameId(),
                player,
                server,
                "2535412345678901",
                "GOOGLE",
                "TOUCH",
                "POCKET",
                "1.21.90",
                "tr_TR");
    }

    private BedrockFormAnswerFrame answer(long requestId, String server, BedrockFormResponse response) {
        return new BedrockFormAnswerFrame(
                ProxyConnectionFrame.PROXY,
                frameId(),
                server,
                MIRA,
                requestId,
                Base64.getEncoder().encodeToString(BedrockFormCodec.encode(response)));
    }

    private static BedrockFormResponse submitted(String pin) {
        return new BedrockFormResponse.Submitted(java.util.Map.of("input", pin));
    }

    private static BedrockFormRequestFrame request(Sent sent) {
        return (BedrockFormRequestFrame) NetworkMessageCodec.decode(FrameSeal.frame(sent.bytes()));
    }

    /** The frame as it arrives over {@code carrier}'s connection: the transport notes who carried it. */
    private <T extends com.uxplima.uxmessentials.shared.network.NetworkMessage> T carried(UUID carrier, T frame) {
        UUID id =
                switch (frame) {
                    case BedrockPresenceFrame presence -> presence.frameId();
                    case BedrockFormAnswerFrame answer -> answer.frameId();
                    default -> throw new IllegalArgumentException("not a proxy frame");
                };
        nanos++;
        receipts.received(id, carrier);
        return frame;
    }

    private UUID frameId() {
        return new UUID(7L, ++frames);
    }

    private static final class QuietLogger implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A player the screen is handed: their id and name, nothing else. */
    private static final class FakePlayers {

        static Player player(UUID id, String name) {
            return (Player) java.lang.reflect.Proxy.newProxyInstance(
                    ProxyBedrockTest.class.getClassLoader(),
                    new Class<?>[] {Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> name;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
        }
    }
}
