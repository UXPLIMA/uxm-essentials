package com.uxplima.uxmessentials.testing;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

import com.google.common.base.Function;

/**
 * Builds the damage event a listener test feeds to its subject. Paper marked the short constructors for
 * removal in 26.2, leaving the modifier-map one as the only form that survives, so the maps every caller
 * would otherwise repeat are assembled here.
 */
public final class DamageEvents {

    private DamageEvents() {}

    /** A damage event carrying {@code amount} as its base damage and nothing else. */
    // DamageModifier is deprecated alongside the constructors that read it, and the surviving constructor
    // still demands it, so a test that fabricates damage has no undeprecated way to name the base amount.
    @SuppressWarnings("deprecation")
    public static EntityDamageEvent of(Entity entity, DamageCause cause, DamageSource source, double amount) {
        Map<DamageModifier, Double> modifiers = new EnumMap<>(DamageModifier.class);
        modifiers.put(DamageModifier.BASE, amount);
        Map<DamageModifier, Function<? super Double, Double>> functions = new EnumMap<>(DamageModifier.class);
        functions.put(DamageModifier.BASE, damage -> -0.0);
        return new EntityDamageEvent(entity, cause, source, modifiers, functions);
    }
}
