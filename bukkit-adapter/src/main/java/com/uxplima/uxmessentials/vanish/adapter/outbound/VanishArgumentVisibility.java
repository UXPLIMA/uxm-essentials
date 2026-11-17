package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerArgument;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import org.jspecify.annotations.NullMarked;

/**
 * Who may be told where a player on another server is, by vanish: a player vanished there is found only by a sender
 * whose see level reaches theirs. The network vanish view is memory, so the answer never waits on the database.
 *
 * <p>With vanish {@code cross-server} off this server keeps no view of who is vanished elsewhere, so it cannot tell a
 * vanished player from a visible one. Then only the console and senders who hold a see level are told; anyone else
 * reads that nobody has the name, which is what a vanished player must look like.
 */
@NullMarked
public final class VanishArgumentVisibility implements PlayerArgument.Visibility {

    private final VanishLevelResolver levels;
    private final NetworkVanishStore network;
    private final boolean tracked;

    /**
     * @param tracked whether {@code network} is fed by the cross-server bus, so a player it does not list is visible
     */
    public VanishArgumentVisibility(VanishLevelResolver levels, NetworkVanishStore network, boolean tracked) {
        this.levels = Objects.requireNonNull(levels, "levels");
        this.network = Objects.requireNonNull(network, "network");
        this.tracked = tracked;
    }

    @Override
    public boolean mayLocate(CommandSourceStack source, PlayerRef target) {
        if (!(source.getSender() instanceof Player viewer)) {
            return true;
        }
        int seeLevel = levels.seeLevel(BukkitRefs.toRef(viewer));
        Optional<VanishLevel> vanished = network.levelOf(target.uuid());
        if (vanished.isPresent()) {
            return VanishLevels.sees(seeLevel, vanished.get());
        }
        return tracked || seeLevel > VanishLevels.NO_SEE_LEVEL;
    }
}
