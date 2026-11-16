package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The players online on this server, for the verbs that mean exactly them: {@code /eco giverandom},
 * {@code /eco giveall --online} and {@code /payall}. The roster is read once on the thread that may read it and handed to the off-tick worker as an
 * immutable list, so the database writes never run on the tick thread. {@code /eco giveall|resetall} without
 * {@code --online} do not come here: they reach every account through the ledger's sweep.
 */
@NullMarked
final class EcoTargets {

    private EcoTargets() {}

    /**
     * A snapshot of the online players as {@link PlayerRef}s. Called only from the {@code /eco} and {@code /payall}
     * Brigadier handlers, which Paper dispatches on the global region thread, the one thread where
     * {@code Bukkit.getOnlinePlayers()} is consistently readable on Folia, so the enumeration is already on the
     * correct thread and needs no {@code onGlobal} hop. The refs are handed to the off-tick worker as an immutable
     * list so the DB writes never touch the roster off-thread.
     */
    static List<PlayerRef> online() {
        List<PlayerRef> refs = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refs.add(BukkitRefs.toRef(player));
        }
        return List.copyOf(refs);
    }

    /** One randomly chosen ref from {@code online}, or empty when nobody is connected. */
    static Optional<PlayerRef> randomOnline(List<PlayerRef> online) {
        if (online.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(online.get(ThreadLocalRandom.current().nextInt(online.size())));
    }
}
