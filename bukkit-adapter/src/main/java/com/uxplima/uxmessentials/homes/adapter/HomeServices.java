package com.uxplima.uxmessentials.homes.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeMenuView;
import com.uxplima.uxmessentials.homes.application.DelHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.MoveHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHome;
import com.uxplima.uxmessentials.homes.application.SetMainHome;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed homes use cases the Brigadier commands share, built once per module start by
 * {@code HomesWiring} from the kernel ports, the jOOQ repository, and the teleport-delegating teleporter.
 * Held so every command reads the same use cases; the homes context keeps no other adapter-side runtime
 * state, so there is nothing here to drain on stop beyond dropping this holder.
 *
 * @param setHome {@code /sethome}
 * @param delHome {@code /delhome}
 * @param listHomes {@code /homes}
 * @param teleportHome {@code /home}
 * @param renameHome {@code /renamehome}
 * @param moveHome {@code /movehome}
 * @param setMainHome {@code /setmainhome}
 * @param homeAdmin {@code /homeadmin}
 * @param homeMenu the read-only {@code /homes} browse menu the bare {@code /homes} command opens
 * @param players name → ref resolution for the admin and {@code .others} forms
 */
@NullMarked
public record HomeServices(
        SetHome setHome,
        DelHome delHome,
        ListHomes listHomes,
        TeleportHome teleportHome,
        RenameHome renameHome,
        MoveHome moveHome,
        SetMainHome setMainHome,
        HomeAdmin homeAdmin,
        HomeMenuView homeMenu,
        PlayerLookup players) {

    public HomeServices {
        Objects.requireNonNull(setHome, "setHome");
        Objects.requireNonNull(delHome, "delHome");
        Objects.requireNonNull(listHomes, "listHomes");
        Objects.requireNonNull(teleportHome, "teleportHome");
        Objects.requireNonNull(renameHome, "renameHome");
        Objects.requireNonNull(moveHome, "moveHome");
        Objects.requireNonNull(setMainHome, "setMainHome");
        Objects.requireNonNull(homeAdmin, "homeAdmin");
        Objects.requireNonNull(homeMenu, "homeMenu");
        Objects.requireNonNull(players, "players");
    }
}
