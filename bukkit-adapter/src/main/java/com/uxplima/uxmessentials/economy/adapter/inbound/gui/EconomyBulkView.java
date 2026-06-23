package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmlib.gui.ConfirmMenu;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The server-wide eco-admin screen reached from the hub's [Server-wide] button: give-all (an amount captured
 * through the shared input seam, credited to every online wallet) and reset-all (confirm-gated, zeroing every
 * online balance). Both
 * operate on the currently-online roster — the same scope the {@code /eco giveall|resetall} commands use until a
 * repository {@code allOwners()} read-model lands (see {@code EcoTargets}). The roster is enumerated on the
 * global region thread (the one thread {@code Server.getOnlinePlayers()} is safely readable on Folia), snapshotted
 * to {@link PlayerRef}s, and the bulk op runs off the tick thread.
 *
 * <p>When more than one currency is configured a single [Currency] item between give-all and reset-all opens the
 * shared paginated {@link CurrencyPickerView}; choosing a currency re-opens this screen with it active.
 */
@NullMarked
public final class EconomyBulkView {

    private static final int ROWS = 3;
    private static final int GIVEALL_SLOT = 11;
    private static final int SELECT_CURRENCY_SLOT = 13;
    private static final int RESETALL_SLOT = 15;
    private static final int BACK_SLOT = 22;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final Server server;
    private final EcoAdminOps ops;
    private final CurrencyRegistry currencies;
    private final EconomyNotifier notifier;
    private final CurrencyPickerView currencyPicker;
    private final java.util.function.BiConsumer<Player, PlayerRef> onBack;

    public EconomyBulkView(
            GuiText guiText,
            Scheduler scheduler,
            TextInput textInput,
            Server server,
            EcoAdminOps ops,
            CurrencyRegistry currencies,
            EconomyNotifier notifier,
            CurrencyPickerView currencyPicker,
            java.util.function.BiConsumer<Player, PlayerRef> onBack) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.server = Objects.requireNonNull(server, "server");
        this.ops = Objects.requireNonNull(ops, "ops");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.currencyPicker = Objects.requireNonNull(currencyPicker, "currencyPicker");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /** Open the server-wide screen for {@code viewer}, with the default currency active. */
    public void open(Player viewer, PlayerRef viewerRef) {
        open(viewer, viewerRef, currencies.defaultCurrency());
    }

    /** Open the server-wide screen for {@code viewer} with {@code active} as the currency the actions apply to. */
    public void open(Player viewer, PlayerRef viewerRef, Currency active) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(active, "active");
        scheduler.onEntity(viewerRef, () -> buildAndOpen(viewer, viewerRef, active));
    }

    private void buildAndOpen(Player viewer, PlayerRef viewerRef, Currency active) {
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_BULK_TITLE))
                .rows(ROWS)
                .build();
        fill(gui);
        Map<String, String> currencyName = Map.of("currency", active.plural());
        gui.set(
                GIVEALL_SLOT,
                action(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_GIVEALL_NAME,
                        EconomyMessageKey.ECO_ADMIN_GUI_GIVEALL_LORE,
                        currencyName,
                        Material.EMERALD_BLOCK,
                        () -> promptGiveAll(viewer, viewerRef, active)));
        selectCurrency(gui, viewer, viewerRef, active);
        gui.set(
                RESETALL_SLOT,
                action(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_RESETALL_NAME,
                        EconomyMessageKey.ECO_ADMIN_GUI_RESETALL_LORE,
                        currencyName,
                        Material.TNT,
                        () -> confirmResetAll(viewer, viewerRef, active)));
        gui.set(BACK_SLOT, GuiItem.button(backIcon(viewerRef), e -> onBack.accept(viewer, viewerRef)));
        gui.open(viewer);
    }

    /**
     * Render the single [Currency] item only when more than one currency is configured; clicking it opens the
     * shared paginated picker, and choosing a currency re-opens this screen with that currency active.
     */
    private void selectCurrency(SimpleGui gui, Player viewer, PlayerRef viewerRef, Currency active) {
        List<Currency> all = List.copyOf(currencies.all());
        if (all.size() <= 1) {
            return;
        }
        gui.set(
                SELECT_CURRENCY_SLOT,
                GuiItem.button(
                        selectCurrencyIcon(viewerRef, active),
                        e -> currencyPicker.open(
                                viewer, viewerRef, all, active, chosen -> open(viewer, viewerRef, chosen))));
    }

    private ItemStack selectCurrencyIcon(PlayerRef viewer, Currency active) {
        Map<String, String> placeholders = Map.of("currency", active.plural());
        return ItemBuilder.of(Material.SUNFLOWER)
                .name(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_SELECT_CURRENCY_NAME))
                .lore(List.of(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_SELECT_CURRENCY_LORE, placeholders)))
                .build();
    }

    private void promptGiveAll(Player viewer, PlayerRef viewerRef, Currency active) {
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of("eco.bulk-amount", EconomyMessageKey.ECO_ADMIN_GUI_AMOUNT_PROMPT),
                text -> applyGiveAll(viewer, viewerRef, active, text),
                () -> open(viewer, viewerRef, active));
    }

    /**
     * Parse the typed amount, and on success snapshot the online roster on the global thread and credit it off the
     * tick thread. A malformed amount sends the existing parse-error rejection and re-opens — no op runs.
     * Package-private so the amount branch is unit-tested without driving a live anvil, mirroring
     * {@code PlayerPickerView.resolveTyped}.
     */
    void applyGiveAll(Player viewer, PlayerRef viewerRef, Currency active, String raw) {
        Result<Money, AmountParseError> parsed = AmountParser.parse(raw, active);
        if (parsed.isErr()) {
            notifier.send(viewerRef, parsed.errorOrThrow().messageKey());
            open(viewer, viewerRef, active);
            return;
        }
        Money money = parsed.orElseThrow();
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster = roster();
            scheduler.async(() -> ops.giveAll(viewerRef, roster, money));
        });
        open(viewer, viewerRef, active);
    }

    private void confirmResetAll(Player viewer, PlayerRef viewerRef, Currency active) {
        Component title = guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_RESETALL_CONFIRM_TITLE);
        scheduler.onEntity(
                viewerRef,
                () -> ConfirmMenu.of(
                                title, () -> resetAll(viewer, viewerRef, active), () -> open(viewer, viewerRef, active))
                        .open(viewer));
    }

    private void resetAll(Player viewer, PlayerRef viewerRef, Currency active) {
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster = roster();
            scheduler.async(() -> ops.resetAll(viewerRef, roster, active));
        });
        open(viewer, viewerRef, active);
    }

    private List<PlayerRef> roster() {
        List<PlayerRef> refs = new ArrayList<>();
        for (Player online : server.getOnlinePlayers()) {
            refs.add(BukkitRefs.toRef(online));
        }
        return List.copyOf(refs);
    }

    private GuiItem action(
            PlayerRef viewer,
            MessageKey nameKey,
            MessageKey loreKey,
            Map<String, String> placeholders,
            Material icon,
            Runnable onClick) {
        ItemStack item = ItemBuilder.of(icon)
                .name(guiText.text(viewer, nameKey, placeholders))
                .lore(List.of(guiText.text(viewer, loreKey, placeholders)))
                .build();
        return GuiItem.button(item, e -> onClick.run());
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.ARROW)
                .name(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_BACK))
                .build();
    }

    private void fill(SimpleGui gui) {
        ItemStack filler = ItemBuilder.of(FILLER).name(Component.empty()).build();
        for (int slot = 0; slot < ROWS * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }
}
