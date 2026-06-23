package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A reusable paginated currency picker, shared by the eco-admin manage and bulk screens. It grids one icon per
 * configured {@link Currency} — drawn from the operator-configured {@code icon-material} via {@link CurrencyIcons}
 * — across a 6-row chest, paging when a server runs more currencies than fit on a page. The currently-active
 * currency is highlighted with the same "Active" line the old inline selector used. Clicking a currency runs the
 * supplied {@code Consumer<Currency>} callback, which the caller wires to set the active currency and re-open its
 * own screen.
 *
 * <p>The view holds no module logic beyond the icon: it is given the full currency list, the active one, and the
 * callback. The menu is a uxmLib {@link PaginatedGui}, so paging routes through the installed menu listener with
 * no bespoke listener. {@link #open} schedules the build on the viewer's entity thread.
 */
@NullMarked
public final class CurrencyPickerView {

    private static final int ROWS = 6;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final List<Integer> CONTENT_SLOTS = contentSlots();
    private static final List<Integer> NAV_FILLER_SLOTS = List.of(46, 47, 48, 49, 50, 51, 52);

    private final GuiText guiText;
    private final Scheduler scheduler;

    public CurrencyPickerView(GuiText guiText, Scheduler scheduler) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
        scheduler.onEntity(viewerRef, () -> buildAndOpen(viewer, viewerRef, currencies, active, onPick));
    }

    private void buildAndOpen(
            Player viewer, PlayerRef viewerRef, List<Currency> currencies, Currency active, Consumer<Currency> onPick) {
        PaginatedGui gui = Guis.paginated()
                .title(guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_PICKER_TITLE))
                .rows(ROWS)
                .contentSlots(CONTENT_SLOTS)
                .build();
        for (Currency currency : List.copyOf(currencies)) {
            ItemStack icon = currencyIcon(viewerRef, currency, currency.equals(active));
            gui.addPageItem(GuiItem.button(icon, e -> onPick.accept(currency)));
        }
        navigation(gui, viewerRef);
        gui.open(viewer);
    }

    private void navigation(PaginatedGui gui, PlayerRef viewerRef) {
        ItemStack prev = ItemBuilder.of(Material.ARROW)
                .name(guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_PICKER_PREVIOUS))
                .build();
        ItemStack next = ItemBuilder.of(Material.ARROW)
                .name(guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_PICKER_NEXT))
                .build();
        gui.set(PREV_SLOT, GuiItem.previousPage(gui, prev));
        gui.set(NEXT_SLOT, GuiItem.nextPage(gui, next));
        ItemStack filler = ItemBuilder.of(FILLER).name(Component.empty()).build();
        for (int slot : NAV_FILLER_SLOTS) {
            gui.set(slot, GuiItem.display(filler));
        }
    }

    private ItemStack currencyIcon(PlayerRef viewer, Currency currency, boolean active) {
        Material material = CurrencyIcons.materialFor(currency, Material.SUNFLOWER);
        Map<String, String> placeholders = Map.of("currency", currency.plural());
        List<Component> lore = new ArrayList<>();
        lore.add(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_LORE, placeholders));
        if (active) {
            lore.add(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_ACTIVE_LORE));
        }
        return ItemBuilder.of(material)
                .name(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_NAME, placeholders))
                .lore(lore)
                .build();
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }
}
