package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmlib.gui.ConfirmMenu;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player eco-admin screen reached after a target is picked from the hub: the target's head with their
 * current balance per currency, the Give / Take / Set / Reset action buttons, a [History] link into the
 * transaction-history GUI, and — when more than one currency is configured — a single [Currency] item that
 * opens a paginated {@link CurrencyPickerView} to switch the active currency the four actions apply to. Give /
 * Take / Set capture an amount through the shared input seam and run the matching {@code EcoAdmin} op with
 * {@code Money.of(activeCurrency, amount)}; Reset is confirm-gated (it zeroes a balance) before calling
 * {@code reset(actor, target, activeCurrency)}.
 *
 * <p>The balance read uses the resolved {@link EconomyProvider} directly (synchronous, no chat side effect),
 * hopped off the tick thread before the menu is built so a foreign provider never blocks the viewer's region
 * thread. The op itself is dispatched off-tick exactly as the {@code /eco} command does, since {@code EcoAdmin}
 * assumes it runs off the tick thread.
 */
@NullMarked
public final class EconomyTargetView {

    private static final int ROWS = 5;
    private static final int HEAD_SLOT = 4;
    private static final int GIVE_SLOT = 19;
    private static final int TAKE_SLOT = 21;
    private static final int SET_SLOT = 23;
    private static final int RESET_SLOT = 25;
    private static final int HISTORY_SLOT = 31;
    private static final int BACK_SLOT = 40;
    private static final int SELECT_CURRENCY_SLOT = 38;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final EconomyProvider economy;
    private final EcoAdminOps ops;
    private final CurrencyRegistry currencies;
    private final EconomyNotifier notifier;
    private final TransactionsHistoryMenu historyView;
    private final CurrencyPickerView currencyPicker;
    private final java.util.function.BiConsumer<Player, PlayerRef> onBack;

    public EconomyTargetView(
            GuiText guiText,
            Scheduler scheduler,
            TextInput textInput,
            EconomyProvider economy,
            EcoAdminOps ops,
            CurrencyRegistry currencies,
            EconomyNotifier notifier,
            TransactionsHistoryMenu historyView,
            CurrencyPickerView currencyPicker,
            java.util.function.BiConsumer<Player, PlayerRef> onBack) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.ops = Objects.requireNonNull(ops, "ops");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
        this.currencyPicker = Objects.requireNonNull(currencyPicker, "currencyPicker");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /** Open the manage screen for {@code target} with the default currency active. */
    public void open(Player viewer, PlayerRef viewerRef, PlayerRef target) {
        open(viewer, viewerRef, target, currencies.defaultCurrency());
    }

