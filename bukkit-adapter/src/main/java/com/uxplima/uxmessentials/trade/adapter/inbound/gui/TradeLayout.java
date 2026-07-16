package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.math.BigDecimal;
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
    private static final int PARTNER_MONEY_SLOT = 13; // row 1, centre column — the other side's staked money, read-only

    /**
     * The centre-column slots the viewer's own per-currency "add money" buttons occupy, one per allowed currency, in
     * priority order from just above the confirm button upward. Capped to this list, so a config listing more
     * currencies than there are slots simply shows the first few as buttons.
     */
    private static final List<Integer> MONEY_BUTTON_SLOTS = List.of(40, 31, 22); // rows 4, 3, 2 of the centre column

    private final int perSide;
    private final List<Integer> editableSlots;
    private final List<Integer> mirrorSlots;

    /** The allowed currency ids the viewer may stake, capped to {@link #MONEY_BUTTON_SLOTS}; empty when money is off. */
    private final List<String> moneyCurrencies;

    TradeLayout(int slotsPerSide, List<String> moneyCurrencies) {
        Objects.requireNonNull(moneyCurrencies, "moneyCurrencies");
        this.perSide = Math.min(Math.max(slotsPerSide, 1), MAX_PER_SIDE);
        this.editableSlots = slots(0);
        this.mirrorSlots = slots(MIRROR_COLUMN_OFFSET);
        int cap = Math.min(moneyCurrencies.size(), MONEY_BUTTON_SLOTS.size());
        this.moneyCurrencies = List.copyOf(moneyCurrencies.subList(0, cap));
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

    /**
     * The allowed currency id whose "add money" button sits at {@code rawSlot}, or {@code null} when {@code rawSlot} is
     * not one of the viewer's money buttons — the listener routes a money-button click to the amount prompt through it.
     */
    @Nullable String moneyCurrencyAt(int rawSlot) {
        for (int i = 0; i < moneyCurrencies.size(); i++) {
            if (MONEY_BUTTON_SLOTS.get(i) == rawSlot) {
                return moneyCurrencies.get(i);
            }
        }
        return null;
    }

    /** Paint the divider and control frame; the editable, mirror, control, and money slots are left for the view. */
    void seedFrame(Inventory inv) {
        Objects.requireNonNull(inv, "inv");
        ItemStack pane = filler();
        for (int slot = 0; slot < SIZE; slot++) {
            if (!editableSlots.contains(slot) && !mirrorSlots.contains(slot) && !isControlSlot(slot)) {
                inv.setItem(slot, pane.clone());
            }
        }
    }

    /** Whether {@code slot} carries a control or money item the view paints, so {@link #seedFrame} leaves it alone. */
    private boolean isControlSlot(int slot) {
        if (slot == CONFIRM_SLOT || slot == PARTNER_STATUS_SLOT) {
            return true;
        }
        if (moneyCurrencies.isEmpty()) {
            return false;
        }
        return slot == PARTNER_MONEY_SLOT
                || MONEY_BUTTON_SLOTS.subList(0, moneyCurrencies.size()).contains(slot);
    }

    /** Paint the two control items — the viewer's confirm button and the partner's status — for this render. */
    void renderControls(
            Inventory inv, Messages messages, PlayerRef viewer, boolean selfConfirmed, boolean partnerConfirmed) {
        inv.setItem(CONFIRM_SLOT, confirmButton(messages, viewer, selfConfirmed));
        inv.setItem(PARTNER_STATUS_SLOT, partnerStatus(messages, viewer, partnerConfirmed));
    }

    /**
     * Paint the money row: one "add money" button per allowed currency showing the viewer's own staked amount, and the
     * read-only display of the other side's staked money. A no-op when money is off (no economy wired), so those slots
     * stay the divider filler {@link #seedFrame} laid down.
     */
    void renderMoney(
            Inventory inv,
            Messages messages,
            PlayerRef viewer,
            Map<String, BigDecimal> ownMoney,
            Map<String, BigDecimal> partnerMoney) {
        if (moneyCurrencies.isEmpty()) {
            return;
        }
        for (int i = 0; i < moneyCurrencies.size(); i++) {
            String currency = moneyCurrencies.get(i);
            BigDecimal amount = ownMoney.getOrDefault(currency, BigDecimal.ZERO);
            inv.setItem(MONEY_BUTTON_SLOTS.get(i), moneyButton(messages, viewer, currency, amount));
        }
        inv.setItem(PARTNER_MONEY_SLOT, partnerMoneyDisplay(messages, viewer, partnerMoney));
    }

    private ItemStack moneyButton(Messages messages, PlayerRef viewer, String currency, BigDecimal amount) {
        Component name = StyledText.render(
                messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_MONEY, Map.of("currency", currency)));
        return ItemBuilder.of(Material.GOLD_INGOT)
                .name(name)
                .lore(amountLine(messages, viewer, currency, amount))
                .build();
    }

    private ItemStack partnerMoneyDisplay(Messages messages, PlayerRef viewer, Map<String, BigDecimal> partnerMoney) {
        Component name =
                StyledText.render(messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_PARTNER_MONEY, Map.of()));
        List<Component> lore = new ArrayList<>();
        for (String currency : moneyCurrencies) {
            lore.add(amountLine(messages, viewer, currency, partnerMoney.getOrDefault(currency, BigDecimal.ZERO)));
        }
        return ItemBuilder.of(Material.SUNFLOWER).name(name).lore(lore).build();
    }

    private Component amountLine(Messages messages, PlayerRef viewer, String currency, BigDecimal amount) {
        return StyledText.render(messages.resolve(
                viewer,
                TradeMessageKey.TRADE_WINDOW_MONEY_AMOUNT,
                Map.of("currency", currency, "amount", amount.toPlainString())));
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
