package com.example.uxmhooks;

import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletDebitEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomePreCreateEvent;
import com.uxplima.uxmessentials.api.view.UxmLocation;

/**
 * The two halves of the event API, one of each.
 *
 * <p>{@code Pre} events ask before something happens and can be cancelled. Everything else reports a fact that has
 * already happened, and cancelling is not on the table.
 */
public final class UxmHooksListener implements Listener {

    private final Logger log;
    private final UxmHooksQueries queries;

    public UxmHooksListener(Logger log) {
        this.log = log;
        this.queries = new UxmHooksQueries(log);
    }

    /**
     * Ask about a player as they arrive. {@code get()} answers null while uxmEssentials is absent or still
     * enabling, which is the whole of the guard a consumer needs: no dependency entry, no load-order assumption.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UxmEssentialsApi api = UxmEssentialsApi.get();
        if (api != null) {
            queries.describe(api, event.getPlayer().getUniqueId());
        }
    }

    /**
     * Refuse homes in the nether. This runs on whichever thread the use case is on, which is usually not the tick
     * thread, so it reads the event and decides without touching the Bukkit API.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHomeCreating(UxmHomePreCreateEvent event) {
        UxmLocation location = event.getLocation();
        if (location.world().endsWith("_nether")) {
            event.setCancelled(true);
        }
    }

    /** A home was created. Nothing to decide here, it already exists. */
    @EventHandler
    public void onHomeCreated(UxmHomeCreateEvent event) {
        log.info(event.getPlayerName() + " set home " + event.getSlot() + " in " + event.getLocation().world());
    }

    /** Money left a wallet. The amount carries its currency, since a server can run more than one. */
    @EventHandler
    public void onWalletDebited(UxmWalletDebitEvent event) {
        log.info(event.getPlayerName() + " spent " + event.getAmount().amount() + " "
                + event.getAmount().currency() + ", leaving " + event.getBalance().amount());
    }
}
