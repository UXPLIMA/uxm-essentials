package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class NpcSkinModelTest {

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    @Test
    void injectsTheSlimModelIntoAClassicTexture() {
        String classic = encode("{\"textures\":{\"SKIN\":{\"url\":\"http://textures/abc\"}}}");

        Optional<String> slim = NpcSkinModel.slimTexture(classic);

        assertThat(slim).isPresent();
        assertThat(decode(slim.get())).contains("\"model\":\"slim\"").contains("http://textures/abc");
    }

    @Test
    void leavesAnAlreadySlimTextureUnchanged() {
        String alreadySlim =
                encode("{\"textures\":{\"SKIN\":{\"url\":\"http://textures/abc\",\"metadata\":{\"model\":\"slim\"}}}}");

        assertThat(NpcSkinModel.slimTexture(alreadySlim)).isEmpty();
    }

    @Test
    void ignoresAValueThatIsNotBase64() {
        assertThat(NpcSkinModel.slimTexture("not base64 @@@")).isEmpty();
    }

    @Test
    void ignoresAValueThatCarriesNoSkinTexture() {
        String capeOnly = encode("{\"textures\":{\"CAPE\":{\"url\":\"http://textures/cape\"}}}");

        assertThat(NpcSkinModel.slimTexture(capeOnly)).isEmpty();
    }
}
