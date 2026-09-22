package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.text.GlyphWidthTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * This plugin and the library measure a menu title the same way.
 *
 * <p>Both centre a chest title by the same arithmetic: the window is 176 pixels, the title starts at 8,
 * and the free space is halved into spaces. The only thing that can make them disagree is the width table
 * underneath, and they use different ones: {@code FontWidths} here, {@code GlyphWidthTable} there.
 *
 * <p>If the two tables give different answers then a menu title in this plugin sits at a different offset
 * from a menu title in every other plugin we ship, on the same server, in the same font. That is the
 * defect the style canon exists to prevent, and this estate has already paid for it once: see
 * {@code uxm-briefs/docs/notes/2026-08-29-a-title-centred-twice.md}.
 *
 * <p>This test is the question, not the fix. It fails on the first character the two disagree about, and
 * what it prints is the list.
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
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ");

    @Test
    @DisplayName("the two width tables measure every title the same")
    void thetwoTablesAgree() {
        List<String> disagreed = new ArrayList<>();
        for (String title : TITLES) {
            int ours = FontWidths.of(title);
            int theirs = GlyphWidthTable.widthOf(title, false);
            if (ours != theirs) {
                disagreed.add(title + ": ours " + ours + ", the library's " + theirs);
            }
        }

        assertThat(disagreed)
                .describedAs("a title measured differently is a title centred differently, and an operator"
                        + " sees two of our menus side by side")
                .isEmpty();
    }

    @Test
    @DisplayName("the space a title is padded with is the same width in both")
    void thespaceIsTheSameWidth() {
        assertThat(FontWidths.of(" "))
                .describedAs("the padding is counted in spaces, so a space of a different width shifts"
                        + " every title by a whole character")
                .isEqualTo(GlyphWidthTable.SPACE_WIDTH);
    }
}
