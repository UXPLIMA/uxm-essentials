package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The fixed slot map of a trade window and the items that dress its controls. The window is a single six-row
 * (54-slot) chest split down the middle: the viewer's editable offer occupies the left four columns, the other
 * side's offer is mirrored read-only into the right four columns, and the centre column carries the controls — the
 * viewer's confirm button at the bottom, the partner's confirm status at the top, and a gray-glass divider filling
 * the rest. Editable slot {@code k} and mirror slot {@code k} line up on the same row, so a stack a player places at
 * their slot {@code k} shows at the counterpart's mirror slot {@code k}.
 *
 * <p>{@code slots-per-side} is capped to what the left/right column blocks can hold ({@value #MAX_PER_SIDE}); a
 * larger configured value simply uses the maximum the chest geometry allows. The layout writes only the divider and
 * control slots as filler on seed — the editable and mirror slots start empty (air) so a player can place into their
 * own side, and the mirror is painted by {@link TradeView} from the counterpart's offer.
 */
@NullMarked
final class TradeLayout {

    static final int SIZE = 54;
    static final int COLUMNS = 9;
    static final int ROWS = 6;
    static final int SIDE_COLUMNS = 4;

    /** The most offer slots one side can hold: four columns down six rows. */
    static final int MAX_PER_SIDE = SIDE_COLUMNS * ROWS;

    private static final int MIRROR_COLUMN_OFFSET = 5;
    private static final int CONFIRM_SLOT = 49; // row 5, centre column
    private static final int PARTNER_STATUS_SLOT = 4; // row 0, centre column

    private final int perSide;
    private final List<Integer> editableSlots;
    private final List<Integer> mirrorSlots;

    TradeLayout(int slotsPerSide) {
        this.perSide = Math.min(Math.max(slotsPerSide, 1), MAX_PER_SIDE);
        this.editableSlots = slots(0);
        this.mirrorSlots = slots(MIRROR_COLUMN_OFFSET);
    }

    private List<Integer> slots(int columnOffset) {
        List<Integer> slots = new ArrayList<>(perSide);
        for (int k = 0; k < perSide; k++) {
            slots.add((k / SIDE_COLUMNS) * COLUMNS + columnOffset + (k % SIDE_COLUMNS));
        }
        return List.copyOf(slots);
    }

    int perSide() {
        return perSide;
    }

    int editableSlot(int k) {
        return editableSlots.get(k);
    }

    int mirrorSlot(int k) {
        return mirrorSlots.get(k);
    }

    /** Whether {@code slot} is one the viewer may place into or take from — a real offer slot, not a control. */
    boolean isEditable(int slot) {
        return editableSlots.contains(slot);
    }

    int confirmSlot() {
        return CONFIRM_SLOT;
    }

    /** Paint the divider and control frame; the editable, mirror, confirm, and status slots are left for the view. */
    void seedFrame(Inventory inv) {
        Objects.requireNonNull(inv, "inv");
        ItemStack pane = filler();
        for (int slot = 0; slot < SIZE; slot++) {
            if (!editableSlots.contains(slot)
                    && !mirrorSlots.contains(slot)
                    && slot != CONFIRM_SLOT
                    && slot != PARTNER_STATUS_SLOT) {
                inv.setItem(slot, pane.clone());
            }
        }
    }

    /** Paint the two control items — the viewer's confirm button and the partner's status — for this render. */
    void renderControls(
            Inventory inv, Messages messages, PlayerRef viewer, boolean selfConfirmed, boolean partnerConfirmed) {
        inv.setItem(CONFIRM_SLOT, confirmButton(messages, viewer, selfConfirmed));
        inv.setItem(PARTNER_STATUS_SLOT, partnerStatus(messages, viewer, partnerConfirmed));
    }

    private ItemStack confirmButton(Messages messages, PlayerRef viewer, boolean confirmed) {
        Material material = confirmed ? Material.LIME_WOOL : Material.YELLOW_WOOL;
        TradeMessageKey key = confirmed ? TradeMessageKey.TRADE_WINDOW_CONFIRMED : TradeMessageKey.TRADE_WINDOW_CONFIRM;
        return named(material, messages, viewer, key);
    }

    private ItemStack partnerStatus(Messages messages, PlayerRef viewer, boolean confirmed) {
        Material material = confirmed ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        TradeMessageKey key = confirmed
                ? TradeMessageKey.TRADE_WINDOW_PARTNER_CONFIRMED
                : TradeMessageKey.TRADE_WINDOW_PARTNER_WAITING;
        return named(material, messages, viewer, key);
    }

    private ItemStack named(Material material, Messages messages, PlayerRef viewer, TradeMessageKey key) {
        Component name = StyledText.render(messages.resolve(viewer, key, Map.of()));
        return ItemBuilder.of(material).name(name).build();
    }

    private ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
    }

    private @Nullable ItemStack copyOf(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }

    /** Read one side's editable region into a positional array (nullable per slot), length {@link #perSide()}. */
    @Nullable ItemStack[] readOffer(Inventory inv) {
        Objects.requireNonNull(inv, "inv");
        @Nullable ItemStack[] offer = new ItemStack[perSide];
        for (int k = 0; k < perSide; k++) {
            offer[k] = copyOf(inv.getItem(editableSlots.get(k)));
        }
        return offer;
    }

    /** Paint {@code offer} into the mirror region of {@code inv} as read-only display copies, clearing empties. */
    void renderMirror(Inventory inv, @Nullable ItemStack @Nullable [] offer) {
        for (int k = 0; k < perSide; k++) {
            @Nullable ItemStack src = offer != null && k < offer.length ? offer[k] : null;
            inv.setItem(mirrorSlots.get(k), copyOf(src));
        }
    }

    /** Empty a view's editable region — the offered originals leave the window on settle so nothing is returned twice. */
    void clearOffer(Inventory inv) {
        for (int slot : editableSlots) {
            inv.setItem(slot, null);
        }
    }
}
