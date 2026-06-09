package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.adapter.inbound.listener.LoanChatPromptListener;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.LoanService;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.LoanError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The /loan dashboard: a player's credit profile, active loans (left-click to pay an installment, right-click to
 * pay off, shift-click for a custom amount), and a request-new-loan flow. Every label resolves through a
 * {@code MessageKey} in the viewer's locale, never an inline literal; the icon rendering lives in
 * {@link LoanIcons} so this class stays within the size budget.
 */
@NullMarked
public final class LoanGuiView {

    private final LoanService loanService;
    private final LoanChatPromptListener chatPromptListener;
    private final Scheduler scheduler;
    private final Messages messages;
    private final LoanIcons icons;
    private final LoanRequestFlow requestFlow;
    private final FixedMenuLayout layout;
    private final MiniMessage miniMessage;

    public LoanGuiView(
            LoanService loanService,
            CurrencyRegistry currencies,
            LoanChatPromptListener chatPromptListener,
            Scheduler scheduler,
            Messages messages,
            FixedMenuLayout layout) {
        this.loanService = Objects.requireNonNull(loanService, "loanService");
        Objects.requireNonNull(currencies, "currencies");
        this.chatPromptListener = Objects.requireNonNull(chatPromptListener, "chatPromptListener");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.icons = new LoanIcons(loanService, messages, currencies);
        this.requestFlow = new LoanRequestFlow(
                loanService, currencies, chatPromptListener, scheduler, messages, icons, this::open);
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** The shipped geometry: profile, active-loan strip, request, and close, externalised to loan-dashboard.conf. */
    public static FixedMenuLayout defaultLayout() {
        return FixedMenuLayout.builder(3, Material.GRAY_STAINED_GLASS_PANE)
                .slotOnly("profile", 4)
                .slotOnly("loan-first", 10)
                .slotOnly("loan-last", 16)
                .element("request", 20, Material.EMERALD_BLOCK)
                .element("close", 22, Material.BARRIER)
                .build();
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage
                .deserialize(messages.resolve(viewer, key, placeholders))
                .decoration(TextDecoration.ITALIC, false);
    }

    public void open(Player player) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler.async(() -> {
            Loan.CreditScore creditScore = loanService.getCreditScore(viewerRef);
            List<Loan> activeLoans = loanService.getActiveLoans(viewerRef);
            scheduler.onEntity(viewerRef, () -> renderDashboard(player, viewerRef, creditScore, activeLoans));
        });
    }

    private void renderDashboard(
            Player player, PlayerRef viewerRef, Loan.CreditScore creditScore, List<Loan> activeLoans) {
        SimpleGui gui = Guis.gui()
                .title(text(viewerRef, EconomyMessageKey.LOAN_GUI_TITLE, Map.of()))
                .rows(layout.rows())
                .build();

        ItemStack filler =
                ItemBuilder.of(layout.fillerMaterial()).name(Component.empty()).build();
        for (int i = 0; i < layout.rows() * 9; i++) {
            gui.set(i, GuiItem.display(filler));
        }

        gui.set(layout.slot("profile"), GuiItem.display(icons.profile(viewerRef, creditScore)));
        placeActiveLoans(gui, player, viewerRef, activeLoans);
        placeRequest(gui, player, viewerRef);
        placeClose(gui, player, viewerRef);

        gui.open(player);
    }

    private void placeActiveLoans(SimpleGui gui, Player player, PlayerRef viewerRef, List<Loan> activeLoans) {
        int slotIndex = layout.slot("loan-first");
        int lastSlot = layout.slot("loan-last");
        for (Loan loan : activeLoans) {
            if (slotIndex > lastSlot) {
                break;
            }
            gui.set(
                    slotIndex,
                    GuiItem.button(
                            icons.loan(viewerRef, loan),
                            event -> scheduler.onEntity(viewerRef, () -> {
                                if (event.isShiftClick()) {
                                    gui.close(player);
                                    promptCustomRepayment(player, loan);
                                } else if (event.isRightClick()) {
                                    payOffLoan(player, loan);
                                } else {
                                    payInstallment(player, loan);
                                }
                            })));
            slotIndex++;
        }
    }

