package com.uxplima.uxmessentials.economy.domain;

/** What a sweep over every account does to each balance. */
public enum SweepKind {
    /** {@code /eco giveall}: add the amount. */
    CREDIT,
    /** {@code /eco resetall}: set the balance to the amount, the currency's starting figure. */
    RESET
}
