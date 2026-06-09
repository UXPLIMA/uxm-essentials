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
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.ExchangeChatPromptListener;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.ExchangeOutcome;
import com.uxplima.uxmessentials.economy.application.ExchangeService;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
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
    private final MiniMessage miniMessage;

    public ExchangeGuiView(
            Plugin plugin,
            EconomyProvider economyProvider,
            ExchangeService exchangeService,
            Scheduler scheduler,
            EconomyNotifier notifier,
            Messages messages,
            ExchangeChatPromptListener chatPromptListener) {
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.chatPromptListener = Objects.requireNonNull(chatPromptListener, "chatPromptListener");
        this.miniMessage = MiniMessage.miniMessage();
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
                Component titleText = text(viewerRef, EconomyMessageKey.EXCHANGE_GUI_TITLE, Map.of());

                SimpleGui gui = Guis.gui().title(titleText).rows(3).build();

                // 1. Source Currency Item (Slot 11)
                ItemStack sourceItem = ItemBuilder.of(currencyMaterial(source))
                        .name(text(
                                viewerRef,
                                EconomyMessageKey.EXCHANGE_GUI_SOURCE_NAME,
                                Map.of("currency", source.plural())))
                        .lore(textLoreLines(
                                viewerRef,
                                EconomyMessageKey.EXCHANGE_GUI_SOURCE_LORE,
                                Map.of("balance", notifier.amount(sourceBalance))))
                        .build();

                gui.set(11, GuiItem.button(sourceItem, event -> {
                    scheduler.onEntity(viewerRef, () -> {
                        List<Currency> list = new ArrayList<>(economyProvider.currencies());
                        if (list.size() < 2) return;
                        int idx = list.indexOf(source);
                        int nextIdx = (idx + 1) % list.size();
                        Currency nextSource = list.get(nextIdx);
                        if (nextSource.equals(target)) {
                            nextIdx = (nextIdx + 1) % list.size();
                            nextSource = list.get(nextIdx);
                        }
                        open(viewer, nextSource, target);
                    });
                }));

                // 2. Target Currency Item (Slot 15)
                ItemStack targetItem = ItemBuilder.of(currencyMaterial(target))
                        .name(text(
                                viewerRef,
                                EconomyMessageKey.EXCHANGE_GUI_TARGET_NAME,
                                Map.of("currency", target.plural())))
                        .lore(textLoreLines(
                                viewerRef,
                                EconomyMessageKey.EXCHANGE_GUI_TARGET_LORE,
                                Map.of("balance", notifier.amount(targetBalance))))
                        .build();

                gui.set(15, GuiItem.button(targetItem, event -> {
                    scheduler.onEntity(viewerRef, () -> {
                        List<Currency> list = new ArrayList<>(economyProvider.currencies());
                        if (list.size() < 2) return;
                        int idx = list.indexOf(target);
                        int nextIdx = (idx + 1) % list.size();
                        Currency nextTarget = list.get(nextIdx);
                        if (nextTarget.equals(source)) {
                            nextIdx = (nextIdx + 1) % list.size();
                            nextTarget = list.get(nextIdx);
                        }
                        open(viewer, source, nextTarget);
                    });
                }));

                // 3. Info/Convert Button (Slot 13)
                Optional<ExchangeRate> rateOpt = exchangeService.registry().findRate(source.id(), target.id());
                ItemStack infoItem;
                if (rateOpt.isPresent()) {
                    ExchangeRate rate = rateOpt.get();
                    String rateStr = rate.rate().toPlainString();
                    String feeStr = rate.feePercent()
                            .multiply(BigDecimal.valueOf(100))
                            .stripTrailingZeros()
                            .toPlainString();

                    infoItem = ItemBuilder.of(Material.SUNFLOWER)
                            .name(text(viewerRef, EconomyMessageKey.EXCHANGE_GUI_INFO_NAME, Map.of()))
                            .lore(textLoreLines(
                                    viewerRef,
                                    EconomyMessageKey.EXCHANGE_GUI_INFO_LORE,
                                    Map.of(
                                            "source",
                                            source.plural(),
                                            "target",
                                            target.plural(),
                                            "rate",
                                            rateStr,
                                            "fee",
                                            feeStr)))
                            .build();
                } else {
                    infoItem = ItemBuilder.of(Material.BARRIER)
                            .name(text(viewerRef, EconomyMessageKey.EXCHANGE_GUI_NO_RATE_NAME, Map.of()))
                            .lore(List.of(text(
                                    viewerRef,
                                    EconomyMessageKey.EXCHANGE_GUI_NO_RATE_LORE,
                                    Map.of("source", source.plural(), "target", target.plural()))))
                            .build();
                }

                gui.set(13, GuiItem.button(infoItem, event -> {
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

                    BigDecimal amountToConvert = BigDecimal.ZERO;
                    if (event.isLeftClick() && !event.isShiftClick()) {
                        amountToConvert = BigDecimal.valueOf(10);
                    } else if (event.isRightClick() && !event.isShiftClick()) {
                        amountToConvert = BigDecimal.valueOf(100);
                    } else if (event.isLeftClick() && event.isShiftClick()) {
                        amountToConvert = BigDecimal.valueOf(1000);
                    }

                    if (amountToConvert.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                    }

                    BigDecimal finalAmount = amountToConvert;
                    scheduler.async(() -> {
                        ExchangeOutcome result = exchangeService.exchange(viewerRef, finalAmount, source, target);
                        scheduler.onEntity(viewerRef, () -> {
                            handleExchangeResult(viewer, result, source, target);
                            open(viewer, source, target);
                        });
                    });
                }));

                // 4. Close Button (Slot 22)
                ItemStack closeItem = ItemBuilder.of(Material.BARRIER)
                        .name(text(viewerRef, EconomyMessageKey.BALTOP_GUI_CLOSE, Map.of()))
                        .build();
                gui.set(22, GuiItem.button(closeItem, event -> {
                    scheduler.onEntity(viewerRef, () -> gui.close(viewer));
                }));

                // Fillers
                ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(Component.empty())
                        .build();
                for (int slot = 0; slot < 27; slot++) {
                    if (slot != 11 && slot != 13 && slot != 15 && slot != 22) {
                        gui.set(slot, GuiItem.display(filler));
                    }
                }

                gui.open(viewer);
            });
        });
    }

    private void promptCustomAmount(Player player, Currency source, Currency target) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        String prefixStr = messages.resolve(viewerRef, () -> "prefix", Map.of());
        Component prefix = miniMessage.deserialize(prefixStr);
        String resolvedPrompt = messages.resolve(
                viewerRef,
                EconomyMessageKey.EXCHANGE_PROMPT,
                Map.of(
                        "source", source.plural(),
                        "target", target.plural()));
        Component promptMessage = prefix.append(miniMessage.deserialize(resolvedPrompt));

        chatPromptListener.prompt(player, promptMessage, input -> {
            BigDecimal amount;
            try {
                amount = new BigDecimal(input);
            } catch (NumberFormatException e) {
                String invalidMsg = messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_INVALID_AMOUNT, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(invalidMsg)));
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
        String prefixStr = messages.resolve(viewerRef, () -> "prefix", Map.of());
        Component prefix = miniMessage.deserialize(prefixStr);

        switch (result.status()) {
            case SUCCESS -> {
                String successMsg = messages.resolve(
                        viewerRef,
                        EconomyMessageKey.EXCHANGE_SUCCESS,
                        Map.of(
                                "source-amount", notifier.amount(Money.of(source, result.sourceAmount())),
                                "target-amount", notifier.amount(Money.of(target, result.targetAmount()))));
                player.sendMessage(prefix.append(miniMessage.deserialize(successMsg)));
            }
            case RATE_NOT_FOUND -> {
                String errorMsg = messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_RATE_NOT_FOUND, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
            case INSUFFICIENT_FUNDS -> {
                String errorMsg = messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_INSUFFICIENT_FUNDS, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
            case LIMIT_EXCEEDED -> {
                String errorMsg = messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_LIMIT_EXCEEDED, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
            case FAILED -> {
                com.uxplima.uxmessentials.economy.domain.TransferError err = result.error();
                com.uxplima.uxmessentials.economy.application.EconomyMessageKey key =
                        err != null ? err.messageKey() : EconomyMessageKey.PAY_ERROR;
                String errorMsg = messages.resolve(viewerRef, key, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
            case ROLLBACK_FAILED -> {
                player.getServer()
                        .getLogger()
                        .severe("exchange rollback failed for " + player.getUniqueId() + ": debited "
                                + result.sourceAmount() + " " + source.id().value()
                                + " could not be returned (cause=" + result.error() + ")");
                String errorMsg = messages.resolve(viewerRef, EconomyMessageKey.PAY_ERROR, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(errorMsg)));
            }
        }
    }

    private Material currencyMaterial(Currency currency) {
        return CurrencyIcons.materialFor(currency, Material.PAPER);
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }

    private List<Component> textLoreLines(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        String rawText = messages.resolve(viewer, key, placeholders);
        Iterable<String> lines = Splitter.on('\n').split(rawText);
        List<Component> list = new ArrayList<>();
        for (String line : lines) {
            list.add(miniMessage.deserialize(line));
        }
        return list;
    }
}
