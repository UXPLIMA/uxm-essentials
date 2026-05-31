package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PlayerEffects} implementation: the apply-once and live-only verbs that act on the live player
 * without changing the persisted snapshot — heal, feed, extinguish, suicide, night-vision, and the per-player
 * time/weather overrides. Each resolves the live {@code Player} and runs on the player's owning region/entity
 * thread through the injected {@link Scheduler} port; an offline target is a silent no-op.
 *
 * <p>{@link #toggleNightVision} reports the resulting on/off state synchronously off the snapshot the call
 * captures so the use case can render the right confirmation, while the effect mutation itself is hopped to
 * the entity thread.
 */
@NullMarked
public final class BukkitPlayerEffects implements PlayerEffects {

    private static final long INFINITE_TICKS = -1L;

    private final Scheduler scheduler;

    public BukkitPlayerEffects(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void heal(PlayerRef who, boolean clearEffects) {
        onEntity(who, player -> {
            double max = maxHealth(player);
            player.setHealth(max);
            player.setFireTicks(0);
            if (clearEffects) {
                player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            }
        });
    }

    @Override
    public void feed(PlayerRef who) {
        onEntity(who, player -> {
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setExhaustion(0.0f);
        });
    }

    @Override
    public void extinguish(PlayerRef who) {
        onEntity(who, player -> player.setFireTicks(0));
    }

    @Override
    public void kill(PlayerRef who) {
        onEntity(who, player -> player.setHealth(0.0));
    }

    @Override
    public boolean toggleNightVision(PlayerRef who) {
        Player snapshot = Bukkit.getPlayer(who.uuid());
        boolean willEnable = snapshot == null || !snapshot.hasPotionEffect(PotionEffectType.NIGHT_VISION);
        onEntity(who, player -> {
            if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            } else {
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.NIGHT_VISION, (int) INFINITE_TICKS, 0, false, false));
            }
        });
        return willEnable;
    }

    @Override
    public void applyTime(PlayerRef who, PersonalTime time) {
        onEntity(who, player -> {
            if (time.reset()) {
                player.resetPlayerTime();
            } else {
                player.setPlayerTime(time.ticks(), false);
            }
        });
    }

    @Override
    public void applyWeather(PlayerRef who, PersonalWeather weather) {
        onEntity(who, player -> {
            switch (weather) {
                case CLEAR -> player.setPlayerWeather(WeatherType.CLEAR);
                case RAIN -> player.setPlayerWeather(WeatherType.DOWNFALL);
                case RESET -> player.resetPlayerWeather();
            }
        });
    }

    private void onEntity(PlayerRef who, java.util.function.Consumer<Player> action) {
        Objects.requireNonNull(who, "who");
        scheduler.onEntity(who, () -> {
            Player player = Bukkit.getPlayer(who.uuid());
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        });
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}
