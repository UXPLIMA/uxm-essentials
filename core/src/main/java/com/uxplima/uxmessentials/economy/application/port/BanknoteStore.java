package com.uxplima.uxmessentials.economy.application.port;

import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Banknote;

/**
 * Outbound port for active banknotes tracking to prevent duplication exploits (dupes).
 */
public interface BanknoteStore {

    /** Registers a newly withdrawn banknote token in the database. */
    void register(Banknote banknote);

    /**
     * Checks if the banknote token is valid, and if so, deletes it (redeems) atomically.
     * Returns true if the banknote was valid and successfully redeemed, false otherwise.
     */
    boolean redeem(UUID token);
}