    /** Open the manage screen for {@code target} with {@code active} as the currency the actions apply to. */
    public void open(Player viewer, PlayerRef viewerRef, PlayerRef target, Currency active) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(active, "active");
        scheduler.async(() -> {
            List<Money> balances = readBalances(target);
            scheduler.onEntity(viewerRef, () -> buildAndOpen(viewer, viewerRef, target, active, balances));
        });
    }

    private List<Money> readBalances(PlayerRef target) {
        List<Money> balances = new ArrayList<>();
        for (Currency currency : currencies.all()) {
            balances.add(economy.balance(target, currency));
        }
        return balances;
    }

    private void buildAndOpen(
            Player viewer, PlayerRef viewerRef, PlayerRef target, Currency active, List<Money> balances) {
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_TARGET_TITLE, player(target)))
                .rows(ROWS)
                .build();
        fill(gui);
        gui.set(HEAD_SLOT, GuiItem.display(head(viewerRef, target, balances)));
        Map<String, String> currencyName = Map.of("currency", active.plural());
        gui.set(
                GIVE_SLOT,
                action(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_GIVE_NAME,
                        currencyName,
                        Material.EMERALD,
                        () -> promptAmount(viewer, viewerRef, target, active, EcoAdminOps.Verb.GIVE)));
        gui.set(
                TAKE_SLOT,
                action(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_TAKE_NAME,
                        currencyName,
                        Material.REDSTONE,
                        () -> promptAmount(viewer, viewerRef, target, active, EcoAdminOps.Verb.TAKE)));
        gui.set(
                SET_SLOT,
                action(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_SET_NAME,
                        currencyName,
                        Material.COMPARATOR,
                        () -> promptAmount(viewer, viewerRef, target, active, EcoAdminOps.Verb.SET)));
        gui.set(
                RESET_SLOT,
                action(
                        viewerRef,
                        EconomyMessageKey.ECO_ADMIN_GUI_RESET_NAME,
                        currencyName,
                        Material.TNT,
                        () -> confirmReset(viewer, viewerRef, target, active)));
        gui.set(HISTORY_SLOT, GuiItem.button(historyIcon(viewerRef), e -> openHistory(viewerRef, target)));
        selectCurrency(gui, viewer, viewerRef, target, active);
        gui.set(BACK_SLOT, GuiItem.button(backIcon(viewerRef), e -> onBack.accept(viewer, viewerRef)));
        gui.open(viewer);
    }

    /**
     * Render the single [Currency] item only when more than one currency is configured; clicking it opens the
     * paginated picker, and choosing a currency re-opens this screen with that currency active.
     */
    private void selectCurrency(SimpleGui gui, Player viewer, PlayerRef viewerRef, PlayerRef target, Currency active) {
        List<Currency> all = List.copyOf(currencies.all());
        if (all.size() <= 1) {
            return;
        }
        gui.set(
                SELECT_CURRENCY_SLOT,
                GuiItem.button(
                        selectCurrencyIcon(viewerRef, active),
                        e -> currencyPicker.open(
                                viewer, viewerRef, all, active, chosen -> open(viewer, viewerRef, target, chosen))));
    }

    private void promptAmount(
            Player viewer, PlayerRef viewerRef, PlayerRef target, Currency active, EcoAdminOps.Verb verb) {
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of("eco.amount", EconomyMessageKey.ECO_ADMIN_GUI_AMOUNT_PROMPT),
                text -> applyAmount(viewer, viewerRef, target, active, verb, text),
                () -> open(viewer, viewerRef, target, active));
    }

    /**
     * Parse the typed amount against the active currency and, when valid, dispatch the op off the tick thread.
     * A malformed amount sends the existing parse-error rejection and re-opens the screen — no op runs.
     * Package-private so the amount branch is unit-tested without driving a live anvil (the sync test scheduler
     * runs the callback inline), mirroring {@code PlayerPickerView.resolveTyped}.
     */
    void applyAmount(
            Player viewer, PlayerRef viewerRef, PlayerRef target, Currency active, EcoAdminOps.Verb verb, String raw) {
        Result<Money, AmountParseError> parsed = AmountParser.parse(raw, active);
        if (parsed.isErr()) {
            notifier.send(viewerRef, parsed.errorOrThrow().messageKey());
            open(viewer, viewerRef, target, active);
            return;
        }
        Money money = parsed.orElseThrow();
        scheduler.async(() -> ops.dispatch(verb, viewerRef, target, money));
        open(viewer, viewerRef, target, active);
    }

    private void confirmReset(Player viewer, PlayerRef viewerRef, PlayerRef target, Currency active) {
        Component title = guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_RESET_CONFIRM_TITLE, player(target));
        scheduler.onEntity(
                viewerRef,
                () -> ConfirmMenu.of(
                                title,
                                () -> {
                                    scheduler.async(() -> ops.reset(viewerRef, target, active));
                                    open(viewer, viewerRef, target, active);
                                },
                                () -> open(viewer, viewerRef, target, active))
                        .open(viewer));
    }

    private void openHistory(PlayerRef viewerRef, PlayerRef target) {
        scheduler.onEntity(viewerRef, () -> historyView.open(viewerRef, target.uuid(), target.name()));
    }

    private GuiItem action(
            PlayerRef viewer,
            EconomyMessageKey nameKey,
            Map<String, String> placeholders,
            Material icon,
            Runnable onClick) {
        MessageKey loreKey = loreFor(nameKey);
        ItemStack item = ItemBuilder.of(icon)
                .name(guiText.text(viewer, nameKey, placeholders))
                .lore(List.of(guiText.text(viewer, loreKey, placeholders)))
                .build();
        return GuiItem.button(item, e -> onClick.run());
    }

    private static MessageKey loreFor(EconomyMessageKey nameKey) {
        return switch (nameKey) {
            case ECO_ADMIN_GUI_GIVE_NAME -> EconomyMessageKey.ECO_ADMIN_GUI_GIVE_LORE;
            case ECO_ADMIN_GUI_TAKE_NAME -> EconomyMessageKey.ECO_ADMIN_GUI_TAKE_LORE;
            case ECO_ADMIN_GUI_SET_NAME -> EconomyMessageKey.ECO_ADMIN_GUI_SET_LORE;
            case ECO_ADMIN_GUI_RESET_NAME -> EconomyMessageKey.ECO_ADMIN_GUI_RESET_LORE;
            default -> throw new IllegalArgumentException("no lore for " + nameKey);
        };
    }

    private ItemStack head(PlayerRef viewer, PlayerRef target, List<Money> balances) {
        List<Component> lore = new ArrayList<>();
        for (Money balance : balances) {
            lore.add(guiText.text(
                    viewer,
                    EconomyMessageKey.ECO_ADMIN_GUI_TARGET_HEAD_LORE,
                    Map.of("currency", balance.currency().plural(), "amount", notifier.amount(balance))));
        }
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_TARGET_HEAD_NAME, player(target)))
                .lore(lore)
                .skull(SkullData.ofUuid(target.uuid()))
                .build();
    }

    private ItemStack selectCurrencyIcon(PlayerRef viewer, Currency active) {
        Map<String, String> placeholders = Map.of("currency", active.plural());
        return ItemBuilder.of(Material.SUNFLOWER)
                .name(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_SELECT_CURRENCY_NAME))
                .lore(List.of(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_SELECT_CURRENCY_LORE, placeholders)))
                .build();
    }

    private ItemStack historyIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.BOOK)
                .name(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_TARGET_HISTORY_NAME))
                .lore(List.of(guiText.text(viewer, EconomyMessageKey.ECO_ADMIN_GUI_TARGET_HISTORY_LORE)))
                .build();
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

    private static Map<String, String> player(PlayerRef target) {
        return Map.of("player", target.name());
    }
}
