package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.adapter.inbound.listener.ExchangeChatPromptListener;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.ExchangeOutcome;
import com.uxplima.uxmessentials.economy.application.ExchangeService;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import org.jspecify.annotations.NullMarked;

/**
 * Interactive 3-row Chest GUI serving as the currency swapper exchange dashboard.
 */
@NullMarked
public final class ExchangeGuiView {

    private final EconomyProvider economyProvider;
    private final ExchangeService exchangeService;
    private final Scheduler scheduler;
    private final EconomyNotifier notifier;
    private final Messages messages;
    private final ExchangeChatPromptListener chatPromptListener;
    private final FixedMenuLayout layout;
    private final ExchangeIcons icons;

    public ExchangeGuiView(
            Plugin plugin,
            EconomyProvider economyProvider,
            ExchangeService exchangeService,
            Scheduler scheduler,
            EconomyNotifier notifier,
            Messages messages,
            ExchangeChatPromptListener chatPromptListener,
            FixedMenuLayout layout) {
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.chatPromptListener = Objects.requireNonNull(chatPromptListener, "chatPromptListener");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.icons = new ExchangeIcons(messages, notifier, layout);
    }

    /**
     * The shipped geometry: source/target currency pickers, the info-convert button, and close, externalised
     * to exchange.conf. The source/target icons use the per-currency icon-material, so only their slots are
     * configured here, never their materials.
     */
    public static FixedMenuLayout defaultLayout() {
        return FixedMenuLayout.builder(3, Material.GRAY_STAINED_GLASS_PANE)
                .slotOnly("source", 11)
                .element("info", 13, Material.SUNFLOWER)
                .slotOnly("target", 15)
                .element("no-rate", 13, Material.BARRIER)
                .element("close", 22, Material.BARRIER)
                .build();
    }

    /** Opens the exchange GUI for the player using the default source and target currencies. */
    public void open(Player viewer) {
        List<Currency> currencies = new ArrayList<>(economyProvider.currencies());
        if (currencies.isEmpty()) {
            return;
        }
        Currency source = currencies.get(0);
        Currency target = currencies.size() > 1 ? currencies.get(1) : source;
        open(viewer, source, target);
    }

