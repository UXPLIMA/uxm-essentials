package com.uxplima.uxmessentials.migration.adapter;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The repositories the running modules serve players from, collected as each module wires, so an import writes
 * through the very caches {@code /warp}, {@code /hologram}, {@code /worlds} and the rest answer from. Those caches
 * load once and never read the database again, so an importer with caches of its own would fill the database
 * while every module kept serving the set it loaded at enable: an imported row would stay invisible until a
 * restart, and a {@code /setwarp} of an imported name would create the warp afresh over it.
 *
 * <p>Each live repository is the module's own decorated one, so a write through it also carries the module's
 * cross-server announcement and peers drop or reload their copy exactly as they do for a command. A module that is
 * not running leaves its slot empty, and each {@code ...Or} accessor then builds the fallback store it is given,
 * so a running module never gets a second cache built beside its own.
 *
 * <p>Filled on the enable thread before the importer is built and only read afterwards, so it needs no locking.
 */
@NullMarked
public final class ImportTargets {

    private @Nullable HomeRepository homes;
    private @Nullable WalletRepository wallets;
    private @Nullable WarpRepository warps;
    private @Nullable KitRepository kits;
    private @Nullable HologramRepository holograms;
    private @Nullable WorldRepository worlds;
    private @Nullable PlayerWarpRepository playerWarps;

    /** The homes module's repository, the one {@code /home} reads. */
    public void homes(HomeRepository live) {
        this.homes = Objects.requireNonNull(live, "live");
    }

    /** The economy module's wallet repository, the one the provider reads balances through. */
    public void wallets(WalletRepository live) {
        this.wallets = Objects.requireNonNull(live, "live");
    }

    /** The warps module's repository, the one {@code /warp} and {@code /setwarp} read. */
    public void warps(WarpRepository live) {
        this.warps = Objects.requireNonNull(live, "live");
    }

    /** The kits module's catalog, the one {@code /kit} reads. */
    public void kits(KitRepository live) {
        this.kits = Objects.requireNonNull(live, "live");
    }

    /**
     * The holograms module's repository and its renderer. A stored hologram is drawn only when something renders
     * it, so an imported one is rendered as it is saved, the way {@code /hologram create} draws the one it makes.
     */
    public void holograms(HologramRepository live, HologramView renderer) {
        this.holograms = new RenderingHologramRepository(
                Objects.requireNonNull(live, "live"), Objects.requireNonNull(renderer, "renderer"));
    }

    /** The worlds module's register, the one {@code /worlds} reads. */
    public void worlds(WorldRepository live) {
        this.worlds = Objects.requireNonNull(live, "live");
    }

    /** The player-warps module's repository, the one {@code /pwarp} reads. */
    public void playerWarps(PlayerWarpRepository live) {
        this.playerWarps = Objects.requireNonNull(live, "live");
    }

    /** The homes module's repository, or {@code store} when the module is not running. */
    public HomeRepository homesOr(Supplier<HomeRepository> store) {
        return orElse(homes, store);
    }

    /** The economy module's wallet repository, or {@code store} when the module is not running. */
    public WalletRepository walletsOr(Supplier<WalletRepository> store) {
        return orElse(wallets, store);
    }

    /** The warps module's repository, or {@code store} when the module is not running. */
    public WarpRepository warpsOr(Supplier<WarpRepository> store) {
        return orElse(warps, store);
    }

    /** The kits module's catalog, or {@code store} when the module is not running. */
    public KitRepository kitsOr(Supplier<KitRepository> store) {
        return orElse(kits, store);
    }

    /** The holograms module's repository, rendering what it saves, or {@code store} when the module is off. */
    public HologramRepository hologramsOr(Supplier<HologramRepository> store) {
        return orElse(holograms, store);
    }

    /** The worlds module's register, or {@code store} when the module is not running. */
    public WorldRepository worldsOr(Supplier<WorldRepository> store) {
        return orElse(worlds, store);
    }

    /** The player-warps module's repository, or {@code store} when the module is not running. */
    public PlayerWarpRepository playerWarpsOr(Supplier<PlayerWarpRepository> store) {
        return orElse(playerWarps, store);
    }

    private static <T> T orElse(@Nullable T live, Supplier<T> store) {
        Objects.requireNonNull(store, "store");
        return live != null ? live : Objects.requireNonNull(store.get(), "store");
    }
}
