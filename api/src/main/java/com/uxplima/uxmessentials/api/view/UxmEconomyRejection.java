package com.uxplima.uxmessentials.api.view;

/** Why a wallet operation was refused. */
public enum UxmEconomyRejection {

    /** The wallet did not hold enough to cover the debit. */
    INSUFFICIENT_FUNDS,

    /** The credit would have taken the balance past the currency's configured maximum. */
    BALANCE_MAX_EXCEEDED
}
