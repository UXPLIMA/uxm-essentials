package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EntityListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A reusable paginated currency picker, shared by the eco-admin manage and bulk screens. It grids one icon per
 * configured {@link Currency} — drawn from the operator-configured {@code icon-material} via {@link CurrencyIcons}
 * — across a 6-row chest, paging when a server runs more currencies than fit on a page. The currently-active
 * currency is highlighted with the same "Active" line the old inline selector used and an enchant glint, so the
 * choice stands out at a glance. Clicking a currency runs the supplied {@code Consumer<Currency>} callback, which
 * the caller wires to set the active currency and re-open its own screen.
 *
 * <p>The view holds no module logic beyond the icon: it is given the full currency list, the active one, and the
 * callback. It draws through the menu engine's paginated-list runtime ({@link Menus#openList}) over a
 * {@link EntityListSpec}, so the window is a holder-backed engine list routed and torn down by the one menu listener and
 * one {@code closeMenu}, with paging re-paginating the same holder. The currency list is already in hand (the
 * caller passes it), so the engine reads only that snapshot and shows the window on the viewer's entity thread.
 */
@NullMarked
public final class CurrencyPickerView {

    private static final int ROWS = 6;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final List<Integer> CONTENT_SLOTS = contentSlots();

    private final Menus menus;
    private final GuiText guiText;

    public CurrencyPickerView(Menus menus, GuiText guiText, Scheduler scheduler) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        // The engine hops onto the viewer's entity thread inside openList, so the scheduler is only validated here.
        Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Open the picker for {@code viewer}. {@code currencies} is the full configured list, {@code active} the one
     * highlighted, and {@code onPick} runs when a currency is clicked.
     */
    public void open(
            Player viewer, PlayerRef viewerRef, List<Currency> currencies, Currency active, Consumer<Currency> onPick) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(onPick, "onPick");
        List<Currency> snapshot = List.copyOf(currencies);
        menus.openList(viewerRef, spec(viewerRef, snapshot, active, onPick));
    }

    /**
     * Build the engine {@link EntityListSpec} for one viewer: the currencies as the listed entities, the per-currency
     * icon renderer (which glints the active one), and an {@code onSelect} that runs the caller's {@code onPick}.
     * The geometry — six rows, content slots 0..44, prev/next at 45/53, gray-glass filler — matches the old view
     * slot-for-slot, so the engine draws the same picker the {@code PaginatedGui} did.
     */
    private EntityListSpec spec(
            PlayerRef viewerRef, List<Currency> currencies, Currency active, Consumer<Currency> onPick) {
        return EntityListSpec.builder()
                .title(guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_PICKER_TITLE))
                .rows(ROWS)
                .contentSlots(CONTENT_SLOTS)
                .navigation(PREV_SLOT, NEXT_SLOT, Material.ARROW)
                .navNames(
                        guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_PICKER_PREVIOUS),
                        guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_PICKER_NEXT))
                .filler(FILLER)
                .entities(() -> List.<Object>copyOf(currencies))
                .iconRenderer((v, entity) -> currencyIcon(v, (Currency) entity, entity.equals(active)))
                .onSelect((player, entity) -> onPick.accept((Currency) entity))
                .build();
    }

    private ItemStack currencyIcon(PlayerRef viewer, Currency currency, boolean active) {
        Material material = CurrencyIcons.materialFor(currency, Material.SUNFLOWER);
        Map<String, String> placeholders = Map.of("currency", currency.plural());
        List<Component> lore = new ArrayList<>();
        lore.add(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_LORE, placeholders));
        if (active) {
            lore.add(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_ACTIVE_LORE));
        }
        ItemBuilder builder = ItemBuilder.of(material)
                .name(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_NAME, placeholders))
                .lore(lore);
        if (active) {
            // A glint marks the active currency; HIDE_ENCHANTS keeps the enchant out of the lore.
            builder.enchant(Enchantment.UNBREAKING, 1).flags(ItemFlag.HIDE_ENCHANTS);
        }
        return builder.build();
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }
}
