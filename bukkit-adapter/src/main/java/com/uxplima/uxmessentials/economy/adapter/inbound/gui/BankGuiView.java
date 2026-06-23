package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The bank-list menu listing the shared banks a player belongs to, with a create button. Clicking a bank opens
 * its actions menu through the {@link BankNavigation} supplier injected at construction (a final, non-null link
 * with no setter). Every label resolves through a {@code MessageKey} in the viewer's locale.
 */
@NullMarked
public final class BankGuiView {

    // Action-button slots in the reserved bottom row; the row count, nav slots, content slots, and icons are
    // externalised to modules/economy/gui/bank-list.conf, the rest stays in code with the menu's fixed actions.
    private static final int CREATE_SLOT = 49;
    private static final List<Integer> FILLER_SLOTS = List.of(46, 47, 48, 50, 51, 52);

    private final BankService bankService;
    private final CurrencyRegistry currencies;
    private final TextInput textInput;
    private final CurrencyPickerView currencyPicker;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Supplier<BankNavigation> navigation;
    private final GuiLayout layout;

    public BankGuiView(
            BankService bankService,
            CurrencyRegistry currencies,
            TextInput textInput,
            CurrencyPickerView currencyPicker,
            Scheduler scheduler,
            Messages messages,
            Supplier<BankNavigation> navigation,
            GuiLayout layout) {
        this.bankService = Objects.requireNonNull(bankService, "bankService");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.currencyPicker = Objects.requireNonNull(currencyPicker, "currencyPicker");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders)).decoration(TextDecoration.ITALIC, false);
    }

    public void open(Player player) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());

        scheduler.async(() -> {
            List<SharedBank> banks = new ArrayList<>();
            for (String id : bankService.getBankIdsForPlayer(viewerRef)) {
                bankService.getBank(id).ifPresent(banks::add);
            }
            scheduler.onEntity(viewerRef, () -> renderPage(player, viewerRef, banks));
        });
    }

    private void renderPage(Player player, PlayerRef viewerRef, List<SharedBank> banks) {
        Guis.PaginatedBuilder builder = Guis.paginated()
                .title(text(viewerRef, EconomyMessageKey.BANK_LIST_GUI_TITLE, Map.of()))
                .rows(layout.rows());
        layout.explicitContentSlots().ifPresent(builder::contentSlots);
        PaginatedGui gui = builder.build();

        gui.populate(
                banks,
                ItemPopulator.of(
                        bank -> bankIcon(viewerRef, bank),
                        (bank, event) -> scheduler.onEntity(
                                viewerRef,
                                () -> navigation.get().bankActionsView().open(player, bank))));

        gui.set(
                layout.prevSlot(),
                GuiItem.previousPage(
                        gui,
                        ItemBuilder.of(layout.navIcon())
                                .name(text(viewerRef, EconomyMessageKey.BANK_LIST_GUI_PREV, Map.of()))
                                .build()));
        gui.set(
                CREATE_SLOT,
                GuiItem.button(
                        ItemBuilder.of(Material.EMERALD_BLOCK)
                                .name(text(viewerRef, EconomyMessageKey.BANK_LIST_GUI_CREATE, Map.of()))
                                .lore(List.of(text(viewerRef, EconomyMessageKey.BANK_LIST_GUI_CREATE_LORE, Map.of())))
                                .build(),
                        event -> scheduler.onEntity(viewerRef, () -> {
                            gui.close(player);
                            promptCreateName(player);
                        })));
        gui.set(
                layout.nextSlot(),
                GuiItem.nextPage(
                        gui,
                        ItemBuilder.of(layout.navIcon())
                                .name(text(viewerRef, EconomyMessageKey.BANK_LIST_GUI_NEXT, Map.of()))
                                .build()));

        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int slot : FILLER_SLOTS) {
            gui.set(slot, GuiItem.display(filler));
        }
        gui.open(player);
    }

    private ItemStack bankIcon(PlayerRef viewer, SharedBank bank) {
        String balanceText = bank.balance().amount().toPlainString() + " "
                + bank.balance().currency().id().value();
        return ItemBuilder.of(Material.CHEST)
                .name(text(
                        viewer,
                        EconomyMessageKey.BANK_LIST_GUI_ICON_NAME,
                        Map.of("name", bank.name(), "id", bank.id())))
                .lore(List.of(
                        text(viewer, EconomyMessageKey.BANK_LIST_GUI_ICON_BALANCE, Map.of("balance", balanceText)),
                        text(
                                viewer,
                                EconomyMessageKey.BANK_LIST_GUI_ICON_CREATOR,
                                Map.of("creator", bank.creator().name())),
                        text(
                                viewer,
                                EconomyMessageKey.BANK_LIST_GUI_ICON_MEMBERS,
                                Map.of(
                                        "members",
                                        Integer.toString(bank.members().size()))),
                        Component.empty(),
                        text(viewer, EconomyMessageKey.BANK_LIST_GUI_ICON_HINT, Map.of())))
                .build();
    }

    private void promptCreateName(Player player) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        textInput.prompt(
                player,
                viewerRef,
                InputRequest.of("bank.create-name", EconomyMessageKey.BANK_CREATE_PROMPT_NAME),
                name -> {
                    String cleanName = name.trim();
                    if (cleanName.isEmpty()) {
                        player.sendMessage(text(viewerRef, EconomyMessageKey.BANK_CREATE_NAME_EMPTY, Map.of()));
                        open(player);
                        return;
                    }
                    pickCurrency(player, viewerRef, cleanName);
                },
                () -> open(player));
    }

    private void pickCurrency(Player player, PlayerRef viewerRef, String cleanName) {
        currencyPicker.open(
                player,
                viewerRef,
                List.copyOf(currencies.all()),
                currencies.defaultCurrency(),
                currency -> submitCreate(player, viewerRef, cleanName, currency));
    }

    private void submitCreate(Player player, PlayerRef viewerRef, String cleanName, Currency currency) {
        scheduler.async(() -> {
            Result<SharedBank, BankError> res = bankService.createBank(cleanName, currency, viewerRef);
            scheduler.onEntity(viewerRef, () -> {
                if (res.isOk()) {
                    SharedBank bank = res.orElseThrow();
                    player.sendMessage(text(
                            viewerRef,
                            EconomyMessageKey.BANK_CREATE_SUCCESS,
                            Map.of("id", bank.id(), "name", bank.name())));
                } else {
                    player.sendMessage(text(viewerRef, res.errorOrThrow().messageKey(), Map.of()));
                }
                open(player);
            });
        });
    }
}
