package com.uxplima.uxmessentials.economy.application.port;

import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Outbound port for migrating player balances from external plugins (EssentialsX, PlayerPoints, Vault).
 */
@NullMarked
public interface EconomyMigrator {

    /**
     * Executes the bakiye migration from the specified source.
     *
     * @param source the source plugin to migrate from ("essentialsx", "playerpoints", "vault")
     * @return Result.ok() on success, or Result.err(reason) on failure
     */
    Result<Unit, String> migrate(String source);
}
