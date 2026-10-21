package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StyleTagsTest {

    private Component parse(String src) {
        return StyledText.render(src);
    }

    private TextColor firstColor(Component root) {
        Deque<Component> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Component node = queue.removeFirst();
            TextColor color = node.color();
            if (color != null) {
                return color;
            }
            queue.addAll(node.children());
        }
        throw new AssertionError("no coloured node found in " + root);
    }

    private TextColor colorOfRun(Component root, String text) {
        Deque<Component> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Component node = queue.removeFirst();
            if (node instanceof net.kyori.adventure.text.TextComponent textNode
                    && textNode.content().equals(text)) {
                return java.util.Objects.requireNonNull(node.color(), "run '" + text + "' carries no colour");
            }
            queue.addAll(node.children());
        }
        throw new AssertionError("no run with text '" + text + "' found in " + root);
    }

    private boolean anyBold(Component root) {
        if (root.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
            return true;
        }
        return root.children().stream().anyMatch(this::anyBold);
    }

    @Test
    void accentAppliesTheBrandSky() {
        assertThat(firstColor(parse("<accent>hello</accent>"))).isEqualTo(StyleTags.accent());
    }

    @Test
    void valueCarriesItsOwnIceRatherThanTheAccent() {
        // A value used to be an alias of the accent. The palette gives it the lighter ice so a name or an
        // amount separates from the sky the prefix and the section labels are written in.
        assertThat(firstColor(parse("<value>a name</value>"))).isEqualTo(StyleTags.value());
        assertThat(StyleTags.value()).isNotEqualTo(StyleTags.accent());
    }

    @Test
    void moneyIsGoldAndSuccessIsGreen() {
        // The two also used to be aliases: an amount is gold, a positive outcome is green.
        assertThat(firstColor(parse("<money>500</money>"))).isEqualTo(StyleTags.money());
        assertThat(firstColor(parse("<good>done</good>"))).isEqualTo(StyleTags.good());
        assertThat(StyleTags.money()).isNotEqualTo(StyleTags.good());
    }

    @Test
    void badAppliesRed() {
        assertThat(firstColor(parse("<bad>nope</bad>"))).isEqualTo(StyleTags.bad());
    }

    @Test
    void loreToneTokensResolve() {
        assertThat(firstColor(parse("<subtext>line</subtext>"))).isEqualTo(StyleTags.subtext());
        assertThat(firstColor(parse("<dim>|</dim>"))).isEqualTo(StyleTags.dim());
        assertThat(firstColor(parse("<icon>*</icon>"))).isEqualTo(StyleTags.icon());
        assertThat(firstColor(parse("<crumb>kind</crumb>"))).isEqualTo(StyleTags.crumb());
        assertThat(firstColor(parse("<info>details</info>"))).isEqualTo(StyleTags.info());
    }

    @Test
    void tagRendersTheCategoryPrefixInSmallCapitals() {
        Component c = parse("<tag:'HOME'> hi");
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        // The label the catalog carries is the prefix word, small-capped, and exactly one space precedes the body.
        assertThat(plain).isEqualTo("ʜᴏᴍᴇ ▶ hi");
        assertThat(colorOfRun(c, "ʜᴏᴍᴇ")).isEqualTo(StyleTags.accent());
        assertThat(colorOfRun(c, "▶")).isEqualTo(StyleTags.dim());
        assertThat(anyBold(c)).isTrue();
    }

    @Test
    void aMoneyCategoryPrefixIsGreen() {
        // Money is the one subject the palette gives its own prefix colour, so a balance line is recognisable
        // before it is read.
        assertThat(colorOfRun(parse("<tag:'ECONOMY'> hi"), "ᴇᴄᴏɴᴏᴍʏ")).isEqualTo(StyleTags.good());
        assertThat(colorOfRun(parse("<tag:'BANK'> hi"), "ʙᴀɴᴋ")).isEqualTo(StyleTags.good());
    }

    /**
     * An error prefix shows the word its line writes, in the denial colour. It used to drop the label and write
     * "error" in English for every language; each catalogue now writes its own error word there, which
     * {@code EveryRefusalReadsAsAnErrorTest} keeps to one word per language.
     */
    @Test
    void etagRendersTheErrorWordItsLineWritesInRed() {
        Component c = parse("<etag:'HATA'> oops");
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        assertThat(plain).isEqualTo("ʜᴀᴛᴀ ▶ oops");
        assertThat(colorOfRun(c, "ʜᴀᴛᴀ")).isEqualTo(StyleTags.bad());
    }

    @Test
    void helpopAndStaffchatCarryDistinctLabelledPrefixes() {
        Component help = parse("<helpop> hi");
        Component staff = parse("<staffchat> hi");
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        // The two staff channels read apart from each other rather than both showing the brand prefix.
        assertThat(plain.serialize(help)).isEqualTo("ʜᴇʟᴘᴏᴘ ▶ hi");
        assertThat(plain.serialize(staff)).isEqualTo("ꜱᴛᴀꜰꜰᴄʜᴀᴛ ▶ hi");
        assertThat(colorOfRun(help, "ʜᴇʟᴘᴏᴘ")).isEqualTo(StyleTags.accent());
        assertThat(colorOfRun(staff, "ꜱᴛᴀꜰꜰᴄʜᴀᴛ")).isEqualTo(StyleTags.accent());
    }

    /**
     * Small capitals exist for some letters and not for others. A Turkish heading came out as {@code ʟᴏʙɪʏᴇ ʜᴏş}, half
     * converted, on every one of the eighty seven headings the Turkish catalogue carries. A heading either converts
     * whole or keeps its letters.
     */
    @Test
    void aHeadingWithALetterThatHasNoSmallCapitalKeepsItsLetters() {
        PlainTextComponentSerializer text = PlainTextComponentSerializer.plainText();
        assertThat(text.serialize(parse("<h:'Lobiye hoş geldin'>"))).isEqualTo("Lobiye hoş geldin");
        assertThat(text.serialize(parse("<h:'Größe'>"))).isEqualTo("Größe");
        assertThat(text.serialize(parse("<h:'Sezon'>"))).isEqualTo("ꜱᴇᴢᴏɴ");
        assertThat(text.serialize(parse("<h:'Xbox'>"))).isEqualTo("xʙᴏx");
        assertThat(text.serialize(parse("<tag:'Işınlanma'> hi"))).startsWith("Işınlanma");
    }

    @Test
    void aHeaderWithNoWheelKeepsTheBrandColour() {
        // The shipped palette names no wheel, so a position asks for an effect the server never set up.
        assertThat(firstColor(parse("<h:'Home Panel':3>"))).isEqualTo(StyleTags.accent());
    }

    @Test
    void aHeaderTakesTheArcTheWheelHoldsAtItsPosition(@TempDir Path plugins) throws Exception {
        Path dataFolder = Files.createDirectories(plugins.resolve("uxmEssentials"));
        Files.writeString(dataFolder.resolve("theme.conf"), "wheel = [\"#ff6b8b\", \"#4ecca3\", \"#48cae4\"]\n");
        StyleTags.use(ThemeFile.read(dataFolder));
        try {
            Component first = parse("<h:'Home Panel':1>");
            assertThat(PlainTextComponentSerializer.plainText().serialize(first))
                    .isEqualTo("ʜᴏᴍᴇ ᴘᴀɴᴇʟ");
            assertThat(firstColor(first)).isEqualTo(TextColor.color(0x4ecca3));
            assertThat(anyBold(first)).isTrue();
            // A second position reads as a second heading rather than as the same one repeated.
            assertThat(firstColor(parse("<h:'Home Panel':2>"))).isEqualTo(TextColor.color(0x48cae4));
        } finally {
            StyleTags.use(ThemeFile.shippedTheme());
        }
    }

    @Test
    void headerSmallCapsItsArgumentAndRendersItBoldSky() {
        // A header keeps its argument in plain ASCII in the catalog; the tag is what writes it in the
        // interface's small capitals, so a catalog author never types the glyphs by hand.
        Component c = parse("<h:'Home Panel'>");
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("ʜᴏᴍᴇ ᴘᴀɴᴇʟ");
        assertThat(firstColor(c)).isEqualTo(StyleTags.accent());
        assertThat(anyBold(c)).isTrue();
    }
}
