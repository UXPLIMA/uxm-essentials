package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A HUD line is parsed once per text. Every player's sidebar and tab list are drawn each second, and most of their
 * lines read the same for everybody, yet each one went through the style tokens and the MiniMessage parser again:
 * with thirty players that was most of the sidebar's cost. A new theme draws every line afresh.
 */
class HudTextParsesALineOnceTest {

    @AfterEach
    void restoreTheShippedTheme() {
        StyleTags.use(ThemeFile.shippedTheme());
    }

    @Test
    void theSameTextGivesTheSameLine() {
        Component first = HudText.parse("<accent>UXM</accent> <muted>lobby</muted>");

        assertThat(HudText.parse("<accent>UXM</accent> <muted>lobby</muted>")).isSameAs(first);
    }

    @Test
    void aNewThemeDrawsTheLineAgain() {
        Component before = HudText.parse("<accent>play.uxm.net</accent>");

        StyleTags.use(ThemeFile.shippedTheme());

        Component after = HudText.parse("<accent>play.uxm.net</accent>");
        assertThat(after).isNotSameAs(before).isEqualTo(before);
    }
}
