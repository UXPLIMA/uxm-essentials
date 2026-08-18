package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import org.jspecify.annotations.NullMarked;

/**
 * Renders plain ASCII words in the small-capital letters the interface is written in. The style canon writes
 * fixed text in small caps ({@code ʜᴏᴍᴇ}) rather than ordinary capitals, and the message catalog already carries
 * its text that way. What still arrives in ASCII is text the catalog cannot pre-convert: the category label a
 * {@code <tag:'HOME'>} prefix carries as an argument, and labels built in code. Those pass through here.
 *
 * <p>Only the twenty-six Latin letters map. Digits, punctuation and every non-ASCII character are returned
 * untouched, because a player has to be able to read a number and a symbol has no small-capital form. Two letters
 * are special: small-capital {@code x} does not exist in Unicode so {@code x} stays as it is, and {@code q} maps
 * to the Latin letter small capital {@code ǫ}, which is the shape the canon uses.
 */
@NullMarked
public final class SmallCaps {

    private static final String ASCII = "abcdefghijklmnopqrstuvwxyz";
    private static final String[] SMALL = {
        "ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ", "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ",
        "ᴡ", "x", "ʏ", "ᴢ"
    };

    private SmallCaps() {}

    /** {@code text} with every ASCII letter replaced by its small-capital form; everything else is unchanged. */
    public static String of(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = ASCII.indexOf(Character.toLowerCase(c));
            if (index >= 0 && isLatinLetter(c)) {
                out.append(SMALL[index]);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isLatinLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
