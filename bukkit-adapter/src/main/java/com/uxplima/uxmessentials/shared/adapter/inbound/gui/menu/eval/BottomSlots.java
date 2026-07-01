package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

/**
 * The pure slot arithmetic behind a bottom-inventory menu, where a menu paints items into the viewer's own 36
 * inventory slots shown below the chest top. In a chest {@code InventoryView} the top occupies raw slots
 * {@code 0..topSize-1} and the player's own inventory follows in raw slots {@code topSize..topSize+35}. Bukkit's
 * player-inventory convention orders those 36 as the 27 main-storage slots first (player slots 9..35) then the 9
 * hotbar slots (player slots 0..8), so a raw offset must be re-mapped to reach the right {@code PlayerInventory}
 * index. Kept here as plain math, free of any Bukkit type, so the mapping is unit-testable without a server and the
 * menu engine's pure-core fence stays green.
 */
public final class BottomSlots {

    /** The number of the viewer's own inventory slots a bottom-inventory menu may paint into (27 main + 9 hotbar). */
    public static final int PLAYER_SLOTS = 36;

    /** The count of main-storage slots shown before the hotbar in a chest view's bottom half. */
    private static final int MAIN_SLOTS = 27;

    /** The number of hotbar slots, the offset by which a main-storage raw slot leads its player index. */
    private static final int HOTBAR_SLOTS = 9;

    private BottomSlots() {}

    /**
     * Map a view raw slot to the {@link org.bukkit.inventory.PlayerInventory} index it addresses. A raw slot below
     * {@code topSize} is a chest-top slot, not a bottom one, and is returned unchanged (the caller never paints it
     * into the player inventory). At or past {@code topSize} the raw offset {@code 0..35} is re-ordered to Bukkit's
     * hotbar-last player layout: an offset in the first 27 is a main-storage slot ({@code offset + 9}), and the last
     * 9 are the hotbar ({@code offset - 27}). For a 54-slot top this maps raw 54 &rarr; 9, raw 80 &rarr; 35, raw 81
     * &rarr; 0 and raw 89 &rarr; 8.
     */
    public static int rawToPlayerSlot(int rawSlot, int topSize) {
        if (rawSlot < topSize) {
            return rawSlot;
        }
        int offset = rawSlot - topSize;
        return offset < MAIN_SLOTS ? offset + HOTBAR_SLOTS : offset - MAIN_SLOTS;
    }
}
