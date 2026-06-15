package com.uxplima.uxmessentials.npc.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NpcSkinTest {

    @Test
    void carriesTextureAndSignature() {
        NpcSkin skin = new NpcSkin("tex", "sig");
        assertThat(skin.texture()).isEqualTo("tex");
        assertThat(skin.signature()).isEqualTo("sig");
    }

    @Test
    void unsignedHasNoSignature() {
        assertThat(NpcSkin.unsigned("tex").signature()).isNull();
    }

    @Test
    void rejectsBlankTexture() {
        assertThatThrownBy(() -> new NpcSkin("  ", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
