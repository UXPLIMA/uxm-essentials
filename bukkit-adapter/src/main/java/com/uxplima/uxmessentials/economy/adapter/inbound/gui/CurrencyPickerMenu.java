package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the shared currency picker with the menu engine and opens it. The eco-admin manage and bulk screens and
 * the loan request flow all open this one menu: an icon per configured {@link Currency}, the active one glinting,
 * and a click that hands the chosen currency back to the caller.
 *
 * <p>The icons are the {@code economy:currency-picker} list source, snapshotted at open and handed to the engine as
 * the menu subject together with the active currency and the caller's callback. The per-currency icon material,
 * name and lore reach the spec through the {@code currency_picker_*} placeholders because they are authored per
 * currency; everything else lives in {@code modules/economy/gui/currency-picker.conf}.
 */
@NullMarked
public final class CurrencyPickerMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "currency-picker";

    private static final String SPEC_RESOURCE = "modules/economy/gui/currency-picker.conf";

    private final Menus menus;
    private final Messages messages;
    private final Scheduler scheduler;

    public CurrencyPickerMenu(Menus menus, Messages messages, Scheduler scheduler) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Register the bindings the spec names and the spec itself; called once at economy wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(
                "economy:currency-picker", ctx -> ctx.subject(Selection.class).currencies());
        bindings.placeholder(
                "currency_picker_icon",
                ctx -> CurrencyIcons.materialFor(currencyOf(ctx), Material.SUNFLOWER)
                        .name());
        bindings.placeholder(
                "currency_picker_name",
                ctx -> messages.resolve(
                        ctx.viewer(),
                        EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_NAME,
                        Map.of("currency", currencyOf(ctx).plural())));
        bindings.placeholder("currency_picker_lore", this::lore);
        bindings.placeholder("currency_picker_active", ctx -> Boolean.toString(isActive(ctx)));
        bindings.action("economy:currency-pick", this::pickClicked);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the picker for {@code viewer}. {@code currencies} is the full configured list, {@code active} the one
     * that glints, and {@code onPick} runs when a currency is clicked.
     */
    public void open(
            Player viewer, PlayerRef viewerRef, List<Currency> currencies, Currency active, Consumer<Currency> onPick) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(onPick, "onPick");
        scheduler.onEntity(viewerRef, () -> menus.open(viewerRef, SPEC_ID, new Selection(currencies, active, onPick)));
    }

    /** The bound currency's lore: its own line, plus the active line when this is the currency in use. */
    private String lore(MenuContext ctx) {
        Currency currency = currencyOf(ctx);
        List<String> lines = new ArrayList<>();
        lines.add(messages.resolve(
                ctx.viewer(), EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_LORE, Map.of("currency", currency.plural())));
        if (isActive(ctx)) {
            lines.add(messages.resolve(ctx.viewer(), EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_ACTIVE_LORE, Map.of()));
        }
        return String.join("\n", lines);
    }

    /** Left-click a currency: hand it to the screen that opened the picker, which reopens itself. */
    private void pickClicked(MenuActionContext ctx) {
        ctx.subject(Selection.class).onPick().accept(ctx.entry(Currency.class));
    }

    private static boolean isActive(MenuContext ctx) {
        return currencyOf(ctx).equals(ctx.subject(Selection.class).active());
    }

    private static Currency currencyOf(MenuContext ctx) {
        return ctx.entry(Currency.class);
    }

    /**
     * The subject of an open picker: the currencies to choose from, the one currently in use, and what a click does.
     *
     * @param currencies the configured currencies, in registry order
     * @param active the currency the opening screen is working in, drawn with a glint
     * @param onPick invoked with the clicked currency
     */
    public record Selection(List<Currency> currencies, Currency active, Consumer<Currency> onPick) {

        public Selection {
            currencies = List.copyOf(Objects.requireNonNull(currencies, "currencies"));
            Objects.requireNonNull(active, "active");
            Objects.requireNonNull(onPick, "onPick");
        }
    }
}
