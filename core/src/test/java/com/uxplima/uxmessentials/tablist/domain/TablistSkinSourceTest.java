package com.uxplima.uxmessentials.tablist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource.PlayerName;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource.Texture;
import org.junit.jupiter.api.Test;

class TablistSkinSourceTest {

    @Test
    void textureCarriesValueAndSignature() {
        Texture texture = new Texture("dmFsdWU=", Optional.of("sig"));

        assertThat(texture.value()).isEqualTo("dmFsdWU=");
        assertThat(texture.signature()).contains("sig");
    }

    @Test
    void textureRejectsABlankValue() {
        assertThatThrownBy(() -> new Texture(" ", Optional.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void playerNameCarriesTheName() {
        assertThat(new PlayerName("Notch").name()).isEqualTo("Notch");
    }

    @Test
    void playerNameRejectsABlankName() {
        assertThatThrownBy(() -> new PlayerName("")).isInstanceOf(IllegalArgumentException.class);
    }
}
