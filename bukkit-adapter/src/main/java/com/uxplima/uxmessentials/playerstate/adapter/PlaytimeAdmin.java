package com.uxplima.uxmessentials.playerstate.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.VanillaPlaytime;
import com.uxplima.uxmessentials.playerstate.application.AdjustPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ImportPlaytime;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The operator side of {@code /playtime}: set, add, take and import. It puts each use case on the thread it needs.
 * The database work runs on the async lane, and the vanilla statistic is read by {@link VanillaPlaytime} on the
 * thread that owns it.
 *
 * <p>{@code /playtime import all} walks every player the server has a profile for, one after the other, so a server
 * with many players gets a steady trickle of reads rather than a burst. A player whose import fails is logged and
 * skipped; the rest carry on.
 */
@NullMarked
public final class PlaytimeAdmin {

    private final Scheduler scheduler;
    private final AdjustPlaytime adjust;
    private final ImportPlaytime imports;
    private final VanillaPlaytime vanilla;
    private final Logger log;

    public PlaytimeAdmin(
            Scheduler scheduler, AdjustPlaytime adjust, ImportPlaytime imports, VanillaPlaytime vanilla, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.adjust = Objects.requireNonNull(adjust, "adjust");
        this.imports = Objects.requireNonNull(imports, "imports");
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** {@code /playtime set <player> <time>}. */
    public void set(PlayerRef actor, PlayerRef subject, String amount) {
        scheduler.async(() -> adjust.set(actor, subject, amount));
    }

    /** {@code /playtime add <player> <time>}. */
    public void add(PlayerRef actor, PlayerRef subject, String amount) {
        scheduler.async(() -> adjust.add(actor, subject, amount));
    }

    /** {@code /playtime take <player> <time>}. */
    public void take(PlayerRef actor, PlayerRef subject, String amount) {
        scheduler.async(() -> adjust.take(actor, subject, amount));
    }

    /** {@code /playtime import <player>}. */
    public void importOne(PlayerRef actor, PlayerRef subject) {
        vanilla.read(subject, played -> imports.importFor(actor, subject, played));
    }

    /** {@code /playtime import all}: every player the server has a profile for. */
    public void importAll(PlayerRef actor) {
        scheduler.onGlobal(() -> {
            List<PlayerRef> everyone = new ArrayList<>();
            for (OfflinePlayer account : Bukkit.getOfflinePlayers()) {
                UUID uuid = account.getUniqueId();
                String name = account.getName();
                everyone.add(new PlayerRef(uuid, name == null ? uuid.toString() : name));
            }
            scheduler.async(() -> {
                imports.startedAll(actor, everyone.size());
                next(actor, everyone, 0, new AtomicInteger());
            });
        });
    }

    /** The join-time import, called on the joining player's region thread. */
    public void importOnJoin(Player player) {
        Duration played = VanillaPlaytime.of(player);
        PlayerRef subject = BukkitRefs.toRef(player);
        scheduler.async(() -> imports.importOnJoin(subject, played));
    }

    private void next(PlayerRef actor, List<PlayerRef> everyone, int index, AtomicInteger credited) {
        if (index == everyone.size()) {
            imports.finishedAll(actor, credited.get(), everyone.size());
            return;
        }
        PlayerRef subject = everyone.get(index);
        vanilla.read(subject, played -> {
            try {
                if (!imports.credit(subject, played).isZero()) {
                    credited.incrementAndGet();
                }
            } catch (RuntimeException failed) {
                log.warn("event=playtime_import_failed player={} reason={}", subject.uuid(), failed.toString());
            }
            next(actor, everyone, index + 1, credited);
        });
    }
}
