package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;

import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link VanishBuffs} implementation: grants a vanished player permanent night vision and a flight allowance, both
 * behind their config toggles, and undoes them on reappear. Every mutation hops to the player's own entity thread
 * through the injected {@link Scheduler} port. A potion effect and the flight flag are per-entity operations valid
 * only on the owning thread under Folia, and an offline player is a silent no-op.
 *
 * <p>Flight is restored to the game-mode default rather than a captured value: creative and spectator fly inherently,
 * so those are left untouched, while survival and adventure lose the granted allowance (and any active flight) on
 * reappear. That is the same convention the staff-mode loadout uses, so a player who toggles vanish while in staff mode
 * or creative is never stranded without flight. Night vision is granted with no ambient tint, particles, or icon so it
 * is invisible to the player beyond the brightened view, and removed outright on clear.
 */
@NullMarked
public final class BukkitVanishBuffs implements VanishBuffs {

    /** A negative duration is Paper's "infinite" potion length: the effect persists until removed on reappear. */
    private static final int INFINITE_TICKS = -1;

    private final Server server;
    private final Scheduler scheduler;
    private final boolean nightVision;
    private final boolean allowFlight;

    public BukkitVanishBuffs(Server server, Scheduler scheduler, boolean nightVision, boolean allowFlight) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.nightVision = nightVision;
        this.allowFlight = allowFlight;
    }

    @Override
    public void apply(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.onEntity(who, () -> onLive(who, this::grant));
    }

    @Override
    public void clear(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.onEntity(who, () -> onLive(who, this::revoke));
    }

    private void grant(Player player) {
        if (nightVision) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, INFINITE_TICKS, 0, false, false));
        }
        if (allowFlight) {
            player.setAllowFlight(true);
        }
    }

    @Override
    public void settle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.onEntity(who, () -> onLive(who, BukkitVanishBuffs::takeBackNightVision));
    }

    private void revoke(Player player) {
        // Whatever the toggle says now: a reload that turned night vision off must still take back the one it
        // granted before, and only that one.
        takeBackNightVision(player);
        if (allowFlight && !fliesByGameMode(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /**
     * Remove the night vision a vanish granted, recognised by what only a vanish writes: infinite, level one, and no
     * ambient tint, particles or icon. A potion the player drank has a length and shows itself, so it stays.
     */
    private static void takeBackNightVision(Player player) {
        PotionEffect held = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (held != null
                && held.isInfinite()
                && held.getAmplifier() == 0
                && !held.isAmbient()
                && !held.hasParticles()
                && !held.hasIcon()) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    /** Whether {@code player}'s game mode grants flight on its own, so the vanish allowance must not be stripped. */
    private static boolean fliesByGameMode(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private void onLive(PlayerRef who, java.util.function.Consumer<Player> action) {
        Player player = server.getPlayer(who.uuid());
        if (player != null && player.isOnline()) {
            action.accept(player);
        }
    }
}
