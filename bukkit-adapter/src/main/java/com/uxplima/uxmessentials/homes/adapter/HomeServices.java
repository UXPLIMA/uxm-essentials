package com.uxplima.uxmessentials.homes.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionView;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListView;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorView;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed homes use cases and views the Brigadier commands share, built once per module start by
 * {@code HomesWiring} from the kernel ports, the jOOQ repository, and the teleport-delegating teleporter.
 * {@code /home} opens the slot grid; {@code /homeadmin} drives the admin use case. The grid and its child
 * menus own every other home operation, so there is no per-command use case held here beyond admin — the
 * homes context keeps no other adapter-side runtime state.
 *
 * @param homeList the {@code /home} slot grid the player opens
 * @param homeActions the per-home action menu the grid opens (held so its lifecycle is owned in one place)
 * @param iconSelector the home-icon picker the action menu opens
 * @param homeAdmin the {@code /homeadmin} management use case
 * @param players name → ref resolution for the admin form
 * @param repository the home store, held only so the PlaceholderAPI seam can read it without blocking
 */
@NullMarked
public record HomeServices(
        HomeListView homeList,
        HomeActionView homeActions,
        IconSelectorView iconSelector,
        HomeAdmin homeAdmin,
        PlayerLookup players,
        HomeRepository repository) {

    public HomeServices {
        Objects.requireNonNull(homeList, "homeList");
        Objects.requireNonNull(homeActions, "homeActions");
        Objects.requireNonNull(iconSelector, "iconSelector");
        Objects.requireNonNull(homeAdmin, "homeAdmin");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(repository, "repository");
    }
}
