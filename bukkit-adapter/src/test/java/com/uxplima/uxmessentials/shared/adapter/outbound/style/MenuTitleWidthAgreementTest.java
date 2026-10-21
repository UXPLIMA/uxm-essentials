package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.style.MenuTitles;
import com.uxplima.uxmlib.text.GlyphWidthTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A window title of this plugin sits exactly where the same title sits in every other plugin.
 *
 * <p>Both centre a chest title by the same arithmetic: the window is 176 pixels, the title starts at 8, and the free
 * space is halved into spaces. This plugin once measured with a width table of its own and the library with another,
 * so the same title could sit at two offsets on one server, in one font. That is the defect the style canon exists to
 * prevent, and this estate has already paid for it once: see
 * {@code uxm-briefs/docs/notes/2026-08-29-a-title-centred-twice.md}. The plugin's table is gone and its titles go
 * through the library's {@code MenuTitles}; this keeps it so, by drawing each title both ways and comparing.
 */
final class MenuTitleWidthAgreementTest {

    /** Strings a real menu title is made of: words, digits, punctuation and the narrow characters. */
    private static final List<String> TITLES = List.of(
            "Shop",
            "Player Vaults",
            "Warps",
            "Auction House",
            "iiiiiiiiii",
            "llllllllll",
            "MMMMMMMMMM",
            "WWWWWWWWWW",
            "1234567890",
            "Vault 3 of 12",
            ".,:;'!|",
            "Kit Selector",
            "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "ᴋɪᴛ ꜱᴇʟᴇᴄᴛᴏʀ");

    @Test
    @DisplayName("a title is padded by exactly as many spaces as the library pads it")
    void thisPluginPadsATitleAsTheLibraryDoes() {
        List<String> disagreed = new ArrayList<>();
        for (String title : TITLES) {
            String ours = plain(StyledText.title(Component.text(title)));
            String theirs = plain(MenuTitles.centre(Component.text(title)));
            if (!ours.equals(theirs)) {
                disagreed.add(title + ": ours '" + ours + "', the library's '" + theirs + "'");
            }
        }

        assertThat(disagreed)
                .describedAs("a title padded differently is a title centred differently, and an operator"
                        + " sees two of our menus side by side")
                .isEmpty();
    }

    @Test
    @DisplayName("the space a title is padded with is the width the library counts in")
    void thespaceIsTheSameWidth() {
        String padded = plain(StyledText.title(Component.text("Warps")));
        int spaces = padded.length() - padded.stripLeading().length();
        int middle = 8 + GlyphWidthTable.SPACE_WIDTH * spaces + GlyphWidthTable.widthOf("Warps", false) / 2;

        assertThat(Math.abs(middle - 88))
                .describedAs("the padding is counted in spaces, so a space of a different width shifts"
                        + " every title by a whole character")
                .isLessThanOrEqualTo(2);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
