package com.uxplima.uxmessentials.itemworld.adapter.outbound.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmessentials.api.query.UxmItemworldQuery;
import com.uxplima.uxmessentials.api.view.UxmPowertool;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PdcPowertoolStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The published powertool query, over the same PDC stamp {@code /powertool} writes and {@code /powertoollist} reads.
 *
 * <p>The binding lives on the item, which is why both of these read an inventory rather than a table. That also
 * means a player who is not online has no answer to give: their items are wherever they left them, and the plugin
 * keeps no copy.
 *
 * <p>Both hop to the thread that owns the player before touching anything, since an inventory is only safely read
 * from there.
 */
@NullMarked
public final class ItemworldQueries implements UxmItemworldQuery {

    private final PdcPowertoolStore store;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public ItemworldQueries(PdcPowertoolStore store, PlayerLookup players, Scheduler scheduler) {
        this.store = Objects.requireNonNull(store, "store");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<Optional<UxmPowertool>> powertoolInHand(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        return AsyncQueries.onPlayer(scheduler, who, () -> inHand(playerId), Optional.empty());
    }

    @Override
    public CompletableFuture<List<UxmPowertool>> powertools(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerRef who = ApiValues.subject(players, playerId);
        return AsyncQueries.onPlayer(scheduler, who, () -> carried(playerId), List.of());
    }

    private Optional<UxmPowertool> inHand(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return Optional.empty();
        }
        PlayerInventory inventory = player.getInventory();
        return bound(inventory.getHeldItemSlot(), inventory.getItemInMainHand());
    }

    private List<UxmPowertool> carried(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return List.of();
        }
        ItemStack[] contents = player.getInventory().getContents();
        List<UxmPowertool> found = new ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            bound(slot, contents[slot]).ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    /** The binding on one item, or empty for an empty slot or an item nobody bound anything to. */
    private Optional<UxmPowertool> bound(int slot, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        return store.commandsOf(item)
                .map(commands -> new UxmPowertool(slot, item.getType().getKey().toString(), commands));
    }
}
