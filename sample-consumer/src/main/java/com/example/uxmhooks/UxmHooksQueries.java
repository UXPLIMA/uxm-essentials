package com.example.uxmhooks;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.api.view.UxmBaltopEntry;
import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.api.view.UxmMoney;

/**
 * Asking uxmEssentials questions, which is the other half of the API.
 *
 * <p>Every surface arrives as an {@code Optional}, and empty means the module is switched off rather than that the
 * answer is nothing. Feature-detect once, at the point of use, and behave sensibly when a module is absent: a server
 * that runs no economy is a normal server, not a broken one.
 *
 * <p>Anything the plugin has to read from its database answers with a {@link CompletableFuture}, and the read
 * already runs off the tick thread. Do not {@code join()} one on the main thread. Chain from it instead, and hop
 * back with the scheduler when the continuation touches the Bukkit API.
 *
 * <p>The queries that answer straight away are the ones whose state is already in memory: who is away, who is
 * hidden, which worlds are loaded, what teleport requests are open. They are cheap enough to call from a listener.
 */
public final class UxmHooksQueries {

    private final Logger log;

    public UxmHooksQueries(Logger log) {
        this.log = log;
    }

    /** What a player owns, read the way a plugin adding its own {@code /profile} command would read it. */
    public void describe(UxmEssentialsApi api, UUID playerId) {
        api.homes().ifPresent(homes -> logHomes(homes, playerId));
        api.economy().ifPresent(economy -> logBalance(economy, playerId));
    }

    private void logHomes(UxmHomesQuery homes, UUID playerId) {
        homes.list(playerId).thenAccept(this::logHomeNames).exceptionally(this::logFailure);
    }

    private void logHomeNames(List<UxmHome> owned) {
        log.info("they have " + owned.size() + " homes: "
                + owned.stream().map(UxmHome::displayName).toList());
    }

    private void logBalance(UxmEconomyQuery economy, UUID playerId) {
        // Two reads, and the second does not wait for the first: they are independent questions, so running them
        // one after the other would only make the answer arrive later.
        CompletableFuture<UxmMoney> balance = economy.balance(playerId);
        CompletableFuture<List<UxmBaltopEntry>> top = economy.top(3);

        balance.thenAcceptBoth(top, (held, richest) -> {
                    log.info("they hold " + held.amount() + " " + held.currency());
                    richest.forEach(entry ->
                            log.info("#" + entry.rank() + " " + entry.playerName() + " " + entry.balance().amount()));
                })
                .exceptionally(this::logFailure);
    }

    /**
     * Who is worth showing in a list, answered without waiting for anything.
     *
     * <p>Ask whether the viewer can see the target rather than whether the target is hidden: a server that layers
     * the vanish tiers expects staff to see the players below them, and the flag alone would hide everybody from
     * everybody.
     */
    public boolean shouldList(UxmEssentialsApi api, UUID viewerId, UUID targetId) {
        boolean visible = api.vanish()
                .map(vanish -> vanish.canSee(viewerId, targetId))
                .orElse(true);
        boolean away = api.presence().map(presence -> presence.isAfk(targetId)).orElse(false);
        return visible && !away;
    }

    /** Kept separate so the two paths above read as one line each rather than as a try/catch apiece. */
    private Void logFailure(Throwable failure) {
        log.warning("could not read from uxmEssentials: " + failure.getMessage());
        return null;
    }
}
