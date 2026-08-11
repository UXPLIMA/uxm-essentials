package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.io.DataInputStream;
import java.io.IOException;

import org.jspecify.annotations.NullMarked;

/**
 * The sanity limits every stored item payload is decoded under. Each of the plugin's binary codecs (vault contents,
 * inventory snapshots, villager recipes, staff loadouts) writes a count or a byte length ahead of the data it
 * describes, and decoding reads that number back and sizes an array from it. That is fine for a payload the plugin
 * itself wrote and wrong for anything else: a truncated row, a byte flipped by a failing disk, or a hand-edited
 * database can hand back a length of two billion, and the decoder would try to allocate it before discovering there
 * is nothing to fill it with. The result is an {@code OutOfMemoryError} on whichever thread happened to open the
 * vault, which is a far worse outcome than refusing one damaged row.
 *
 * <p>So a decoder asks here first. A slot count is clamped into a range no real container can exceed, and a
 * declared item length outside its bound fails the read as malformed, which the codec already reports as a
 * deserialization failure for that one payload. The limits are deliberately generous: they are a backstop against
 * absurd numbers, not a format constraint, and no payload the plugin writes comes close to them.
 */
@NullMarked
public final class PayloadLimits {

    /** The largest slot count any stored container section may declare; the biggest real one is a 54-slot chest. */
    public static final int MAX_SLOTS = 4096;

    /** The largest number of entries a stored list (recipes, effects, ingredients) may declare. */
    public static final int MAX_ENTRIES = 4096;

    /** The largest a single serialized item may declare itself to be, well above anything Minecraft produces. */
    public static final int MAX_ITEM_BYTES = 8 * 1024 * 1024;

    private PayloadLimits() {}

    /** {@code raw} clamped into {@code [0, MAX_SLOTS]}, for the array a decoded section is read into. */
    public static int slots(int raw) {
        return Math.clamp(raw, 0, MAX_SLOTS);
    }

    /** {@code raw} clamped into {@code [0, MAX_ENTRIES]}, for a list length read off a payload. */
    public static int entries(int raw) {
        return Math.clamp(raw, 0, MAX_ENTRIES);
    }

    /**
     * Read one length-prefixed serialized item off {@code in}. A declared length below zero or above
     * {@link #MAX_ITEM_BYTES} is not something this plugin ever wrote, so the payload is rejected as malformed
     * rather than acted on.
     */
    public static byte[] readItemBytes(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_ITEM_BYTES) {
            throw new IOException("stored item declares an impossible length: " + length);
        }
        return in.readNBytes(length);
    }
}
