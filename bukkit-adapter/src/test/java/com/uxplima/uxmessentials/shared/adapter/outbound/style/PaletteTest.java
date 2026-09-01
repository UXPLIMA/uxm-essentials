package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import net.kyori.adventure.text.format.TextColor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/** The colours: what a server that wrote nothing sees, and what one line of a theme file changes. */
class PaletteTest {

    @Test
    @DisplayName("a server with no theme file keeps the colours this plugin ships")
    void shippedColoursAreUnchanged() {
        Palette palette = Palette.shipped();

        assertThat(palette.accent()).isEqualTo(TextColor.color(0x38b6ff));
        assertThat(palette.value()).isEqualTo(TextColor.color(0x8fd9ff));
        assertThat(palette.good()).isEqualTo(TextColor.color(0x5be38c));
        assertThat(palette.bad()).isEqualTo(TextColor.color(0xff6b6b));
        assertThat(palette.gold()).isEqualTo(TextColor.color(0xffc93c));
        assertThat(palette.emerald()).isEqualTo(TextColor.color(0x45d9a6));
    }

    @Test
    @DisplayName("a role may name a colour of the palette")
    void aRoleNamesAPaletteColour() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("palette", "sky").set("#48cae4");
        node.node("roles", "accent").set("sky");

        assertThat(Palette.from(node).accent()).isEqualTo(TextColor.color(0x48cae4));
    }

    @Test
    @DisplayName("a role the file leaves out keeps its shipped colour")
    void unwrittenRolesKeepTheirColour() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("roles", "accent").set("#ff0000");

        Palette palette = Palette.from(node);

        assertThat(palette.accent()).isEqualTo(TextColor.color(0xff0000));
        assertThat(palette.body()).isEqualTo(TextColor.color(0xffffff));
    }

    @Test
    @DisplayName("a role this plugin never heard of is still a role")
    void aServerMayInventARole() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("roles", "premium").set("#b388ff");

        Palette palette = Palette.from(node);

        assertThat(palette.has("premium")).isTrue();
        assertThat(palette.role("premium")).isEqualTo(TextColor.color(0xb388ff));
    }

    @Test
    @DisplayName("a gradient takes its second stop from the wheel when the file names one")
    void theWheelGivesTheLighterHalfOfARamp() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("palette", "mint").set("#4ecca3");
        node.node("palette", "sky").set("#48cae4");
        node.node("roles", "good").set("mint");
        node.node("wheel").setList(String.class, List.of("mint", "sky"));

        Palette palette = Palette.from(node);

        assertThat(palette.good()).isEqualTo(TextColor.color(0x4ecca3));
        assertThat(palette.emerald()).isEqualTo(TextColor.color(0x48cae4));
    }

    @Test
    @DisplayName("a value that is neither a colour nor a palette name fails at load")
    void nonsenseFailsLoudly() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("roles", "accent").set("mint");

        assertThatThrownBy(() -> Palette.from(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mint");
    }
}
