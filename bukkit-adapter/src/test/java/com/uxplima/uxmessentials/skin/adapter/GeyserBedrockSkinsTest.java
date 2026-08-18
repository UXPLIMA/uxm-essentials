package com.uxplima.uxmessentials.skin.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpFetcher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.adapter.outbound.GeyserBedrockSkins;
import org.junit.jupiter.api.Test;

/**
 * The Bedrock leg, against a fake HTTP seam.
 *
 * <p>The point of most of these is what does <em>not</em> happen: a Java player is never looked up, a server
 * without a Bedrock backend never asks anything, and no failure escapes into the login path that calls this.
 */
class GeyserBedrockSkinsTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final String XUID = "2535435405627446";
    private static final String PAYLOAD = "{\"value\":\"dmFsdWU=\",\"signature\":\"c2ln\"}";

    @Test
    void aBedrockPlayerGetsTheSignedTextureGeyserHolds() {
        FakeFetcher fetcher = new FakeFetcher(Map.of(GeyserBedrockSkins.SKIN_ENDPOINT + XUID, PAYLOAD));

        Optional<SkinTexture> texture = skins(fetcher, Optional.of(XUID), true).byPlayer(PLAYER);

        assertThat(texture).contains(new SkinTexture("dmFsdWU=", "c2ln"));
    }

    @Test
    void aJavaPlayerIsNeverLookedUp() {
        FakeFetcher fetcher = new FakeFetcher(Map.of());

        assertThat(skins(fetcher, Optional.empty(), true).byPlayer(PLAYER)).isEmpty();
        assertThat(fetcher.requested).isEmpty();
    }

    @Test
    void aServerWithoutABedrockBackendAsksNothingAndSaysSo() {
        FakeFetcher fetcher = new FakeFetcher(Map.of(GeyserBedrockSkins.SKIN_ENDPOINT + XUID, PAYLOAD));
        GeyserBedrockSkins skins = skins(fetcher, Optional.of(XUID), false);

        assertThat(skins.available()).isFalse();
        assertThat(skins.byPlayer(PLAYER)).isEmpty();
        assertThat(fetcher.requested).isEmpty();
    }

    @Test
    void aPlayerGeyserHoldsNoSkinForIsAnEmptyAnswerAskedOnce() {
        // The service answers 200 with an empty object for a player it has never cached; that is an answer, so
        // there is nothing to retry.
        FakeFetcher fetcher = new FakeFetcher(Map.of(GeyserBedrockSkins.SKIN_ENDPOINT + XUID, "{}"));

        assertThat(skins(fetcher, Optional.of(XUID), true).byPlayer(PLAYER)).isEmpty();
        assertThat(fetcher.requested).hasSize(1);
    }

    @Test
    void aFailedRequestIsRetriedUpToTheConfiguredCount() {
        FakeFetcher fetcher = new FakeFetcher(Map.of());

        assertThat(skins(fetcher, Optional.of(XUID), true).byPlayer(PLAYER)).isEmpty();
        assertThat(fetcher.requested).hasSize(3); // the first attempt plus two retries
    }

    @Test
    void aMalformedResponseIsAnEmptyAnswerRatherThanAnException() {
        FakeFetcher fetcher = new FakeFetcher(Map.of(GeyserBedrockSkins.SKIN_ENDPOINT + XUID, "not json"));

        assertThat(skins(fetcher, Optional.of(XUID), true).byPlayer(PLAYER)).isEmpty();
    }

    @Test
    void anUnsignedTextureIsStillATexture() {
        FakeFetcher fetcher = new FakeFetcher(Map.of(GeyserBedrockSkins.SKIN_ENDPOINT + XUID, "{\"value\":\"dmFs\"}"));

        assertThat(skins(fetcher, Optional.of(XUID), true)
                        .byPlayer(PLAYER)
                        .orElseThrow()
                        .signature())
                .isNull();
    }

    private GeyserBedrockSkins skins(FakeFetcher fetcher, Optional<String> xuid, boolean available) {
        return new GeyserBedrockSkins(player -> xuid, fetcher, new NoopLogger(), 2, available);
    }

    /** A fetcher answering from a fixed map and recording every uri it was asked for. */
    private static final class FakeFetcher implements HttpFetcher {

        private final Map<String, String> bodies;
        private final List<String> requested = new ArrayList<>();

        private FakeFetcher(Map<String, String> bodies) {
            this.bodies = bodies;
        }

        @Override
        public Optional<String> get(URI uri) {
            requested.add(uri.toString());
            return Optional.ofNullable(bodies.get(uri.toString()));
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
