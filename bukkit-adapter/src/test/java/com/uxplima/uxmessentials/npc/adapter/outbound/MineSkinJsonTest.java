package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link MineSkinJson}'s defensive extraction of the signed texture value/signature from a MineSkin
 * generate response, across the response shapes the public API has used ({@code data.texture.*}, a bare
 * {@code texture.*}, and the v2 {@code texture.data.*}), and its fail-soft empties for a malformed or partial
 * body.
 */
class MineSkinJsonTest {

    @Test
    void extractsValueAndSignatureFromDataTextureShape() {
        String body = "{\"data\":{\"texture\":{\"value\":\"dGV4dA==\",\"signature\":\"sig=\"}}}";

        Optional<NpcSkin> skin = MineSkinJson.skin(body);

        assertThat(skin).contains(new NpcSkin("dGV4dA==", "sig="));
    }

    @Test
    void extractsFromBareTextureShape() {
        String body = "{\"texture\":{\"value\":\"dGV4dA==\",\"signature\":\"sig=\"}}";

        assertThat(MineSkinJson.skin(body)).contains(new NpcSkin("dGV4dA==", "sig="));
    }

    @Test
    void extractsFromNestedTextureDataShape() {
        // The v2 queue response nests the strings one level deeper under texture.data.
        String body = "{\"texture\":{\"data\":{\"value\":\"dGV4dA==\",\"signature\":\"sig=\"}}}";

        assertThat(MineSkinJson.skin(body)).contains(new NpcSkin("dGV4dA==", "sig="));
    }

    @Test
    void aValueWithNoSignatureYieldsAnUnsignedSkin() {
        String body = "{\"data\":{\"texture\":{\"value\":\"dGV4dA==\"}}}";

        Optional<NpcSkin> skin = MineSkinJson.skin(body);

        assertThat(skin).isPresent();
        assertThat(skin.orElseThrow().texture()).isEqualTo("dGV4dA==");
        assertThat(skin.orElseThrow().signature()).isNull();
    }

    @Test
    void aMissingTextureYieldsEmpty() {
        assertThat(MineSkinJson.skin("{\"data\":{\"id\":42}}")).isEmpty();
    }

    @Test
    void aBlankValueYieldsEmpty() {
        assertThat(MineSkinJson.skin("{\"data\":{\"texture\":{\"value\":\"\"}}}"))
                .isEmpty();
    }

    @Test
    void malformedJsonYieldsEmpty() {
        assertThat(MineSkinJson.skin("not json {")).isEmpty();
    }
}
