package com.uxplima.uxmessentials.kits.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Listens for {@link PlayerJoinEvent} to automatically grant any kit configured with
 * {@code firstJoin = true} to players joining the server for the first time.
 */
@NullMarked
public final class KitsJoinListener implements Listener {

    private final KitRepository repository;
    private final KitGranter granter;
    private final KitAccess access;

    public KitsJoinListener(KitRepository repository, KitGranter granter, KitAccess access) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.granter = Objects.requireNonNull(granter, "granter");
        this.access = Objects.requireNonNull(access, "access");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        for (KitDefinition kit : repository.all()) {
            if (kit.firstJoin()) {
                granter.grant(who, access.resolveVariant(who, kit));
                if (kit.isOneTime()) {
                    access.recordClaim(who, kit);
                }
            }
        }
    }
}
