package com.uxplima.uxmessentials.migration.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.kits.application.LocalKitClaims;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The caches the running modules serve players from, collected as each module wires, so an import writes through
 * the very caches {@code /warp}, {@code /hologram}, {@code /worlds} and the rest answer from. Those caches load once
 * and never read the database again, so an importer with caches of its own would fill the database while every
 * module kept serving the set it loaded at enable: an imported row would stay invisible until a restart, and a
 * {@code /setwarp} of an imported name would create the warp afresh over it.
 *
 * <p>A module registers the cache that sits under its cross-server broadcaster, not the broadcaster itself: an
 * import writes thousands of rows, and one frame per row would flood the bus queue and push out the frames live
 * commands send. {@link AnnouncingRecordWriter} tells peers what changed once the import is done instead.
 *
 * <p>Each registration returns the action that withdraws it, which the module hands to its resources, so a module
 * that fails after registering does not leave the importer writing through a torn-down cache or renderer. A module
 * that is not running leaves its slot empty, and each {@code ...Or} accessor then builds the fallback store it is
 * given, so a running module never gets a second cache built beside its own.
 *
 * <p>Filled and withdrawn on the enable thread before the importer is built, so it needs no locking.
 */
@NullMarked
public final class ImportTargets {

    private final Logger log;
    private @Nullable HomeRepository homes;
    private @Nullable WalletRepository wallets;
    private @Nullable WarpRepository warps;
    private @Nullable KitRepository kits;
    private @Nullable LocalKitClaims kitClaims;
    private @Nullable HologramRepository holograms;
    private @Nullable WorldRepository worlds;
    private @Nullable PlayerWarpRepository playerWarps;

    /** @param log where a hologram that was imported but could not be drawn is reported */
    public ImportTargets(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The homes module's cache, the one {@code /home} reads. */
    public Runnable homes(HomeRepository live) {
        HomeRepository registered = Objects.requireNonNull(live, "live");
        this.homes = registered;
        return () -> {
            if (homes == registered) {
                homes = null;
            }
        };
    }

    /** The economy module's wallet cache, the one the provider reads balances through. */
    public Runnable wallets(WalletRepository live) {
        WalletRepository registered = Objects.requireNonNull(live, "live");
        this.wallets = registered;
        return () -> {
            if (wallets == registered) {
                wallets = null;
            }
        };
    }

    /** The warps module's cache, the one {@code /warp} and {@code /setwarp} read. */
    public Runnable warps(WarpRepository live) {
        WarpRepository registered = Objects.requireNonNull(live, "live");
        this.warps = registered;
        return () -> {
            if (warps == registered) {
                warps = null;
            }
        };
    }

    /** The kits module's catalog, the one {@code /kit} reads. */
    public Runnable kits(KitRepository live) {
        KitRepository registered = Objects.requireNonNull(live, "live");
        this.kits = registered;
        return () -> {
            if (kits == registered) {
                kits = null;
            }
        };
    }

    /**
     * The kits module's one-time claim store on a single server. A claim the import records for a player who is
     * online is stamped through it at once, since their stamps are read from the database only as they join.
     */
    public Runnable kitClaims(LocalKitClaims live) {
        LocalKitClaims registered = Objects.requireNonNull(live, "live");
        this.kitClaims = registered;
        return () -> {
            if (kitClaims == registered) {
                kitClaims = null;
            }
        };
    }

    /**
     * The holograms module's cache and its renderer. A stored hologram is drawn only when something renders it, so
     * an imported one is rendered as it is saved, the way {@code /hologram create} draws the one it makes.
     */
    public Runnable holograms(HologramRepository live, HologramView renderer) {
        HologramRepository registered = new RenderingHologramRepository(
                Objects.requireNonNull(live, "live"), Objects.requireNonNull(renderer, "renderer"), log);
        this.holograms = registered;
        return () -> {
            if (holograms == registered) {
                holograms = null;
            }
        };
    }

    /** The worlds module's register, the one {@code /worlds} reads. */
    public Runnable worlds(WorldRepository live) {
        WorldRepository registered = Objects.requireNonNull(live, "live");
        this.worlds = registered;
        return () -> {
            if (worlds == registered) {
                worlds = null;
            }
        };
    }

    /** The player-warps module's cache, the one {@code /pwarp} reads. */
    public Runnable playerWarps(PlayerWarpRepository live) {
        PlayerWarpRepository registered = Objects.requireNonNull(live, "live");
        this.playerWarps = registered;
        return () -> {
            if (playerWarps == registered) {
                playerWarps = null;
            }
        };
    }

    /** The homes module's cache, or {@code store} when the module is not running. */
    public HomeRepository homesOr(Supplier<HomeRepository> store) {
        return orElse(homes, store);
    }

    /** The economy module's wallet cache, or {@code store} when the module is not running. */
    public WalletRepository walletsOr(Supplier<WalletRepository> store) {
        return orElse(wallets, store);
    }

    /** The warps module's cache, or {@code store} when the module is not running. */
    public WarpRepository warpsOr(Supplier<WarpRepository> store) {
        return orElse(warps, store);
    }

    /** The kits module's catalog, or {@code store} when the module is not running. */
    public KitRepository kitsOr(Supplier<KitRepository> store) {
        return orElse(kits, store);
    }

    /** The kits module's single-server claim store; empty on a network or when the kits module is off. */
    public Optional<LocalKitClaims> kitClaims() {
        return Optional.ofNullable(kitClaims);
    }

    /** The holograms module's cache, rendering what it saves, or {@code store} when the module is off. */
    public HologramRepository hologramsOr(Supplier<HologramRepository> store) {
        return orElse(holograms, store);
    }

    /** The worlds module's register, or {@code store} when the module is not running. */
    public WorldRepository worldsOr(Supplier<WorldRepository> store) {
        return orElse(worlds, store);
    }

    /** The player-warps module's cache, or {@code store} when the module is not running. */
    public PlayerWarpRepository playerWarpsOr(Supplier<PlayerWarpRepository> store) {
        return orElse(playerWarps, store);
    }

    private static <T> T orElse(@Nullable T live, Supplier<T> store) {
        Objects.requireNonNull(store, "store");
        return live != null ? live : Objects.requireNonNull(store.get(), "store");
    }
}
