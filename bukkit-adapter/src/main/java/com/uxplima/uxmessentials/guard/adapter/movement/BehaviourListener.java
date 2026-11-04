package com.uxplima.uxmessentials.guard.adapter.movement;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.engine.RegionSet;

/**
 * Behaviours enforced as things happen rather than on entering: invincibility, keeping inventory and experience,
 * gliding, blocked effects, flight toggles, mob spawning by type and reason, and blocked or allowed commands.
 */
public final class BehaviourListener implements Listener {

    private final GuardRuntime guard;
    private final PlayerTracks tracks;

    public BehaviourListener(GuardRuntime guard, PlayerTracks tracks) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.tracks = Objects.requireNonNull(tracks, "tracks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && on(player, BehaviourKey.INVINCIBLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (on(player, BehaviourKey.KEEP_INVENTORY)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
        }
        if (on(player, BehaviourKey.KEEP_EXP)) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding()
                && event.getEntity() instanceof Player player
                && tracks.behaviour(player, BehaviourKey.GLIDE)
                        .filter("deny"::equals)
                        .isPresent()
                && !guard.bypassOn(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (event.isFlying()
                && tracks.behaviour(player, BehaviourKey.FLY)
                        .filter("deny"::equals)
                        .isPresent()
                && !guard.bypassOn(player)
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        PotionEffect added = event.getNewEffect();
        if (added == null || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Optional<String> blocked = tracks.behaviour(player, BehaviourKey.EFFECTS_BLOCK);
        if (blocked.isPresent()
                && listed(blocked.get(), added.getType().getKey().getKey())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPreSpawn(PreCreatureSpawnEvent event) {
        if (denySpawn(event.getSpawnLocation(), event.getType(), event.getReason())) {
            event.setCancelled(true);
            event.setShouldAbortSpawn(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (denySpawn(event.getLocation(), event.getEntityType(), event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (guard.bypassOn(player)) {
            return;
        }
        Optional<String> deny = tracks.behaviour(player, BehaviourKey.COMMANDS_DENY);
        Optional<String> allow = tracks.behaviour(player, BehaviourKey.COMMANDS_ALLOW);
        if (deny.isEmpty() && allow.isEmpty()) {
            return;
        }
        String line = event.getMessage().substring(1).strip().toLowerCase(Locale.ROOT);
        int space = line.indexOf(' ');
        String typed = space < 0 ? line : line.substring(0, space);
        String args = space < 0 ? "" : line.substring(space + 1).strip();
        Function<String, Set<String>> namesOf = word -> names(player, word);
        if (CommandWords.same(typed, "guard", namesOf)) {
            return;
        }
        String root = CommandWords.bare(typed);
        boolean denied = deny.isPresent() && CommandWords.matches(deny.get(), typed, args, namesOf);
        if (!denied && allow.isPresent() && !CommandWords.matches(allow.get(), typed, args, namesOf)) {
            denied = true;
        }
        if (denied) {
            event.setCancelled(true);
            guard.send(
                    player,
                    GuardMessageKey.DENY_COMMAND,
                    Map.of("command", root),
                    guard.config().denyChannel());
        }
    }

    private boolean on(Player player, BehaviourKey key) {
        return tracks.behaviour(player, key).map(Boolean::parseBoolean).orElse(false);
    }

    private boolean denySpawn(Location at, EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        RegionSet set = guard.at(at);
        if (set == null) {
            return false;
        }
        Optional<String> rule = set.behaviour(BehaviourKey.SPAWN_DENY);
        if (rule.isEmpty()) {
            return false;
        }
        String name = type.getKey().getKey();
        String why = reason.name().toLowerCase(Locale.ROOT);
        boolean monster = type.getEntityClass() != null && Enemy.class.isAssignableFrom(type.getEntityClass());
        for (String entry : rule.get().split(",", -1)) {
            String[] parts = entry.split(":", 2);
            String what = parts[0];
            boolean typeMatches = what.equals("*")
                    || what.equals(name)
                    || (what.equals("monster") && monster)
                    || (what.equals("animal") && !monster);
            boolean reasonMatches = parts.length < 2 || parts[1].equals("*") || parts[1].equals(why);
            if (typeMatches && reasonMatches) {
                return true;
            }
        }
        return false;
    }

    /** Every name a command word answers to: the word itself, and the name and aliases of its command entry. */
    private static Set<String> names(Player player, String word) {
        Set<String> names = new HashSet<>();
        names.add(CommandWords.bare(word));
        Command command = player.getServer().getCommandMap().getCommand(word);
        if (command != null) {
            names.add(CommandWords.bare(command.getName().toLowerCase(Locale.ROOT)));
            names.add(CommandWords.bare(command.getLabel().toLowerCase(Locale.ROOT)));
            for (String alias : command.getAliases()) {
                names.add(CommandWords.bare(alias.toLowerCase(Locale.ROOT)));
            }
        }
        return names;
    }

    private static boolean listed(String list, String name) {
        for (String entry : list.split(",", -1)) {
            if (entry.strip().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
