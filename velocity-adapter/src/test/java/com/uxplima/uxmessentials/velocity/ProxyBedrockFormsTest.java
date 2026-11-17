package com.uxplima.uxmessentials.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.shared.network.BedrockFormAnswerFrame;
import com.uxplima.uxmessentials.shared.network.BedrockFormRequestFrame;
import com.uxplima.uxmessentials.shared.network.BedrockPresenceFrame;
import com.uxplima.uxmessentials.shared.network.BusSecret;
import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import com.uxplima.uxmlib.bedrock.BedrockFormCodec;
import com.uxplima.uxmlib.bedrock.BedrockFormRequest;
import com.uxplima.uxmlib.bedrock.BedrockFormResponse;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

/**
 * The proxy shows a Bedrock player the form a backend asked for and hands the answer to that backend alone: only a
 * sealed request carried by that player's own connection is shown, and an answer after a switch goes nowhere.
 */
class ProxyBedrockFormsTest {

    private static final String SECRET = "vK8pQ2xR7mT4nW9cL3hF6jD1sB5gY0zA";
    private static final UUID MIRA = new UUID(0L, 0x0009_01f2_a3b4_c5d6L);
    private static final UUID ALEX = new UUID(0x5b1e_2f3a_4c5d_4e6fL, 0x8a9b_0c1d_2e3f_4a5bL);

    private final FrameSeal seal = new FrameSeal(BusSecret.of(SECRET), () -> 1_700_000_000_000L);
    private final AtomicLong ids = new AtomicLong();
    private final FakeGeyser geyser = new FakeGeyser();
    private final List<Delivered> delivered = new ArrayList<>();
    private final ProxyBedrockForms forms = new ProxyBedrockForms(
            seal,
            geyser,
            (player, server, bytes) -> delivered.add(new Delivered(player, server, bytes)),
            () -> new UUID(9L, ids.incrementAndGet()),
            NOPLogger.NOP_LOGGER);

    @Test
    void aBackendHearsWhichOfItsPlayersAreBedrockPlayers() {
        geyser.bedrock.add(MIRA);

        forms.connected(MIRA, "survival");
        forms.connected(ALEX, "survival");

        assertThat(delivered).singleElement().satisfies(sent -> {
            assertThat(sent.player()).isEqualTo(MIRA);
            assertThat(sent.server()).isEqualTo("survival");
            assertThat(seal.check(sent.bytes())).isEqualTo(FrameSeal.Check.TRUSTED);
            BedrockPresenceFrame presence = (BedrockPresenceFrame) read(sent);
            assertThat(presence.originServer()).isEqualTo(ProxyConnectionFrame.PROXY);
            assertThat(presence.server()).isEqualTo("survival");
            assertThat(presence.xuid()).isEqualTo("2535412345678901");
        });
    }

    @Test
    void aTypedSecretGoesBackToTheBackendThatAskedAndOnlyOnce() throws Exception {
        geyser.bedrock.add(MIRA);

        forms.request(MIRA, "survival", request(MIRA, 4L, pinForm()));
        Form shown = geyser.shown.getFirst();
        answer(shown, "[\"4821\"]");
        answer(shown, "[\"0000\"]");

        assertThat(delivered).singleElement().satisfies(sent -> {
            assertThat(sent.player()).isEqualTo(MIRA);
            assertThat(sent.server()).isEqualTo("survival");
            assertThat(seal.check(sent.bytes())).isEqualTo(FrameSeal.Check.TRUSTED);
            BedrockFormAnswerFrame answer = (BedrockFormAnswerFrame) read(sent);
            assertThat(answer.toServer()).isEqualTo("survival");
            assertThat(answer.requestId()).isEqualTo(4L);
            assertThat(BedrockFormCodec.decodeResponse(Base64.getDecoder().decode(answer.answer())))
                    .isEqualTo(new BedrockFormResponse.Submitted(Map.of("pin", "4821")));
        });
    }

