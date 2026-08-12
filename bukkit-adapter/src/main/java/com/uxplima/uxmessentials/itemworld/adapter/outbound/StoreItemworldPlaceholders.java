package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.ItemworldPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ItemworldPlaceholders} seam over the live powertool and unlimited-placement stores. The binding is
 * read off the item in the player's main hand, which is the same item {@code /powertool} writes to, so a HUD line
 * and the command always describe the same stack.
 *
 * <p>A player who is not connected holds no item and no switch, so every key reports nothing for them.
 */
@NullMarked
public final class StoreItemworldPlaceholders implements ItemworldPlaceholders {

    private final Server server;
    private final PdcPowertoolStore powertools;
    private final PowertoolToggleStore powertoolToggle;
    private final UnlimitedPlacementStore unlimited;

    public StoreItemworldPlaceholders(
            Server server,
            PdcPowertoolStore powertools,
            PowertoolToggleStore powertoolToggle,
            UnlimitedPlacementStore unlimited) {
        this.server = Objects.requireNonNull(server, "server");
        this.powertools = Objects.requireNonNull(powertools, "powertools");
        this.powertoolToggle = Objects.requireNonNull(powertoolToggle, "powertoolToggle");
        this.unlimited = Objects.requireNonNull(unlimited, "unlimited");
    }

    @Override
    public List<String> powertool(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return List.of();
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return List.of();
        }
        return powertools.commandsOf(hand).orElseGet(List::of);
    }

    @Override
    public boolean powertoolEnabled(PlayerRef who) {
        return powertoolToggle.enabled(Objects.requireNonNull(who, "who"));
    }

    @Override
    public boolean unlimitedPlacement(PlayerRef who) {
        return unlimited.enabled(Objects.requireNonNull(who, "who"));
    }
}