    private void placeRequest(SimpleGui gui, Player player, PlayerRef viewerRef) {
        ItemStack requestItem = ItemBuilder.of(layout.material("request"))
                .name(text(viewerRef, EconomyMessageKey.LOAN_GUI_REQUEST_NAME, Map.of()))
                .lore(List.of(
                        text(viewerRef, EconomyMessageKey.LOAN_GUI_REQUEST_LORE, Map.of()),
                        Component.empty(),
                        text(viewerRef, EconomyMessageKey.LOAN_GUI_REQUEST_HINT, Map.of())))
                .build();
        gui.set(
                layout.slot("request"),
                GuiItem.button(
                        requestItem,
                        event -> scheduler.onEntity(viewerRef, () -> {
                            gui.close(player);
                            requestFlow.openCurrencySelector(player);
                        })));
    }

    private void placeClose(SimpleGui gui, Player player, PlayerRef viewerRef) {
        ItemStack closeItem = ItemBuilder.of(layout.material("close"))
                .name(text(viewerRef, EconomyMessageKey.LOAN_GUI_CLOSE, Map.of()))
                .build();
        gui.set(
                layout.slot("close"),
                GuiItem.button(closeItem, event -> scheduler.onEntity(viewerRef, () -> gui.close(player))));
    }

    private void promptCustomRepayment(Player player, Loan loan) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        Currency currency = loan.remainingAmount().currency();
        String shortId = loan.id().substring(Math.max(0, loan.id().length() - 8));
        chatPromptListener.prompt(
                player, text(viewerRef, EconomyMessageKey.LOAN_GUI_CUSTOM_PROMPT, Map.of("id", shortId)), amountStr -> {
                    Result<Money, AmountParseError> parsed = AmountParser.parse(amountStr, currency);
                    if (parsed.isErr()) {
                        player.sendMessage(text(viewerRef, EconomyMessageKey.LOAN_GUI_INVALID_AMOUNT, Map.of()));
                        scheduler.onEntity(viewerRef, () -> open(player));
                        return;
                    }
                    Money amount = parsed.orElseThrow();
                    applyRepayment(
                            player,
                            viewerRef,
                            loan,
                            amount,
                            EconomyMessageKey.LOAN_GUI_CUSTOM_PAID,
                            EconomyMessageKey.LOAN_GUI_PAYMENT_FAILED);
                });
    }

    private void payInstallment(Player player, Loan loan) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        applyRepayment(
                player,
                viewerRef,
                loan,
                loan.installmentAmount(),
                EconomyMessageKey.LOAN_GUI_INSTALLMENT_PAID,
                EconomyMessageKey.LOAN_GUI_INSTALLMENT_FAILED);
    }

    private void payOffLoan(Player player, Loan loan) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        applyRepayment(
                player,
                viewerRef,
                loan,
                loan.remainingAmount(),
                EconomyMessageKey.LOAN_GUI_PAID_OFF,
                EconomyMessageKey.LOAN_GUI_PAID_OFF_FAILED);
    }

    private void applyRepayment(
            Player player,
            PlayerRef viewerRef,
            Loan loan,
            Money amount,
            EconomyMessageKey okKey,
            EconomyMessageKey errKey) {
        Currency currency = loan.remainingAmount().currency();
        scheduler.async(() -> {
            Result<Unit, LoanError> res = loanService.payInstallment(viewerRef, loan.id(), amount);
            scheduler.onEntity(viewerRef, () -> {
                if (res.isOk()) {
                    player.sendMessage(text(
                            viewerRef,
                            okKey,
                            Map.of(
                                    "amount",
                                    amount.amount().toPlainString(),
                                    "currency",
                                    currency.id().value())));
                } else {
                    player.sendMessage(text(viewerRef, errKey, Map.of()));
                }
                open(player);
            });
        });
    }
}
