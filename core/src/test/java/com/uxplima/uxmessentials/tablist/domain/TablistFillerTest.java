package com.uxplima.uxmessentials.tablist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class TablistFillerTest {

    @Test
    void carriesItsParts() {
        TablistSkinSource skin = new TablistSkinSource.PlayerName("Notch");
        TablistFiller filler = new TablistFiller(3, "<gold>Welcome", Optional.of(skin));

        assertThat(filler.slot()).isEqualTo(3);
        assertThat(filler.text()).isEqualTo("<gold>Welcome");
        assertThat(filler.skin()).contains(skin);
    }

    @Test
    void allowsNoSkin() {
        TablistFiller filler = new TablistFiller(1, "x", Optional.empty());
        assertThat(filler.skin()).isEmpty();
    }

    @Test
    void rejectsANonPositiveSlot() {
        assertThatThrownBy(() -> new TablistFiller(0, "x", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TablistFiller(-1, "x", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
