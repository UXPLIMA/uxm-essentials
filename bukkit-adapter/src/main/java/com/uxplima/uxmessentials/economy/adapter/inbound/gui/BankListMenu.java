package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code /bank} list menu with the menu engine and opens it. A paginated grid of one chest per shared
 * bank the viewer belongs to, showing the bank's name, id, balance, creator and member count. A left click opens that
 * bank's engine {@link BankActionsMenu} hub through the {@link BankNavigation} supplier; the create button
 * opens the still-bespoke {@link CurrencyPickerView} after prompting for a name, exactly as the old view did.
 *
 * <p>The bank list is a repository read (no Bukkit call), but each bank is resolved by id, so {@link #open} runs the
 * read off the tick thread and hands the already-read banks in as the menu subject; the {@code economy:banks} list
 * source only reads that subject. The {@code bank_*} placeholders fill each chest from the bound bank, prefixed so
 * they never collide with the generic engine tokens or another menu's fields. Every label resolves from the economy
 * catalog, so no user-facing text lives here. The geometry mirrors the original list: a grid across the top five rows,
 * the nav arrows at the corners of the bottom row, and the create button in its centre.
 */
@NullMarked
public final class BankListMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-banks";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-banks.conf";

    private final Menus menus;
    private final BankService bankService;
    private final CurrencyRegistry currencies;
    private final TextInput textInput;
    private final CurrencyPickerView currencyPicker;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Supplier<BankNavigation> navigation;

    public BankListMenu(
            Menus menus,
            BankService bankService,
            CurrencyRegistry currencies,
            TextInput textInput,
            CurrencyPickerView currencyPicker,
            Scheduler scheduler,
            Messages messages,
            Supplier<BankNavigation> navigation) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.bankService = Objects.requireNonNull(bankService, "bankService");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.currencyPicker = Objects.requireNonNull(currencyPicker, "currencyPicker");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    /** Register the list source, the per-bank placeholders, the open/create actions, and the spec; called once. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("economy:banks", ctx -> subject(ctx).banks());
        bindings.placeholder("bank_name", ctx -> entry(ctx).name());
        bindings.placeholder("bank_id", ctx -> entry(ctx).id());
        bindings.placeholder("bank_balance", ctx -> balanceText(entry(ctx)));
        bindings.placeholder("bank_creator", ctx -> entry(ctx).creator().name());
        bindings.placeholder(
                "bank_members", ctx -> Integer.toString(entry(ctx).members().size()));
        bindings.action("economy:open-bank", this::openBank);
        bindings.action("economy:create-bank", this::createBank);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Resolve the viewer's banks off the tick thread, then open the list for {@code player}. */
    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler.async(() -> menus.open(viewer, SPEC_ID, snapshot(viewer)));
    }

    /** Read the viewer's banks off the tick thread, resolving each by id, so the menu opens without a DB read. */
    private BankGrid snapshot(PlayerRef viewer) {
        List<SharedBank> banks = new ArrayList<>();
        for (String id : bankService.getBankIdsForPlayer(viewer)) {
            bankService.getBank(id).ifPresent(banks::add);
        }
        return new BankGrid(banks);
    }

    /** Left-click a bank chest: open that bank's bespoke actions hub on the viewer's entity thread. */
    private void openBank(MenuActionContext ctx) {
        SharedBank bank = ctx.entry(SharedBank.class);
        navigation.get().bankActionsView().open(ctx.player(), bank);
    }

    /** Left-click the create button: prompt for a name, then open the bespoke currency picker, as the old button did. */
    private void createBank(MenuActionContext ctx) {
        promptCreateName(ctx.player());
    }

    private BankGrid subject(MenuContext ctx) {
        return ctx.subject(BankGrid.class);
    }

    private SharedBank entry(MenuContext ctx) {
        return ctx.entry(SharedBank.class);
    }

    private static String balanceText(SharedBank bank) {
        return bank.balance().amount().toPlainString() + " "
                + bank.balance().currency().id().value();
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders)).decoration(TextDecoration.ITALIC, false);
    }

    private void promptCreateName(Player player) {
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("bank.create-name", EconomyMessageKey.BANK_CREATE_PROMPT_NAME),
                name -> {
                    String cleanName = name.trim();
                    if (cleanName.isEmpty()) {
                        player.sendMessage(text(viewer, EconomyMessageKey.BANK_CREATE_NAME_EMPTY, Map.of()));
                        open(player);
                        return;
                    }
                    pickCurrency(player, viewer, cleanName);
                },
                () -> open(player));
    }

    private void pickCurrency(Player player, PlayerRef viewer, String cleanName) {
        currencyPicker.open(
                player,
                viewer,
                List.copyOf(currencies.all()),
                currencies.defaultCurrency(),
                currency -> submitCreate(player, viewer, cleanName, currency));
    }

    private void submitCreate(Player player, PlayerRef viewer, String cleanName, Currency currency) {
        scheduler.async(() -> {
            Result<SharedBank, BankError> res = bankService.createBank(cleanName, currency, viewer);
            scheduler.onEntity(viewer, () -> {
                if (res.isOk()) {
                    SharedBank bank = res.orElseThrow();
                    player.sendMessage(text(
                            viewer,
                            EconomyMessageKey.BANK_CREATE_SUCCESS,
                            Map.of("id", bank.id(), "name", bank.name())));
                } else {
                    player.sendMessage(text(viewer, res.errorOrThrow().messageKey(), Map.of()));
                }
                open(player);
            });
        });
    }

    /**
     * The subject of an open bank list: the already-read banks the viewer belongs to, snapshotted off the tick thread
     * before the open so the engine renders without a DB read. The list source and the placeholders read this, so the
     * menu carries no bank query of its own once it opens.
     *
     * @param banks the viewer's banks, in the order the repository returns their ids
     */
    public record BankGrid(List<SharedBank> banks) {

        public BankGrid {
            banks = List.copyOf(Objects.requireNonNull(banks, "banks"));
        }
    }
}
