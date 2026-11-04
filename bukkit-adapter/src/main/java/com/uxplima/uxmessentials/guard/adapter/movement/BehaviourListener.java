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
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
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

    /** The guard command under this plugin's namespace, which no other plugin can claim. */
    private static final String OWN_GUARD = "uxmessentials:guard";

    /** The owner given to the server's own commands, which no plugin registers. */
    private static final String SERVER_OWNER = "(server)";

    private final GuardRuntime guard;
    private final PlayerTracks tracks;

    public BehaviourListener(GuardRuntime guard, PlayerTracks tracks) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.tracks = Objects.requireNonNull(tracks, "tracks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && (on(player, BehaviourKey.INVINCIBLE)
                        || (event.getCause() == EntityDamageEvent.DamageCause.FALL
                                && off(player, BehaviourKey.FALL_DAMAGE)))) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Firework
                && offAt(event.getEntity().getLocation(), BehaviourKey.FIREWORK_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getFoodLevel() < player.getFoodLevel()
                && off(player, BehaviourKey.HUNGER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExp(BlockBreakEvent event) {
        if (event.getExpToDrop() > 0 && offAt(event.getBlock().getLocation(), BehaviourKey.EXP_DROPS)) {
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobExp(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)
                && event.getDroppedExp() > 0
                && offAt(event.getEntity().getLocation(), BehaviourKey.EXP_DROPS)) {
            event.setDroppedExp(0);
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
        Function<String, CommandWords.Named> lookup = word -> named(player, word);
        // The guard's own command always runs, so staff can fix a region they stand in. The namespaced label is
        // this plugin's whatever else claims the bare words.
        CommandWords.Named own = lookup.apply(OWN_GUARD);
        if (own.owner() != null && CommandWords.same(lookup.apply(typed), own)) {
            return;
        }
        String root = CommandWords.bare(typed);
        boolean denied = deny.isPresent() && CommandWords.denies(deny.get(), typed, args, lookup);
        if (!denied && allow.isPresent() && !CommandWords.allows(allow.get(), typed, args, lookup)) {
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

    /** True when the region the player stands in turns {@code key} off; unset keeps the game's own behaviour. */
    private boolean off(Player player, BehaviourKey key) {
        return tracks.behaviour(player, key).filter("false"::equals).isPresent();
    }

    /** True when the region at {@code at} turns {@code key} off, for what happens to blocks and mobs. */
    private boolean offAt(Location at, BehaviourKey key) {
        RegionSet set = guard.at(at);
        return set != null && set.behaviour(key).filter("false"::equals).isPresent();
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

    /** How the server knows a command word: who registered it, and every name of its entry. */
    private static CommandWords.Named named(Player player, String word) {
        Set<String> names = new HashSet<>();
        names.add(CommandWords.bare(word));
        Command command = player.getServer().getCommandMap().getCommand(word);
        if (command == null) {
            return new CommandWords.Named(null, names);
        }
        names.add(CommandWords.bare(command.getName().toLowerCase(Locale.ROOT)));
        names.add(CommandWords.bare(command.getLabel().toLowerCase(Locale.ROOT)));
        for (String alias : command.getAliases()) {
            names.add(CommandWords.bare(alias.toLowerCase(Locale.ROOT)));
        }
        String owner = command instanceof PluginIdentifiableCommand owned
                ? owned.getPlugin().getName()
                : SERVER_OWNER;
        return new CommandWords.Named(owner, names);
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
