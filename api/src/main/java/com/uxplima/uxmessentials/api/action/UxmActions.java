package com.uxplima.uxmessentials.api.action;

import java.util.Optional;

/**
 * Everything uxmEssentials can be asked to do, on behalf of one plugin.
 *
 * <p>Obtained from the front door with your own plugin, which is what the audit log will name:
 *
 * <pre>{@code
 * UxmActions actions = api.actions(this);
 * actions.economy().ifPresent(economy ->
 *     economy.deposit(playerId, new BigDecimal("50"))
 *         .thenAccept(result -> result.ifFailed(failure -> getLogger().warning(failure.message()))));
 * }</pre>
 *
 * <p>Every accessor is an {@link Optional} for the same reason the queries are: empty means the module is off,
 * which is a different thing from the operation failing, and worth telling apart in a log line.
 */
public interface UxmActions {

    /** Moving money, or empty when the economy module is switched off. */
    Optional<UxmEconomyActions> economy();

    /** Setting and removing homes, or empty when the homes module is switched off. */
    Optional<UxmHomeActions> homes();

    /** Creating and removing warps, or empty when the warps module is switched off. */
    Optional<UxmWarpActions> warps();

    /** Handing out kits, or empty when the kits module is switched off. */
    Optional<UxmKitActions> kits();
}
