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
 * The fixed slot map of a trade window and the items that dress its controls, laid out in the AxTrade style: a six-row
 * (54-slot) chest whose top row (row 0) carries every control and whose five lower rows hold the offered items, split
 * down the centre by a full glass divider column. The viewer's editable offer occupies the left four columns of rows
 * 1-5 ({@code 9,10,11,12, 18,..,21, 27,..,30, 36,..,39, 45,..,48}), the other side's offer is mirrored read-only into
 * the right four columns ({@code 14,15,16,17, ..., 50,51,52,53}), and column 4 ({@code 4,13,22,31,40,49}) is the glass
 * divider. Editable slot {@code k} and mirror slot {@code k} line up on the same row, so a stack a player places at
 * their slot {@code k} shows at the counterpart's mirror slot {@code k}.
 *
 * <p>The control row reads viewer-left, partner-right: slot {@code 0} is the viewer's confirm button, {@code 2} their
 * money button, {@code 3} their experience button; slot {@code 5} shows the partner's staked experience, {@code 6}
 * their staked money, and {@code 8} the partner's confirm status. Slots {@code 1} and {@code 7} are glass filler.
 * Because our economy is multi-currency but AxTrade gives money a single slot, the money button holds one selected
 * currency at a time: a left-click stakes it, a right-click cycles to the next allowed currency, and each currency's
 * staked amount is preserved across the cycle, so no currency support is lost. When money is off (no economy wired) or
 * experience is off (the items-only cross-server window), those control slots fall back to divider filler.
 *
 * <p>{@code slots-per-side} is capped to what the item grid can hold ({@value #MAX_PER_SIDE}); a larger configured
 * value simply uses the maximum the chest geometry allows. The layout writes only the divider and control slots as
 * filler on seed, the editable and mirror slots start empty (air) so a player can place into their own side, and the
 * mirror is painted by {@link TradeView} from the counterpart's offer.
 */
@NullMarked
final class TradeLayout {

    static final int SIZE = 54;
    static final int COLUMNS = 9;
    static final int ROWS = 6;
    static final int SIDE_COLUMNS = 4;

    /** The rows below the control row that hold offered items, rows 1-5. */
    private static final int ITEM_ROWS = ROWS - 1;

    /** The most offer slots one side can hold: four columns down the five item rows. */
    static final int MAX_PER_SIDE = SIDE_COLUMNS * ITEM_ROWS;

    private static final int MIRROR_COLUMN_OFFSET = 5;

    // Control row (row 0), viewer-left / partner-right.
    private static final int CONFIRM_SLOT = 0; // the viewer's confirm button
    private static final int MONEY_SLOT = 2; // the viewer's single "add money" button
    private static final int EXPERIENCE_SLOT = 3; // the viewer's "add experience" button
    private static final int PARTNER_EXPERIENCE_SLOT = 5; // the other side's staked experience, read-only
    private static final int PARTNER_MONEY_SLOT = 6; // the other side's staked money, read-only
    private static final int PARTNER_STATUS_SLOT = 8; // the other side's confirm status, read-only

    private final int perSide;
    private final List<Integer> editableSlots;
    private final List<Integer> mirrorSlots;

    /** The allowed currency ids the viewer may stake through the single money button; empty when money is off. */
    private final List<String> moneyCurrencies;

    /** Whether the experience button is shown; off for the items-only cross-server window. */
    private final boolean experienceEnabled;

    TradeLayout(int slotsPerSide, List<String> moneyCurrencies) {
        this(slotsPerSide, moneyCurrencies, true);
    }

    TradeLayout(int slotsPerSide, List<String> moneyCurrencies, boolean experienceEnabled) {
        Objects.requireNonNull(moneyCurrencies, "moneyCurrencies");
        this.perSide = Math.min(Math.max(slotsPerSide, 1), MAX_PER_SIDE);
        this.editableSlots = slots(0);
        this.mirrorSlots = slots(MIRROR_COLUMN_OFFSET);
        this.moneyCurrencies = List.copyOf(moneyCurrencies);
        this.experienceEnabled = experienceEnabled;
    }

    private List<Integer> slots(int columnOffset) {
        List<Integer> slots = new ArrayList<>(perSide);
        for (int k = 0; k < perSide; k++) {
            slots.add(COLUMNS + (k / SIDE_COLUMNS) * COLUMNS + columnOffset + (k % SIDE_COLUMNS));
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

    /** Whether {@code slot} is one the viewer may place into or take from, a real offer slot, not a control. */
    boolean isEditable(int slot) {
        return editableSlots.contains(slot);
    }

    int confirmSlot() {
        return CONFIRM_SLOT;
    }

    /** Whether the money button is shown, true only when at least one currency may be staked (economy wired). */
    boolean moneyEnabled() {
        return !moneyCurrencies.isEmpty();
    }

    /** Whether {@code rawSlot} is the viewer's money button. */
    boolean isMoneySlot(int rawSlot) {
        return moneyEnabled() && rawSlot == MONEY_SLOT;
    }

    /** Whether {@code rawSlot} is the viewer's experience button. */
    boolean isExperienceSlot(int rawSlot) {
        return experienceEnabled && rawSlot == EXPERIENCE_SLOT;
    }

    /** How many currencies the money button cycles through, {@code 0} when money is off. */
    int currencyCount() {
        return moneyCurrencies.size();
    }

    /** The currency id the money button shows at {@code index}, wrapped into range. */
    String currencyAt(int index) {
        return moneyCurrencies.get(Math.floorMod(index, moneyCurrencies.size()));
    }

    /** Paint the divider and control frame; the editable, mirror, control, money, and experience slots are the view's. */
    void seedFrame(Inventory inv) {
        Objects.requireNonNull(inv, "inv");
        ItemStack pane = filler();
        for (int slot = 0; slot < SIZE; slot++) {
            if (!editableSlots.contains(slot) && !mirrorSlots.contains(slot) && !isControlSlot(slot)) {
                inv.setItem(slot, pane.clone());
            }
        }
    }

    /** Whether {@code slot} carries a control the view paints, so {@link #seedFrame} leaves it alone. */
    private boolean isControlSlot(int slot) {
        if (slot == CONFIRM_SLOT || slot == PARTNER_STATUS_SLOT) {
            return true;
        }
        if (moneyEnabled() && (slot == MONEY_SLOT || slot == PARTNER_MONEY_SLOT)) {
            return true;
        }
        return experienceEnabled && (slot == EXPERIENCE_SLOT || slot == PARTNER_EXPERIENCE_SLOT);
    }

    /** Paint the two confirm items, the viewer's confirm button and the partner's status, for this render. */
    void renderControls(
            Inventory inv, Messages messages, PlayerRef viewer, boolean selfConfirmed, boolean partnerConfirmed) {
        inv.setItem(CONFIRM_SLOT, confirmButton(messages, viewer, selfConfirmed));
        inv.setItem(PARTNER_STATUS_SLOT, partnerStatus(messages, viewer, partnerConfirmed));
    }

    /**
     * Paint the money button, the viewer's single "add money" button showing the selected currency and its staked
     * amount, and the read-only display of the other side's staked money. A no-op when money is off (no economy wired),
     * so those slots stay the divider filler {@link #seedFrame} laid down.
     */
    void renderMoney(
            Inventory inv,
            Messages messages,
            PlayerRef viewer,
            int selectedCurrency,
            Map<String, BigDecimal> ownMoney,
            Map<String, BigDecimal> partnerMoney) {
        if (!moneyEnabled()) {
            return;
        }
        String currency = currencyAt(selectedCurrency);
        BigDecimal amount = ownMoney.getOrDefault(currency, BigDecimal.ZERO);
        inv.setItem(MONEY_SLOT, moneyButton(messages, viewer, currency, amount));
        inv.setItem(PARTNER_MONEY_SLOT, partnerMoneyDisplay(messages, viewer, partnerMoney));
    }

    /**
     * Paint the experience button, the viewer's "add experience" button showing their staked points, and the read-only
     * display of the other side's staked experience. A no-op when experience is off (the items-only cross-server
     * window), so those slots stay the divider filler.
     */
    void renderExperience(
            Inventory inv, Messages messages, PlayerRef viewer, long ownExperience, long partnerExperience) {
        if (!experienceEnabled) {
            return;
        }
        inv.setItem(EXPERIENCE_SLOT, experienceButton(messages, viewer, ownExperience));
        inv.setItem(PARTNER_EXPERIENCE_SLOT, partnerExperienceDisplay(messages, viewer, partnerExperience));
    }

    private ItemStack moneyButton(Messages messages, PlayerRef viewer, String currency, BigDecimal amount) {
        Component name = StyledText.render(
                messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_MONEY, Map.of("currency", currency)));
        List<Component> lore = new ArrayList<>();
        lore.add(moneyAmountLine(messages, viewer, currency, amount));
        if (moneyCurrencies.size() > 1) {
            lore.add(StyledText.render(messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_MONEY_CYCLE, Map.of())));
        }
        return ItemBuilder.of(Material.GOLD_INGOT).name(name).lore(lore).build();
    }

    private ItemStack partnerMoneyDisplay(Messages messages, PlayerRef viewer, Map<String, BigDecimal> partnerMoney) {
        Component name =
                StyledText.render(messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_PARTNER_MONEY, Map.of()));
        List<Component> lore = new ArrayList<>();
        for (String currency : moneyCurrencies) {
            lore.add(moneyAmountLine(messages, viewer, currency, partnerMoney.getOrDefault(currency, BigDecimal.ZERO)));
        }
        return ItemBuilder.of(Material.SUNFLOWER).name(name).lore(lore).build();
    }

    private Component moneyAmountLine(Messages messages, PlayerRef viewer, String currency, BigDecimal amount) {
        return StyledText.render(messages.resolve(
                viewer,
                TradeMessageKey.TRADE_WINDOW_MONEY_AMOUNT,
                Map.of("currency", currency, "amount", amount.toPlainString())));
    }

    private ItemStack experienceButton(Messages messages, PlayerRef viewer, long amount) {
        Component name = StyledText.render(messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_EXPERIENCE, Map.of()));
        return ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(name)
                .lore(experienceAmountLine(messages, viewer, amount))
                .build();
    }

    private ItemStack partnerExperienceDisplay(Messages messages, PlayerRef viewer, long amount) {
        Component name =
                StyledText.render(messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_PARTNER_EXPERIENCE, Map.of()));
        return ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(name)
                .lore(experienceAmountLine(messages, viewer, amount))
                .build();
    }

    private Component experienceAmountLine(Messages messages, PlayerRef viewer, long amount) {
        return StyledText.render(messages.resolve(
                viewer, TradeMessageKey.TRADE_WINDOW_EXPERIENCE_AMOUNT, Map.of("amount", Long.toString(amount))));
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

    /** Empty a view's editable region, the offered originals leave the window on settle so nothing is returned twice. */
    void clearOffer(Inventory inv) {
        for (int slot : editableSlots) {
            inv.setItem(slot, null);
        }
    }
}