    @Test
    void anAnswerAfterTheSwitchOrTheLeaveGoesNowhere() throws Exception {
        geyser.bedrock.add(MIRA);
        forms.request(MIRA, "survival", request(MIRA, 1L, pinForm()));
        forms.request(MIRA, "survival", request(MIRA, 2L, pinForm()));

        forms.left(MIRA);
        answer(geyser.shown.get(0), "[\"4821\"]");
        answer(geyser.shown.get(1), "null");

        assertThat(delivered).isEmpty();
    }

    @Test
    void aRequestNotSealedWithTheSecretOrCarriedByAnotherPlayerIsNeverShown() {
        geyser.bedrock.add(MIRA);
        FrameSeal stranger = new FrameSeal(BusSecret.of("sJ2gF0dH5cB3yN6wZ1tR8pL4vX9m2Q7k"), () -> 1_700_000_000_000L);
        byte[] forged = stranger.seal(NetworkMessageCodec.encode(frame(MIRA, 1L, pinForm())));

        forms.request(MIRA, "survival", forged);
        forms.request(ALEX, "survival", request(MIRA, 2L, pinForm()));

        assertThat(geyser.shown).isEmpty();
        assertThat(delivered).isEmpty();
    }

    @Test
    void aFormGeyserCannotShowAnswersClosedSoTheBackendStopsWaiting() {
        forms.request(MIRA, "survival", request(MIRA, 3L, pinForm()));

        assertThat(delivered)
                .singleElement()
                .satisfies(sent -> assertThat(BedrockFormCodec.decodeResponse(
                                Base64.getDecoder().decode(((BedrockFormAnswerFrame) read(sent)).answer())))
                        .isEqualTo(BedrockFormResponse.CLOSED));
    }

    private static BedrockFormRequest pinForm() {
        return new BedrockFormRequest.Custom("PIN", null, List.of(new BedrockWidget.Input("pin", "Your PIN", "", "")));
    }

    private static BedrockFormRequestFrame frame(UUID player, long requestId, BedrockFormRequest form) {
        return new BedrockFormRequestFrame(
                "survival", player, requestId, Base64.getEncoder().encodeToString(BedrockFormCodec.encode(form)));
    }

    private byte[] request(UUID player, long requestId, BedrockFormRequest form) {
        return seal.seal(NetworkMessageCodec.encode(frame(player, requestId, form)));
    }

    private static Object read(Delivered sent) {
        return NetworkMessageCodec.decode(FrameSeal.frame(sent.bytes()));
    }

    /** The client's raw answer, handled the way Geyser handles it on receipt. */
    private static void answer(Form form, String raw) throws Exception {
        FormDefinitions.instance().definitionFor(form).handleFormResponse(form, raw);
    }

    /** What went to a backend over a player's connection. */
    private static final class Delivered {
        private final UUID player;
        private final String server;
        private final byte[] bytes;

        Delivered(UUID player, String server, byte[] bytes) {
            this.player = player;
            this.server = server;
            this.bytes = bytes.clone();
        }

        UUID player() {
            return player;
        }

        String server() {
            return server;
        }

        byte[] bytes() {
            return bytes.clone();
        }
    }

    /** Geyser as the proxy plugin sees it: the Bedrock players it knows and the forms it was asked to show. */
    private static final class FakeGeyser implements ProxyBedrockForms.GeyserPlayers {

        final Set<UUID> bedrock = new HashSet<>();
        final List<Form> shown = new ArrayList<>();

        @Override
        public Optional<BedrockPresenceFrame> presence(UUID player, String server, UUID frameId) {
            if (!bedrock.contains(player)) {
                return Optional.empty();
            }
            return Optional.of(new BedrockPresenceFrame(
                    ProxyConnectionFrame.PROXY,
                    frameId,
                    player,
                    server,
                    "2535412345678901",
                    "GOOGLE",
                    "TOUCH",
                    "POCKET",
                    "1.21.90",
                    "tr_TR"));
        }

        @Override
        public boolean send(UUID player, Form form) {
            if (!bedrock.contains(player)) {
                return false;
            }
            shown.add(form);
            return true;
        }
    }
}
