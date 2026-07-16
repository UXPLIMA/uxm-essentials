package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.communication.domain.DeathCause;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping from a live Bukkit death to the communication context's own {@link DeathCause}
 * vocabulary and to the {@code {killer_weapon}} token, so the domain and the use case never see a Bukkit type. The
 * cause is decided by <em>who</em> struck the final blow before <em>how</em>: a player killer is {@link
 * DeathCause#PVP}, a creature striking directly is {@link DeathCause#MOB}, a thrown/fired thing is {@link
 * DeathCause#PROJECTILE}, and everything else maps from the vanilla {@code DamageCause}. An untranslated cause
 * degrades to {@link DeathCause#OTHER}, which the policy table resolves through the default death policy.
 *
 * <p>The weapon string is the killer's held item rendered for a template: its custom display name flattened to
 * plain text when the item was renamed (so no default tooltip italic and no smuggled MiniMessage tag reaches the
 * parsed template), otherwise a readable material name such as {@code Diamond Sword}. An empty hand or the absence
 * of a killer yields the empty string, so the token never renders raw.
 */
@NullMarked
final class DeathCauses {

    private DeathCauses() {}

    /** The domain cause of {@code event}, classified from its killer and last damage cause. */
    static DeathCause of(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        return classify(victim.getKiller(), victim.getLastDamageCause());
    }

    /**
     * Classify a death from the player who dealt the killing blow (non-null only for a player killer) and the last
     * damage event. A player killer is always {@link DeathCause#PVP}; otherwise a by-entity blow splits into {@link
     * DeathCause#PROJECTILE} (a thrown/fired projectile) and {@link DeathCause#MOB} (a creature striking directly),
     * and any other damage maps from its {@code DamageCause}. A missing damage event is {@link DeathCause#OTHER}.
     */
    static DeathCause classify(@Nullable Player killer, @Nullable EntityDamageEvent lastDamage) {
        if (killer != null) {
            return DeathCause.PVP;
        }
        if (lastDamage == null) {
            return DeathCause.OTHER;
        }
        if (lastDamage instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile) {
                return DeathCause.PROJECTILE;
            }
            if (damager instanceof LivingEntity) {
                return DeathCause.MOB;
            }
        }
        return fromDamageCause(lastDamage.getCause());
    }

    /** Map a vanilla {@code DamageCause} to a domain {@link DeathCause}; an unmapped cause is {@link DeathCause#OTHER}. */
    static DeathCause fromDamageCause(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FALL -> DeathCause.FALL;
            case FIRE, FIRE_TICK, MELTING, CAMPFIRE -> DeathCause.FIRE;
            case LAVA -> DeathCause.LAVA;
            case DROWNING -> DeathCause.DROWNING;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> DeathCause.EXPLOSION;
            case VOID, WORLD_BORDER -> DeathCause.VOID;
            case LIGHTNING -> DeathCause.LIGHTNING;
            case SUFFOCATION, CRAMMING -> DeathCause.SUFFOCATION;
            case STARVATION -> DeathCause.STARVATION;
            case POISON -> DeathCause.POISON;
            case MAGIC -> DeathCause.MAGIC;
            case WITHER -> DeathCause.WITHER;
            case FALLING_BLOCK -> DeathCause.FALLING_BLOCK;
            case THORNS -> DeathCause.THORNS;
            case DRAGON_BREATH -> DeathCause.DRAGON_BREATH;
            case FLY_INTO_WALL -> DeathCause.FLY_INTO_WALL;
            case HOT_FLOOR -> DeathCause.HOT_FLOOR;
            case CONTACT, DRYOUT -> DeathCause.CONTACT;
            case FREEZE -> DeathCause.FREEZE;
            case SONIC_BOOM -> DeathCause.SONIC_BOOM;
            case PROJECTILE -> DeathCause.PROJECTILE;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> DeathCause.MOB;
            default -> DeathCause.OTHER;
        };
    }

    /**
     * The {@code {killer_weapon}} value for {@code item}: the renamed item's custom display name as plain text, else
     * a readable material name. A null or empty-hand item yields the empty string.
     */
    static String weaponName(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                return Text.plain(name);
            }
        }
        return readableMaterial(item.getType());
    }

    /** A material key rendered for chat: {@code DIAMOND_SWORD} becomes {@code Diamond Sword}. */
    static String readableMaterial(Material material) {
        String key = material.name();
        StringBuilder readable = new StringBuilder(key.length());
        boolean startOfWord = true;
        for (int i = 0; i < key.length(); i++) {
            char letter = key.charAt(i);
            if (letter == '_') {
                readable.append(' ');
                startOfWord = true;
            } else if (startOfWord) {
                readable.append(Character.toUpperCase(letter));
                startOfWord = false;
            } else {
                readable.append(Character.toLowerCase(letter));
            }
        }
        return readable.toString();
    }
}
