package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The escape-hatch currency backend: an operator wires an economy nobody wrote a bridge for by pointing this at a
 * PlaceholderAPI placeholder for the balance and two console commands for the credit and the debit. Its id is
 * {@code placeholder:<name>}, named by a currency in {@code currencies.<id>.backend}.
 *
 * <p>A console command has no return value, so this backend <strong>cannot observe whether its take command
 * succeeded</strong>. {@link #debit} reads the balance, refuses when it falls short, dispatches the take command, and
 * returns {@link Result#ok()} optimistically. That blind spot is why {@link #atomicDebit()} is false — the serialising
 * wrapper cannot make a fire-and-forget command a guarded compare-and-take — and why config validation refuses to
 * schedule recurring charges (player-warp rent) against such a currency unless the operator turns on
 * {@code allow-nonatomic-recurring}. The sufficiency check still runs before every take, so a short balance is
 * rejected rather than handed a free purchase.
 *
 * <p>{@code Bukkit.dispatchCommand} is a main-thread API, so both commands hop onto the global region thread through
 * the injected {@link Scheduler}. The balance is resolved through {@link PlaceholderApiSupport}, so no
 * {@code me.clip.placeholderapi} type is named here and a server without PlaceholderAPI loads none of its classes; the
 * placeholder must resolve to a bare number, since {@link #balance} parses it with {@link BigDecimal}.
 */
public final class PlaceholderCurrencyBackend implements CurrencyBackend {

    private final String id;
    private final String balancePlaceholder;
    private final String giveCommand;
    private final String takeCommand;
    private final boolean worksOffline;
    private final Precision precision;
    private final Server server;
    private final Logger log;
    private final Scheduler scheduler;
    private final AtomicBoolean warned = new AtomicBoolean();

    private PlaceholderCurrencyBackend(
            String name,
            String balancePlaceholder,
            String giveCommand,
            String takeCommand,
            boolean worksOffline,
            Precision precision,
            Server server,
            Logger log,
            Scheduler scheduler) {
        this.id = "placeholder:" + Objects.requireNonNull(name, "name");
        this.balancePlaceholder = Objects.requireNonNull(balancePlaceholder, "balancePlaceholder");
        this.giveCommand = Objects.requireNonNull(giveCommand, "giveCommand");
        this.takeCommand = Objects.requireNonNull(takeCommand, "takeCommand");
        this.worksOffline = worksOffline;
        this.precision = Objects.requireNonNull(precision, "precision");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Build the backend from {@code backends.placeholder.<name>}. Both command templates are validated at load, so an
     * operator who copies a template carrying neither {@code %amount%} nor {@code %price%} gets a startup error naming
     * the currency and the setting rather than a silent no-op at the first charge. {@code integral} selects whether the
     * amount reaches the command as a whole number or at the currency's scale; {@code works-offline} defaults false.
     */
    public static CurrencyBackend fromConfig(
            String name, ConfigStore config, Server server, Logger log, Scheduler scheduler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(scheduler, "scheduler");
        String root = "backends.placeholder." + name;
        String balancePlaceholder = config.getString(root + ".balance-placeholder", "");
        String giveCommand = config.getString(root + ".give-command", "");
        String takeCommand = config.getString(root + ".take-command", "");
        validateCommand(name, "give-command", giveCommand);
        validateCommand(name, "take-command", takeCommand);
        Precision precision = config.getBoolean(root + ".integral", true) ? Precision.INTEGRAL : Precision.DECIMAL;
        boolean worksOffline = config.getBoolean(root + ".works-offline", false);
        return new PlaceholderCurrencyBackend(
                name, balancePlaceholder, giveCommand, takeCommand, worksOffline, precision, server, log, scheduler);
    }

    /** Substitute the player name and the amount; both {@code %amount%} and {@code %price%} are honoured. */
    static String renderCommand(String template, String playerName, String amount) {
        return template.replace("%player%", playerName)
                .replace("%amount%", amount)
                .replace("%price%", amount);
    }

    /** Reject at load a command that can never carry the amount, rather than no-oping at runtime. */
    static void validateCommand(String currency, String setting, String template) {
        if (!template.contains("%amount%") && !template.contains("%price%")) {
            throw new IllegalArgumentException(
                    "currency " + currency + ": " + setting + " must contain %amount% (or %price%); got: " + template);
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean available() {
        return PlaceholderApiSupport.isPresent();
    }

    @Override
    public boolean worksOffline() {
        return worksOffline;
    }

    @Override
    public boolean atomicDebit() {
        return false;
    }

    @Override
    public Precision precision() {
        return precision;
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        String text = PlaceholderApiSupport.messageBridge(owner.uuid()).apply(balancePlaceholder);
        try {
            return Money.of(currency, new BigDecimal(text.trim()));
        } catch (NumberFormatException failure) {
            if (warned.compareAndSet(false, true)) {
                log.warn("event=currency_backend_failed id={} reason=unparseable_balance", id);
            }
            return Money.zero(currency);
        }
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        dispatch(giveCommand, owner, amount);
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        if (balance(owner, amount.currency()).isLessThan(amount)) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        dispatch(takeCommand, owner, amount);
        return Result.ok();
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        return List.of();
    }

    private void dispatch(String template, PlayerRef owner, Money amount) {
        String value =
                ReflectiveCurrencyBackend.toBackendScale(amount, precision).toPlainString();
        String rendered = renderCommand(template, owner.name(), value);
        scheduler.onGlobal(() -> server.dispatchCommand(server.getConsoleSender(), rendered));
    }
}
