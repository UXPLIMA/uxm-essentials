package com.uxplima.uxmessentials.economy.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The economy context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code WALLET_BALANCE} ↔ {@code wallet.balance}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context — every message resolves through one of these, and the keys mirror the economy GLOSSARY
 * terms ({@code wallet.*}, {@code pay.*}, {@code baltop.*}, {@code currency.*}).
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum EconomyMessageKey implements MessageKey {

    // /balance
    WALLET_BALANCE("wallet.balance"),
    WALLET_BALANCE_OTHER("wallet.balance-other"),

    // /pay
    PAY_SENT("pay.sent"),
    PAY_RECEIVED("pay.received"),
    PAY_SELF("pay.self"),
    PAY_INSUFFICIENT("pay.insufficient"),
    PAY_BELOW_MINIMUM("pay.below-minimum"),
    PAY_INVALID_AMOUNT("pay.invalid-amount"),
    PAY_TARGET_DISABLED("pay.target-disabled"),
    PAY_TARGET_UNKNOWN("pay.target-unknown"),
    PAY_ERROR("pay.error"),
    PAY_CONFIRM_PROMPT("pay.confirm-prompt"),
    PAY_CONFIRM_NONE("pay.confirm-none"),
    PAY_CONFIRM_EXPIRED("pay.confirm-expired"),

    // /paytoggle
    PAY_TOGGLE_ON("pay.toggle-on"),
    PAY_TOGGLE_OFF("pay.toggle-off"),

    // /payall
    PAYALL_SENT("wallet.payall-sent"),

    // currency selection
    CURRENCY_UNKNOWN("currency.unknown"),
    CURRENCY_UNSUPPORTED("currency.unsupported"),

    // balance clamp
    BALANCE_MAX_EXCEEDED("wallet.max-exceeded"),

    // /baltop
    BALTOP_HEADER("baltop.header"),
    BALTOP_ROW("baltop.row"),
    BALTOP_EMPTY("baltop.empty"),

    // /worth
    WORTH_RESULT("wallet.worth-result"),
    WORTH_RESULT_STACK("wallet.worth-result-stack"),
    WORTH_UNKNOWN_ITEM("wallet.worth-unknown-item"),
    WORTH_NO_ITEM_IN_HAND("wallet.worth-no-item-in-hand"),
    WORTH_NOT_SELLABLE("wallet.worth-not-sellable"),

    // /sell
    SELL_SOLD("wallet.sell-sold"),
    SELL_NOTHING_TO_SELL("wallet.sell-nothing"),
    SELL_NOT_SELLABLE("wallet.sell-not-sellable"),
    SELL_NO_ITEM_IN_HAND("wallet.sell-no-item-in-hand"),
    // /sellall
    SELLALL_SUMMARY("wallet.sellall-summary"),

    // eco admin
    ECO_ADMIN_GIVEN("eco.admin.given"),
    ECO_ADMIN_TAKEN("eco.admin.taken"),
    ECO_ADMIN_SET("eco.admin.set"),
    ECO_ADMIN_RESET("eco.admin.reset"),
    ECO_ADMIN_GIVEALL("eco.admin.giveall"),
    ECO_ADMIN_GIVERANDOM("eco.admin.giverandom"),
    ECO_ADMIN_RESETALL("eco.admin.resetall"),
    ECO_ADMIN_RESETALL_CONFIRM("eco.admin.resetall-confirm"),
    ECO_ADMIN_TARGET_UNKNOWN("eco.admin.target-unknown"),
    ECO_ADMIN_NO_TARGETS("eco.admin.no-targets");

    private final String key;

    EconomyMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
