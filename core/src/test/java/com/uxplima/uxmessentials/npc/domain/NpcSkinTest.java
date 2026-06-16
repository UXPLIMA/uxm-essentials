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

    @Test
    void defaultsToTheClassicModel() {
        assertThat(new NpcSkin("tex", "sig").slim()).isFalse();
        assertThat(NpcSkin.unsigned("tex").slim()).isFalse();
    }

    @Test
    void withSlimTogglesTheModelKeepingTexture() {
        NpcSkin slim = new NpcSkin("tex", "sig").withSlim(true);
        assertThat(slim.slim()).isTrue();
        assertThat(slim.texture()).isEqualTo("tex");
        assertThat(slim.signature()).isEqualTo("sig");

        assertThat(slim.withSlim(false).slim()).isFalse();
    }

    @Test
    void carriesAnExplicitVariant() {
        assertThat(new NpcSkin("tex", "sig", true).slim()).isTrue();
        assertThat(new NpcSkin("tex", "sig", false).slim()).isFalse();
    }
}
