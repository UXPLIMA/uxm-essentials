package com.uxplima.uxmessentials.warps.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;

/** Hand-written fakes for the warps ports, shared by the warp use tests. */
final class WarpFakes {

    /** A warmup that has already counted down. */
    private static final Warmups.WarmupHandle DONE = new Warmups.WarmupHandle() {
        @Override
        public void cancel() {}

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    private WarpFakes() {}

    /**
     * The teleport engine a backend runs, over {@code cooldowns} (shared between backends as a player's cooldowns
     * follow them). Its launches and arrivals always land; nothing warms up.
     */
    static TeleportEngine engine(
            Cooldowns cooldowns, RecordingNotifier notifier, java.util.function.BooleanSupplier tagged) {
        return new TeleportEngine(
                cooldowns,
                (who, kind, onComplete, onCancel) -> {
                    onComplete.run();
                    return DONE;
                },
                (who, destination, kind) -> {},
                notifier.notifier(),
                new RecordingEvents(),
                new TeleportSettings(new Defaults()),
                who -> false,
                who -> tagged.getAsBoolean());
    }

    /** The warps table every backend shares. */
    static final class Warps implements WarpRepository {

        private final Map<String, Warp> byName = new HashMap<>();

        void put(Warp warp) {
            byName.put(warp.name().value(), warp);
        }

        long visitors(WarpName name) {
            return find(name).map(Warp::visitors).orElse(0L);
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            put(warp);
        }

        @Override
        public void delete(WarpName name) {
            byName.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0;
        }
    }

    /** The player's wallet, with every debit and credit counted. */
    static final class Purse implements WarpEconomy {

        BigDecimal wallet = BigDecimal.ZERO;
        int charges;
        int refunds;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return wallet.compareTo(amount) >= 0;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            if (!canAfford(who, amount, currencyId)) {
                return false;
            }
            wallet = wallet.subtract(amount);
            charges++;
            return true;
        }

        @Override
        public boolean refund(PlayerRef who, BigDecimal amount, String currencyId) {
            wallet = wallet.add(amount);
            refunds++;
            return true;
        }
    }

    /**
     * Records every move. Without an engine it admits every use and lands every arrival unless told to refuse;
     * given one, it asks and moves through that engine, as the teleport adapter does.
     */
    static final class Moves implements WarpTeleporter {

        final List<String> made = new ArrayList<>();
        boolean refuses;

        @Nullable TeleportEngine engine;

        @Override
        public boolean admits(PlayerRef who, Warp warp) {
            TeleportEngine through = engine;
            return through == null
                    || through.gateHop(who, destinationOf(warp), TeleportKind.WARP)
                            .isOk();
        }

        @Override
        public void teleportTo(PlayerRef who, Warp warp) {
            TeleportEngine through = engine;
            if (through == null) {
                made.add(who.name() + "->" + warp.name().value());
                return;
            }
            if (through.launch(who, destinationOf(warp), TeleportKind.WARP).isOk()) {
                made.add(who.name() + "->" + warp.name().value());
            }
        }

        @Override
        public void arrive(PlayerRef who, Warp warp, Runnable onLanded, Runnable onMissed) {
            if (refuses) {
                onMissed.run();
                return;
            }
            Runnable landed = () -> {
                made.add(who.name() + "->" + warp.name().value());
                onLanded.run();
            };
            TeleportEngine through = engine;
            if (through == null) {
                landed.run();
                return;
            }
            if (through.arriveImmediately(who, destinationOf(warp), TeleportKind.WARP, landed, onMissed)
                    .isErr()) {
                onMissed.run();
            }
        }

        private static Destination destinationOf(Warp warp) {
            Optional<Duration> cooldown =
                    warp.cooldownOverrideSeconds().map(seconds -> Duration.ofMillis((long) (seconds * 1000)));
            return Destination.at(warp.location(), Optional.empty(), cooldown);
        }
    }

    /** Teleport settings at their defaults. */
    static final class Defaults implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
