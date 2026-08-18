package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

class StyleTagsTest {

    private final MiniMessage mini = MiniMessage.miniMessage();

    private Component parse(String src) {
        return mini.deserialize(src, StyleTags.resolver());
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
                return node.color();
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
        assertThat(firstColor(parse("<accent>hello</accent>"))).isEqualTo(StyleTags.ACCENT);
    }

    @Test
    void valueCarriesItsOwnIceRatherThanTheAccent() {
        // A value used to be an alias of the accent. The palette gives it the lighter ice so a name or an
        // amount separates from the sky the prefix and the section labels are written in.
        assertThat(firstColor(parse("<value>a name</value>"))).isEqualTo(StyleTags.VALUE);
        assertThat(StyleTags.VALUE).isNotEqualTo(StyleTags.ACCENT);
    }

    @Test
    void moneyIsGoldAndSuccessIsGreen() {
        // The two also used to be aliases: an amount is gold, a positive outcome is green.
        assertThat(firstColor(parse("<money>500</money>"))).isEqualTo(StyleTags.MONEY);
        assertThat(firstColor(parse("<good>done</good>"))).isEqualTo(StyleTags.GOOD);
        assertThat(StyleTags.MONEY).isNotEqualTo(StyleTags.GOOD);
    }

    @Test
    void badAppliesRed() {
        assertThat(firstColor(parse("<bad>nope</bad>"))).isEqualTo(StyleTags.BAD);
    }

    @Test
    void loreToneTokensResolve() {
        assertThat(firstColor(parse("<subtext>line</subtext>"))).isEqualTo(StyleTags.SUBTEXT);
        assertThat(firstColor(parse("<dim>|</dim>"))).isEqualTo(StyleTags.DIM);
        assertThat(firstColor(parse("<icon>*</icon>"))).isEqualTo(StyleTags.ICON);
        assertThat(firstColor(parse("<crumb>kind</crumb>"))).isEqualTo(StyleTags.CRUMB);
        assertThat(firstColor(parse("<info>details</info>"))).isEqualTo(StyleTags.INFO);
    }

    @Test
    void tagRendersTheCategoryPrefixInSmallCapitals() {
        Component c = parse("<tag:'HOME'> hi");
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        // The label the catalog carries is the prefix word, small-capped, and exactly one space precedes the body.
        assertThat(plain).isEqualTo("ʜᴏᴍᴇ ▶ hi");
        assertThat(colorOfRun(c, "ʜᴏᴍᴇ")).isEqualTo(StyleTags.ACCENT);
        assertThat(colorOfRun(c, "▶")).isEqualTo(StyleTags.DIM);
        assertThat(anyBold(c)).isTrue();
    }

    @Test
    void aMoneyCategoryPrefixIsGreen() {
        // Money is the one subject the palette gives its own prefix colour, so a balance line is recognisable
        // before it is read.
        assertThat(colorOfRun(parse("<tag:'ECONOMY'> hi"), "ᴇᴄᴏɴᴏᴍʏ")).isEqualTo(StyleTags.GOOD);
        assertThat(colorOfRun(parse("<tag:'BANK'> hi"), "ʙᴀɴᴋ")).isEqualTo(StyleTags.GOOD);
    }

    @Test
    void etagRendersTheRedErrorWordWhicheverModuleRaisedIt() {
        Component c = parse("<etag:'ECONOMY'> oops");
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        assertThat(plain).isEqualTo("ᴇʀʀᴏʀ ▶ oops");
        assertThat(colorOfRun(c, "ᴇʀʀᴏʀ")).isEqualTo(StyleTags.BAD);
    }

    @Test
    void helpopAndStaffchatCarryDistinctLabelledPrefixes() {
        Component help = parse("<helpop> hi");
        Component staff = parse("<staffchat> hi");
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        // The two staff channels read apart from each other rather than both showing the brand prefix.
        assertThat(plain.serialize(help)).isEqualTo("ʜᴇʟᴘᴏᴘ ▶ hi");
        assertThat(plain.serialize(staff)).isEqualTo("ꜱᴛᴀꜰꜰᴄʜᴀᴛ ▶ hi");
        assertThat(colorOfRun(help, "ʜᴇʟᴘᴏᴘ")).isEqualTo(StyleTags.ACCENT);
        assertThat(colorOfRun(staff, "ꜱᴛᴀꜰꜰᴄʜᴀᴛ")).isEqualTo(StyleTags.ACCENT);
    }

    @Test
    void headerSmallCapsItsArgumentAndRendersItBoldSky() {
        // A header keeps its argument in plain ASCII in the catalog; the tag is what writes it in the
        // interface's small capitals, so a catalog author never types the glyphs by hand.
        Component c = parse("<h:'Home Panel'>");
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("ʜᴏᴍᴇ ᴘᴀɴᴇʟ");
        assertThat(firstColor(c)).isEqualTo(StyleTags.ACCENT);
        assertThat(anyBold(c)).isTrue();
    }
}
