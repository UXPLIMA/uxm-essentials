package com.uxplima.uxmessentials.api.bukkit.event.economy;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.jspecify.annotations.NullMarked;

/** Money was paid into a bank. */
@NullMarked
public final class UxmBankDepositEvent extends UxmBankEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmBankDepositEvent(UUID playerId, String playerName, String bankId, UxmMoney amount, UxmMoney bankBalance) {
        super(playerId, playerName, bankId, amount, bankBalance);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
