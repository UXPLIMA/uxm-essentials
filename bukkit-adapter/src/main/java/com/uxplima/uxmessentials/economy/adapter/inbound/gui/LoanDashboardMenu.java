package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.LoanService;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.LoanError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code /loan} dashboard with the menu engine and opens it. A three-row panel: the viewer's credit
 * profile, a strip of their active loans (one book per loan), a request-a-new-loan button, and a close button. The
 * credit profile and the active loans are read off the tick thread when the panel opens and handed in as the
 * {@link LoanDashboardSubject} the profile display and the loan strip render from — exactly as the old view did;
 * the repayment and request use cases are dispatched off-tick as the {@code /loan} command does.
 *
 * <p>Each loan in the strip is multi-gesture: every click routes through the one {@code economy:loan-entry} action,
 * which branches on the gesture — left pays an installment, right pays the loan off, and a shift-click prompts for a
 * custom amount through the shared input seam, then runs the matching {@link LoanService} repayment. The request
 * button hands off to {@link LoanRequestFlow}, which picks a currency through the shared engine picker and then
 * prompts for an amount and an installment count. The menu holds no new domain logic; every visible string resolves
 * from the economy catalog.
 */
@NullMarked
public final class LoanDashboardMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-loan";

    private static final String SPEC_RESOURCE = "modules/menu/specs/economy-loan.conf";
    private static final int ROWS = 3;

    private final Menus menus;
    private final LoanService loanService;
    private final CurrencyRegistry currencies;
    private final TextInput textInput;
    private final Scheduler scheduler;
    private final Messages messages;
    private final EconomyNotifier notifier;
    private final LoanRequestFlow requestFlow;

    public LoanDashboardMenu(
            Menus menus,
            LoanService loanService,
            CurrencyRegistry currencies,
            TextInput textInput,
            Scheduler scheduler,
            Messages messages,
            EconomyNotifier notifier,
            GuiText guiText) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.loanService = Objects.requireNonNull(loanService, "loanService");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(guiText, "guiText");
        CurrencyPickerView picker = new CurrencyPickerView(menus, guiText, scheduler);
        this.requestFlow =
                new LoanRequestFlow(loanService, currencies, textInput, scheduler, notifier, picker, this::open);
    }

    /**
     * Register the profile/entry placeholders, the active-loan list source, and the entry/request actions the spec
     * names, and the spec itself.
     */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("loan_profile_name", ctx -> resolve(ctx, EconomyMessageKey.LOAN_GUI_PROFILE_NAME));
        bindings.placeholder("loan_profile_lore", this::profileLore);
        bindings.placeholder("loan_entry_icon", ctx -> "BOOK");
        bindings.placeholder("loan_entry_name", this::entryName);
        bindings.placeholder("loan_entry_lore", this::entryLore);
        bindings.list("economy:loan-list", ctx -> subject(ctx).loans());
        bindings.action("economy:loan-entry", this::onEntryClick);
        bindings.action("economy:loan-request", ctx -> requestFlow.start(ctx.player()));
        menus.registerSpec(SPEC_ID, loadSpec(dataFolder, log));
    }

    /**
     * Open the dashboard for {@code player}. The credit profile and the active loans are read off the tick thread
     * (the old view's threading), then the panel is opened through the engine, which renders it on the viewer's
     * entity thread.
     */
    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler.async(() -> {
            Loan.CreditScore creditScore = loanService.getCreditScore(viewerRef);
            LoanService.LoanQuote quote = loanService.quote(creditScore.score());
            List<Loan> activeLoans = loanService.getActiveLoans(viewerRef);
            menus.open(viewerRef, SPEC_ID, new LoanDashboardSubject(creditScore, quote, List.copyOf(activeLoans)));
        });
    }

    /** The credit-profile lore: the score, the interest at that score, and one limit row per configured currency. */
    private String profileLore(MenuContext ctx) {
        LoanDashboardSubject subject = subject(ctx);
        PlayerRef viewer = ctx.viewer();
        BigDecimal interestPct = subject.quote().interestRate().multiply(BigDecimal.valueOf(100));
        String limit = subject.quote().limit().setScale(0, RoundingMode.HALF_UP).toPlainString();
        List<String> lines = new ArrayList<>();
        lines.add(resolve(
                viewer,
                EconomyMessageKey.LOAN_GUI_PROFILE_SCORE,
                Map.of("score", Long.toString(subject.creditScore().score()))));
        lines.add(resolve(
                viewer,
                EconomyMessageKey.LOAN_GUI_PROFILE_INTEREST,
                Map.of("rate", interestPct.setScale(1, RoundingMode.HALF_UP).toPlainString())));
        lines.add("");
        lines.add(resolve(viewer, EconomyMessageKey.LOAN_GUI_PROFILE_LIMITS_HEADER));
        for (Currency currency : currencies.all()) {
            lines.add(resolve(
                    viewer,
                    EconomyMessageKey.LOAN_GUI_PROFILE_LIMIT_ROW,
                    Map.of("currency", currency.id().value(), "limit", limit)));
        }
        return String.join("\n", lines);
    }

    /** The bound loan's entry name. */
    private String entryName(MenuContext ctx) {
        Loan loan = ctx.entry(Loan.class);
        return resolve(ctx.viewer(), EconomyMessageKey.LOAN_GUI_LOAN_NAME, Map.of("id", shortId(loan)));
    }

    /** The bound loan's entry lore block, joined for the engine to split into one component per line. */
    private String entryLore(MenuContext ctx) {
        Loan loan = ctx.entry(Loan.class);
        PlayerRef viewer = ctx.viewer();
        String currency = loan.principal().currency().id().value();
        List<String> lines = new ArrayList<>();
        lines.add(resolve(
                viewer,
                EconomyMessageKey.LOAN_GUI_LOAN_PRINCIPAL,
                Map.of("amount", loan.principal().amount().toPlainString(), "currency", currency)));
        lines.add(resolve(
                viewer,
                EconomyMessageKey.LOAN_GUI_LOAN_REMAINING,
                Map.of("amount", loan.remainingAmount().amount().toPlainString(), "currency", currency)));
        lines.add(resolve(
                viewer, EconomyMessageKey.LOAN_GUI_LOAN_INTEREST, Map.of("rate", percent(loan.interestRate()))));
        lines.add(resolve(
                viewer,
                EconomyMessageKey.LOAN_GUI_LOAN_INSTALLMENTS_LEFT,
                Map.of("count", Integer.toString(loan.remainingInstallments()))));
        lines.add(resolve(
                viewer,
                EconomyMessageKey.LOAN_GUI_LOAN_INSTALLMENT_PAYOUT,
                Map.of("amount", loan.installmentAmount().amount().toPlainString(), "currency", currency)));
        lines.add(nextDebit(viewer, loan));
        lines.add(resolve(viewer, EconomyMessageKey.LOAN_GUI_LOAN_DIVIDER));
        lines.add(resolve(viewer, EconomyMessageKey.LOAN_GUI_LOAN_HINT_INSTALLMENT));
        lines.add(resolve(viewer, EconomyMessageKey.LOAN_GUI_LOAN_HINT_FULL));
        lines.add(resolve(viewer, EconomyMessageKey.LOAN_GUI_LOAN_HINT_CUSTOM));
        return String.join("\n", lines);
    }

    /** The next-debit line: the remaining time until the next installment debits, or an overdue note. */
    private String nextDebit(PlayerRef viewer, Loan loan) {
        long remainingMs = loan.nextPaymentAt() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return resolve(viewer, EconomyMessageKey.LOAN_GUI_LOAN_NEXT_DEBIT_OVERDUE);
        }
        long hours = remainingMs / (60 * 60 * 1000);
        long mins = (remainingMs % (60 * 60 * 1000)) / (60 * 1000);
        String remaining = resolve(
                viewer,
                EconomyMessageKey.LOAN_GUI_LOAN_NEXT_DEBIT_REMAINING,
                Map.of("hours", Long.toString(hours), "minutes", Long.toString(mins)));
        return resolve(viewer, EconomyMessageKey.LOAN_GUI_LOAN_NEXT_DEBIT, Map.of("remaining", remaining));
    }

    /**
     * Branch the loan-entry click: left pays an installment, right pays the loan off, and a shift-click prompts for
     * a custom amount through the shared input seam. The bound loan is the clicked list entry.
     */
    private void onEntryClick(MenuActionContext ctx) {
        Player player = ctx.player();
        Loan loan = ctx.entry(Loan.class);
        ClickKind kind = ctx.clickKind();
        if (kind == ClickKind.SHIFT_LEFT || kind == ClickKind.SHIFT_RIGHT) {
            promptCustomRepayment(player, loan);
            return;
        }
        if (kind == ClickKind.RIGHT) {
            applyRepayment(
                    player,
                    loan,
                    loan.remainingAmount(),
                    EconomyMessageKey.LOAN_GUI_PAID_OFF,
                    EconomyMessageKey.LOAN_GUI_PAID_OFF_FAILED);
            return;
        }
        applyRepayment(
                player,
                loan,
                loan.installmentAmount(),
                EconomyMessageKey.LOAN_GUI_INSTALLMENT_PAID,
                EconomyMessageKey.LOAN_GUI_INSTALLMENT_FAILED);
    }

    /** Capture a custom repayment amount through the input seam, then repay; an abort or malformed amount reopens. */
    private void promptCustomRepayment(Player player, Loan loan) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        Currency currency = loan.remainingAmount().currency();
        textInput.prompt(
                player,
                viewerRef,
                InputRequest.of(
                        "loan.repay-custom", EconomyMessageKey.LOAN_GUI_CUSTOM_PROMPT, Map.of("id", shortId(loan))),
                amountStr -> applyCustomRepayment(player, viewerRef, loan, currency, amountStr),
                () -> open(player));
    }

    /** Parse the typed custom amount against the loan's currency and repay, or reject and reopen. Package-private for tests. */
    void applyCustomRepayment(Player player, PlayerRef viewerRef, Loan loan, Currency currency, String amountStr) {
        Result<Money, AmountParseError> parsed = AmountParser.parse(amountStr, currency);
        if (parsed.isErr()) {
            notifier.send(viewerRef, EconomyMessageKey.LOAN_GUI_INVALID_AMOUNT);
            open(player);
            return;
        }
        applyRepayment(
                player,
                loan,
                parsed.orElseThrow(),
                EconomyMessageKey.LOAN_GUI_CUSTOM_PAID,
                EconomyMessageKey.LOAN_GUI_PAYMENT_FAILED);
    }

    /** Dispatch the repayment off the click thread, report the outcome, then reopen the dashboard. */
    private void applyRepayment(
            Player player, Loan loan, Money amount, EconomyMessageKey okKey, EconomyMessageKey errKey) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        Currency currency = loan.remainingAmount().currency();
        scheduler.async(() -> {
            Result<Unit, LoanError> res = loanService.payInstallment(viewerRef, loan.id(), amount);
            scheduler.onEntity(viewerRef, () -> {
                if (res.isOk()) {
                    notifier.send(
                            viewerRef,
                            okKey,
                            Map.of(
                                    "amount", amount.amount().toPlainString(),
                                    "currency", currency.id().value()));
                } else {
                    notifier.send(viewerRef, errKey);
                }
                open(player);
            });
        });
    }

    private LoanDashboardSubject subject(MenuContext ctx) {
        return ctx.subject(LoanDashboardSubject.class);
    }

    private String resolve(MenuContext ctx, EconomyMessageKey key) {
        return resolve(ctx.viewer(), key);
    }

    private String resolve(PlayerRef viewer, EconomyMessageKey key) {
        return messages.resolve(viewer, key, Map.of());
    }

    private String resolve(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return messages.resolve(viewer, key, placeholders);
    }

    private static String shortId(Loan loan) {
        return loan.id().substring(Math.max(0, loan.id().length() - 8));
    }

    private static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * Load the spec, preferring an operator's edit on disk over the bundled resource and finally a built-in empty
     * fallback, so a typo or a missing file degrades to a closeable empty window rather than aborting economy
     * wiring. Resolution mirrors {@code GuiLayouts}: disk first, then the classpath default.
     */
    private MenuSpec loadSpec(Path dataFolder, Logger log) {
        MenuSpecLoader specLoader = new MenuSpecLoader();
        Path onDisk = dataFolder.resolve(SPEC_RESOURCE);
        if (Files.isRegularFile(onDisk)) {
            try {
                return specLoader.load(onDisk);
            } catch (MenuSpecException malformed) {
                log.error("failed to load menu spec " + onDisk + ", using bundled default", malformed);
            }
        }
        return loadBundledSpec(specLoader, log);
    }

    private MenuSpec loadBundledSpec(MenuSpecLoader specLoader, Logger log) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SPEC_RESOURCE)) {
            if (in == null) {
                log.warn("bundled menu spec {} is missing from the jar", SPEC_RESOURCE);
                return emptySpec();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return specLoader.parse(reader.lines().collect(Collectors.joining("\n")));
            }
        } catch (IOException | MenuSpecException failure) {
            log.error("could not read bundled menu spec " + SPEC_RESOURCE, failure);
            return emptySpec();
        }
    }

    /** A minimal valid spec used only when the real one cannot be read, so economy still wires cleanly. */
    private static MenuSpec emptySpec() {
        return new MenuSpec("", ROWS, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * The subject of an open dashboard: the viewer's credit score, the loan terms that score qualifies for, and
     * their active loans — all read at open time. The profile placeholders read the score and quote directly and
     * the loan strip lists the loans, so the render touches no port.
     *
     * @param creditScore the viewer's credit score, read at open time
     * @param quote the loan terms that score qualifies for, computed from the policy at open time
     * @param loans the viewer's active loans, read at open time
     */
    public record LoanDashboardSubject(Loan.CreditScore creditScore, LoanService.LoanQuote quote, List<Loan> loans) {

        public LoanDashboardSubject {
            Objects.requireNonNull(creditScore, "creditScore");
            Objects.requireNonNull(quote, "quote");
            loans = List.copyOf(loans);
        }
    }
}
