package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Assembles the eco-admin GUI stack the bare {@code /eco} opens: the {@link EconomyAdminView hub}, the
 * per-player {@link EconomyTargetMenu manage screen}, and the server-wide {@link EconomyBulkMenu bulk screen}.
 * The hub holds the other two so its [Manage a player] and [Server-wide] entries open them through the menu engine,
 * and each child carries a back-callback into the hub. One instance is built per module start by
 * {@code EconomyWiring}; the command's {@code guiRoot()} delegates here.
 *
 * <p>The hub remains a bespoke view that opens the two engine-rendered children — a transitional cross-runtime
 * seam: the hub's entry buttons call each child menu's {@code open}, which routes through the {@link Menus} façade.
 */
@NullMarked
public final class EconomyAdminGuiViews {

    private final EconomyAdminView hub;

    private EconomyAdminGuiViews(EconomyAdminView hub) {
        this.hub = Objects.requireNonNull(hub, "hub");
    }

    /** The hub the bare {@code /eco} opens. */
    public EconomyAdminView hub() {
        return hub;
    }

    /** Build the eco-admin GUI stack over the resolved provider, the kernel ports, and the shared picker/seam. */
    public static EconomyAdminGuiViews create(
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder,
            Logger log,
            GuiText guiText,
            Messages messages,
            Scheduler scheduler,
            Server server,
            PlayerPickerView picker,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput textInput,
            PlayerLookup players,
            EconomyProvider economy,
            EcoAdmin ecoAdmin,
            CurrencyRegistry currencies,
            EconomyNotifier notifier,
            TransactionsHistoryMenu historyView) {
        EcoAdminOps ops = new EcoAdminOps(ecoAdmin);
        CurrencyPickerView currencyPicker = new CurrencyPickerView(menus, guiText, scheduler);
        // Both child screens return to the hub; the hub is constructed last, so the back-callbacks read it
        // through a one-slot holder rather than a setter, keeping every cross-link constructor-injected.
        EconomyAdminView[] hubHolder = new EconomyAdminView[1];
        EconomyTargetMenu targetMenu = new EconomyTargetMenu(
                menus,
                guiText,
                messages,
                scheduler,
                textInput,
                economy,
                ops,
                currencies,
                notifier,
                historyView,
                currencyPicker,
                (player, viewer) -> hubHolder[0].open(player, viewer));
        targetMenu.register(menuBindings, dataFolder, log);
        EconomyBulkMenu bulkMenu = new EconomyBulkMenu(
                menus,
                guiText,
                scheduler,
                textInput,
                server,
                ops,
                currencies,
                notifier,
                currencyPicker,
                (player, viewer) -> hubHolder[0].open(player, viewer));
        bulkMenu.register(menuBindings, dataFolder, log);
        EconomyAdminView hub =
                new EconomyAdminView(guiText, scheduler, picker, players, targetMenu, bulkMenu, historyView);
        hubHolder[0] = hub;
        return new EconomyAdminGuiViews(hub);
    }
}