    /** Opens the exchange GUI for the player with specified source and target currencies. */
    public void open(Player viewer, Currency source, Currency target) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());

        scheduler.async(() -> {
            Money sourceBalance = economyProvider.balance(viewerRef, source);
            Money targetBalance = economyProvider.balance(viewerRef, target);

            scheduler.onEntity(viewerRef, () -> {
                Component titleText =
                        StyledText.render(messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_GUI_TITLE, Map.of()));
                SimpleGui gui = Guis.gui().title(titleText).rows(layout.rows()).build();

                fillBackground(gui);
                placeCurrency(gui, viewer, viewerRef, source, target, sourceBalance, true);
                placeCurrency(gui, viewer, viewerRef, source, target, targetBalance, false);
                placeInfo(gui, viewer, viewerRef, source, target);
                placeClose(gui, viewer, viewerRef);

                gui.open(viewer);
            });
        });
    }

    private void fillBackground(SimpleGui gui) {
        ItemStack filler = icons.filler();
        java.util.Set<Integer> occupied = new java.util.HashSet<>(layout.slots().values());
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            if (!occupied.contains(slot)) {
                gui.set(slot, GuiItem.display(filler));
            }
        }
    }

    private void placeCurrency(
            SimpleGui gui,
            Player viewer,
            PlayerRef viewerRef,
            Currency source,
            Currency target,
            Money balance,
            boolean isSource) {
        Currency shown = isSource ? source : target;
        ItemStack item = icons.currency(viewerRef, shown, balance, isSource);
        gui.set(
                layout.slot(isSource ? "source" : "target"),
                GuiItem.button(
                        item,
                        event -> scheduler.onEntity(viewerRef, () -> cycleCurrency(viewer, source, target, isSource))));
    }

    /** Advance the source or target to the next currency, skipping the other side so they never coincide. */
    private void cycleCurrency(Player viewer, Currency source, Currency target, boolean cyclingSource) {
        List<Currency> list = new ArrayList<>(economyProvider.currencies());
        if (list.size() < 2) {
            return;
        }
        Currency pinned = cyclingSource ? target : source;
        Currency current = cyclingSource ? source : target;
        int nextIdx = (list.indexOf(current) + 1) % list.size();
        Currency next = list.get(nextIdx);
        if (next.equals(pinned)) {
            next = list.get((nextIdx + 1) % list.size());
        }
        if (cyclingSource) {
            open(viewer, next, target);
        } else {
            open(viewer, source, next);
        }
    }

    private void placeInfo(SimpleGui gui, Player viewer, PlayerRef viewerRef, Currency source, Currency target) {
        Optional<ExchangeRate> rateOpt = exchangeService.registry().findRate(source.id(), target.id());
        ItemStack infoItem = rateOpt.isPresent()
                ? icons.rate(viewerRef, source, target, rateOpt.get())
                : icons.noRate(viewerRef, source, target);
        gui.set(
                layout.slot("info"),
                GuiItem.button(infoItem, event -> onInfoClick(gui, viewer, viewerRef, source, target, rateOpt, event)));
    }

    private void onInfoClick(
            SimpleGui gui,
            Player viewer,
            PlayerRef viewerRef,
            Currency source,
            Currency target,
            Optional<ExchangeRate> rateOpt,
            org.bukkit.event.inventory.InventoryClickEvent event) {
        if (rateOpt.isEmpty()) {
            return;
        }
        if (event.isRightClick() && event.isShiftClick()) {
            scheduler.onEntity(viewerRef, () -> {
                gui.close(viewer);
                promptCustomAmount(viewer, source, target);
            });
            return;
        }
        BigDecimal amountToConvert = quickAmount(event);
        if (amountToConvert.signum() <= 0) {
            return;
        }
        scheduler.async(() -> {
            ExchangeOutcome result = exchangeService.exchange(viewerRef, amountToConvert, source, target);
            scheduler.onEntity(viewerRef, () -> {
                handleExchangeResult(viewer, result, source, target);
                open(viewer, source, target);
            });
        });
    }

    /** The quick-convert amount a left/right/shift click maps to; zero for an unmapped combination. */
    private static BigDecimal quickAmount(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.isLeftClick() && !event.isShiftClick()) {
            return BigDecimal.valueOf(10);
        }
        if (event.isRightClick() && !event.isShiftClick()) {
            return BigDecimal.valueOf(100);
        }
        if (event.isLeftClick() && event.isShiftClick()) {
            return BigDecimal.valueOf(1000);
        }
        return BigDecimal.ZERO;
    }

    private void placeClose(SimpleGui gui, Player viewer, PlayerRef viewerRef) {
        gui.set(
                layout.slot("close"),
                GuiItem.button(
                        icons.close(viewerRef), event -> scheduler.onEntity(viewerRef, () -> gui.close(viewer))));
    }

    private void promptCustomAmount(Player player, Currency source, Currency target) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        Component promptMessage = StyledText.render(messages.resolve(
                viewerRef,
                EconomyMessageKey.EXCHANGE_PROMPT,
                Map.of(
                        "source", source.plural(),
                        "target", target.plural())));

        chatPromptListener.prompt(player, promptMessage, input -> {
            BigDecimal amount;
            try {
                amount = new BigDecimal(input);
            } catch (NumberFormatException e) {
                player.sendMessage(StyledText.render(
                        messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_INVALID_AMOUNT, Map.of())));
                return;
            }

            scheduler.async(() -> {
                ExchangeOutcome result = exchangeService.exchange(viewerRef, amount, source, target);
                scheduler.onEntity(viewerRef, () -> {
                    handleExchangeResult(player, result, source, target);
                });
            });
        });
    }

    private void handleExchangeResult(Player player, ExchangeOutcome result, Currency source, Currency target) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        switch (result.status()) {
            case SUCCESS ->
                sendPrefixed(
                        player,
                        viewerRef,
                        EconomyMessageKey.EXCHANGE_SUCCESS,
                        Map.of(
                                "source-amount", notifier.amount(Money.of(source, result.sourceAmount())),
                                "target-amount", notifier.amount(Money.of(target, result.targetAmount()))));
            case RATE_NOT_FOUND -> sendPrefixed(player, viewerRef, EconomyMessageKey.EXCHANGE_RATE_NOT_FOUND, Map.of());
            case INSUFFICIENT_FUNDS ->
                sendPrefixed(player, viewerRef, EconomyMessageKey.EXCHANGE_INSUFFICIENT_FUNDS, Map.of());
            case LIMIT_EXCEEDED -> sendPrefixed(player, viewerRef, EconomyMessageKey.EXCHANGE_LIMIT_EXCEEDED, Map.of());
            case PROVIDER_UNSUPPORTED ->
                sendPrefixed(player, viewerRef, EconomyMessageKey.EXCHANGE_PROVIDER_UNSUPPORTED, Map.of());
            case CURRENCY_DISABLED ->
                sendPrefixed(player, viewerRef, EconomyMessageKey.EXCHANGE_CURRENCY_DISABLED, Map.of());
            case FAILED -> {
                com.uxplima.uxmessentials.economy.domain.TransferError err = result.error();
                sendPrefixed(player, viewerRef, err != null ? err.messageKey() : EconomyMessageKey.PAY_ERROR, Map.of());
            }
        }
    }

    /** Send {@code key} resolved in the viewer's locale; the catalog line carries its own {@code <tag:'ECONOMY'>}. */
    private void sendPrefixed(
            Player player, PlayerRef viewerRef, EconomyMessageKey key, Map<String, String> placeholders) {
        player.sendMessage(StyledText.render(messages.resolve(viewerRef, key, placeholders)));
    }
}
