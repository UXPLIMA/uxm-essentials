package com.uxplima.uxmessentials.guard.adapter.movement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.BuiltInActions;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.engine.RegionSet;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ClickActionValueCheck;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.jspecify.annotations.Nullable;

/**
 * Where each online player stands in region terms, and the behaviours applied to them.
 *
 * <p>A player's state changes only when the region set under them changes: on that change the exit and enter rules
 * are checked, the behaviours of the old set are taken off and the new ones put on, messages are shown and the
 * enter and exit actions run. What a behaviour overrides (flight, speeds, game mode, player time and weather) is
 * remembered the first time it is overridden and put back when no region overrides it any more, and on quit.
 */
public final class PlayerTracks {

    private final GuardRuntime guard;
    private final ClickActionRunner actions;
    private final Map<UUID, Track> tracks = new ConcurrentHashMap<>();
    private final Map<String, List<ClickAction>> parsedActions = new ConcurrentHashMap<>();

    private final Function<UUID, Optional<Boolean>> heldGod;

    /**
     * @param heldGod whether essentials god mode is on for a player, when the playerstate module answers; leaving a
     *     region that set god puts that back rather than what the player had on the way in, which a toggle inside
     *     may have changed
     */
    public PlayerTracks(GuardRuntime guard, ClickActionRunner actions, Function<UUID, Optional<Boolean>> heldGod) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.heldGod = Objects.requireNonNull(heldGod, "heldGod");
    }

    /** One player's region state and the values behaviours took over. */
    static final class Track {
        @Nullable RegionSet set;

        @Nullable Location safe;

        long lastInterval;
        long lastHeal;
        long lastFeed;
        long lastReminder;

        @Nullable Boolean allowFlight;

        @Nullable Boolean invulnerable;

        @Nullable Float walkSpeed;

        @Nullable Float flySpeed;

        @Nullable GameMode gameMode;

        boolean timeSet;
        boolean weatherSet;
        Set<PotionEffectType> given = new HashSet<>();

        @Nullable BossBar bar;
    }

    /** The behaviours in force for a player now. */
    public Behaviours behaviours(Player player) {
        Track track = tracks.get(player.getUniqueId());
        RegionSet set = track == null ? null : track.set;
        return set == null ? Behaviours.empty() : set.behaviours();
    }

    /** One behaviour in force for a player now. */
    public Optional<String> behaviour(Player player, BehaviourKey key) {
        return behaviours(player).get(key);
    }

    /**
     * The player moved from {@code from} to {@code to} by walking ({@code teleport} false) or teleporting. Answers
     * where they may end up: {@code to} when allowed, a safe place to push them back to, or null to cancel.
     */
    public @Nullable Location moved(Player player, Location from, Location to, boolean teleport) {
        Track track = tracks.computeIfAbsent(player.getUniqueId(), id -> new Track());
        RegionSet next = guard.at(to);
        RegionSet previous = track.set;
        if (previous == null && from.getWorld() != null && from.getWorld().equals(to.getWorld())) {
            previous = guard.at(from);
        }
        if (sameRegions(previous, next)) {
            track.set = next;
            track.safe = to;
            return voidCheck(player, to, next);
        }
        List<Region> left = minus(previous, next);
        List<Region> entered = minus(next, previous);
        if (!left.isEmpty() || teleport) {
            if (!guard.allow(
                            player,
                            teleport ? BuiltInActions.MOVE_TELEPORT_OUT : BuiltInActions.MOVE_EXIT,
                            from.getWorld(),
                            from.getBlockX(),
                            from.getBlockY(),
                            from.getBlockZ(),
                            false)
                    || (!left.isEmpty()
                            && !guard.allow(
                                    player,
                                    BuiltInActions.MOVE_EXIT,
                                    from.getWorld(),
                                    from.getBlockX(),
                                    from.getBlockY(),
                                    from.getBlockZ(),
                                    false))) {
                refuse(player, previous, BehaviourKey.EXIT_DENY_MESSAGE, GuardMessageKey.DENY_EXIT);
                return pushBack(track, from, previous, teleport);
            }
        }
        if (!entered.isEmpty() || teleport) {
            boolean teleportAllowed = !teleport
                    || guard.allow(
                            player,
                            BuiltInActions.MOVE_TELEPORT_IN,
                            to.getWorld(),
                            to.getBlockX(),
                            to.getBlockY(),
                            to.getBlockZ(),
                            false);
            boolean enterAllowed = entered.isEmpty()
                    || guard.allow(
                            player,
                            BuiltInActions.MOVE_ENTER,
                            to.getWorld(),
                            to.getBlockX(),
                            to.getBlockY(),
                            to.getBlockZ(),
                            false);
            if (!teleportAllowed || !enterAllowed) {
                refuse(player, next, BehaviourKey.ENTER_DENY_MESSAGE, GuardMessageKey.DENY_ENTER);
                return pushBack(track, from, next, teleport);
            }
        }
        Behaviours before = previous == null ? Behaviours.empty() : previous.behaviours();
        Behaviours after = next == null ? Behaviours.empty() : next.behaviours();
        track.set = next;
        track.safe = to;
        transition(player, track, before, after, !left.isEmpty(), !entered.isEmpty());
        return voidCheck(player, to, next);
    }

    /** A player joined or respawned at a location: enter without rule checks. */
    public void placed(Player player, Location at) {
        Track track = tracks.computeIfAbsent(player.getUniqueId(), id -> new Track());
        RegionSet next = guard.at(at);
        Behaviours before = track.set == null ? Behaviours.empty() : track.set.behaviours();
        Behaviours after = next == null ? Behaviours.empty() : next.behaviours();
        boolean changed = !sameRegions(track.set, next);
        track.set = next;
        track.safe = at;
        if (changed) {
            transition(player, track, before, after, false, true);
        }
    }

    /** The regions changed under everyone: re-apply behaviours for each tracked player. Run per player thread. */
    public void refresh(Player player) {
        Location at = player.getLocation();
        if (at == null) {
            return;
        }
        Track track = tracks.computeIfAbsent(player.getUniqueId(), id -> new Track());
        RegionSet next = guard.at(at);
        Behaviours before = track.set == null ? Behaviours.empty() : track.set.behaviours();
        Behaviours after = next == null ? Behaviours.empty() : next.behaviours();
        track.set = next;
        if (!before.equals(after)) {
            transition(player, track, before, after, false, false);
        }
    }

    /** Called every movement.check-ticks on the player's thread: heal, feed, interval actions, bypass reminder, watchdog. */
    public void tick(Player player, long nowMillis) {
        Track track = tracks.get(player.getUniqueId());
        if (track == null) {
            placed(player, Objects.requireNonNull(player.getLocation(), "location"));
            return;
        }
        Location at = player.getLocation();
        Location safe = track.safe;
        if (at != null && safe != null) {
            // Folia skips the move event for some teleports and respawns, and server-side movers fire none: when
            // the player stands in other regions than we last saw, run the entry and exit rules now and put them
            // back if those refuse.
            if (safe.getWorld() == at.getWorld() && !sameRegions(track.set, guard.at(at))) {
                Location result = moved(player, safe, at, false);
                Location back = result == null ? safe : result;
                if (back != at) {
                    var ignored = player.teleportAsync(back);
                }
            } else if (safe.getWorld() != at.getWorld() || safe.distanceSquared(at) > 64) {
                refresh(player);
                track.safe = at;
            }
        }
        Behaviours behaviours = track.set == null ? Behaviours.empty() : track.set.behaviours();
        behaviours.get(BehaviourKey.HEAL).ifPresent(v -> {
            String[] parts = v.split(" ", -1);
            if (nowMillis - track.lastHeal >= Long.parseLong(parts[1]) * 1000L) {
                track.lastHeal = nowMillis;
                double max = Optional.ofNullable(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH))
                        .map(org.bukkit.attribute.AttributeInstance::getValue)
                        .orElse(20.0);
                if (!player.isDead()) {
                    player.setHealth(Math.min(max, player.getHealth() + Double.parseDouble(parts[0])));
                }
            }
        });
        behaviours.get(BehaviourKey.FEED).ifPresent(v -> {
            String[] parts = v.split(" ", -1);
            if (nowMillis - track.lastFeed >= Long.parseLong(parts[1]) * 1000L) {
                track.lastFeed = nowMillis;
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + (int) Double.parseDouble(parts[0])));
                player.setSaturation(Math.min(20f, player.getSaturation() + 1f));
            }
        });
        Optional<String> interval = behaviours.get(BehaviourKey.ACTIONS_INTERVAL);
        if (interval.isPresent()) {
            long every = behaviours
                            .get(BehaviourKey.ACTIONS_INTERVAL_SECONDS)
                            .map(Long::parseLong)
                            .orElse(60L)
                    * 1000L;
            if (nowMillis - track.lastInterval >= every) {
                track.lastInterval = nowMillis;
                actions.run(player, parse(interval.get()), false);
            }
        }
        if (guard.bypassOn(player)
                && nowMillis - track.lastReminder
                        >= guard.config().bypassReminder().toMillis()) {
            track.lastReminder = nowMillis;
            guard.send(player, GuardMessageKey.BYPASS_REMINDER, Map.of(), "actionbar");
        }
    }

    /** A player left: put back everything behaviours changed and forget them. */
    public void left(Player player) {
        Track track = tracks.remove(player.getUniqueId());
        if (track != null) {
            restore(player, track);
        }
    }

    /** Put everyone back, for a disable. */
    public void restoreAll(Iterable<? extends Player> players) {
        for (Player player : players) {
            left(player);
        }
    }

    private void transition(
            Player player, Track track, Behaviours before, Behaviours after, boolean left, boolean entered) {
        if (left) {
            message(player, track, before, BehaviourKey.MESSAGE_EXIT);
            before.get(BehaviourKey.ACTIONS_EXIT).ifPresent(a -> actions.run(player, parse(a), false));
        }
        apply(player, track, after);
        if (entered) {
            message(player, track, after, BehaviourKey.MESSAGE_ENTER);
            after.get(BehaviourKey.ACTIONS_ENTER).ifPresent(a -> actions.run(player, parse(a), false));
            track.lastInterval = System.currentTimeMillis();
        }
    }

    private void apply(Player player, Track track, Behaviours after) {
        boolean exempt = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
        Optional<String> fly = after.get(BehaviourKey.FLY).filter(v -> !v.equals("keep"));
        if (fly.isPresent() && !exempt) {
            if (track.allowFlight == null) {
                track.allowFlight = player.getAllowFlight();
            }
            boolean allow = fly.get().equals("allow");
            player.setAllowFlight(allow);
            if (!allow && player.isFlying()) {
                player.setFlying(false);
            }
        } else if (track.allowFlight != null) {
            player.setAllowFlight(track.allowFlight);
            if (!track.allowFlight && player.isFlying() && !exempt) {
                player.setFlying(false);
            }
            track.allowFlight = null;
        }
        // god = deny switches essentials god mode off inside (an arena), allow switches it on; leaving puts back
        // what the player had.
        Optional<String> god = after.get(BehaviourKey.GOD).filter(v -> !v.equals("keep"));
        if (god.isPresent()) {
            if (track.invulnerable == null) {
                track.invulnerable = invulnerable(player);
            }
            player.setInvulnerable(god.get().equals("allow"));
        } else if (track.invulnerable != null) {
            player.setInvulnerable(heldGod.apply(player.getUniqueId()).orElse(track.invulnerable));
            track.invulnerable = null;
        }
        Optional<Float> walk = after.get(BehaviourKey.WALK_SPEED).map(Float::parseFloat);
        if (walk.isPresent()) {
            if (track.walkSpeed == null) {
                track.walkSpeed = player.getWalkSpeed();
            }
            player.setWalkSpeed(walk.get());
        } else if (track.walkSpeed != null) {
            player.setWalkSpeed(track.walkSpeed);
            track.walkSpeed = null;
        }
        Optional<Float> flySpeed = after.get(BehaviourKey.FLY_SPEED).map(Float::parseFloat);
        if (flySpeed.isPresent()) {
            if (track.flySpeed == null) {
                track.flySpeed = player.getFlySpeed();
            }
            player.setFlySpeed(flySpeed.get());
        } else if (track.flySpeed != null) {
            player.setFlySpeed(track.flySpeed);
            track.flySpeed = null;
        }
        Optional<GameMode> mode =
                after.get(BehaviourKey.GAME_MODE).map(v -> GameMode.valueOf(v.toUpperCase(Locale.ROOT)));
        if (mode.isPresent() && !player.hasPermission("uxmessentials.guard.bypass.gamemode")) {
            if (track.gameMode == null) {
                track.gameMode = player.getGameMode();
            }
            if (player.getGameMode() != mode.get()) {
                player.setGameMode(mode.get());
            }
        } else if (track.gameMode != null) {
            player.setGameMode(track.gameMode);
            track.gameMode = null;
        }
        Optional<String> time = after.get(BehaviourKey.TIME);
        if (time.isPresent()) {
            player.setPlayerTime(Long.parseLong(time.get()), false);
            track.timeSet = true;
        } else if (track.timeSet) {
            player.resetPlayerTime();
            track.timeSet = false;
        }
        Optional<String> weather = after.get(BehaviourKey.WEATHER);
        if (weather.isPresent()) {
            player.setPlayerWeather(weather.get().equals("rain") ? WeatherType.DOWNFALL : WeatherType.CLEAR);
            track.weatherSet = true;
        } else if (track.weatherSet) {
            player.resetPlayerWeather();
            track.weatherSet = false;
        }
        Set<PotionEffectType> wanted = new HashSet<>();
        after.get(BehaviourKey.EFFECTS_GIVE).ifPresent(list -> {
            for (String entry : list.split(",", -1)) {
                String[] parts = entry.split(":", -1);
                PotionEffectType type = effect(parts[0]);
                if (type == null) {
                    continue;
                }
                int amplifier = parts.length > 1 ? parseInt(parts[1], 1) - 1 : 0;
                wanted.add(type);
                player.addPotionEffect(new PotionEffect(
                        type, PotionEffect.INFINITE_DURATION, Math.max(0, amplifier), true, false, true));
            }
        });
        for (PotionEffectType type : track.given) {
            if (!wanted.contains(type)) {
                PotionEffect active = player.getPotionEffect(type);
                if (active != null && active.isInfinite()) {
                    player.removePotionEffect(type);
                }
            }
        }
        track.given = wanted;
        after.get(BehaviourKey.EFFECTS_BLOCK).ifPresent(list -> {
            for (String name : list.split(",", -1)) {
                PotionEffectType type = effect(name);
                if (type != null) {
                    player.removePotionEffect(type);
                }
            }
        });
        if (track.bar != null
                && after.get(BehaviourKey.MESSAGE_CHANNEL)
                        .filter("bossbar"::equals)
                        .isEmpty()) {
            player.hideBossBar(track.bar);
            track.bar = null;
        }
    }

    // Paper deprecates the getter without a replacement for reading the flag essentials god mode sets; the
    // setter is not deprecated, so reading it back is the only way to restore what the player had.
    @SuppressWarnings("deprecation")
    private static boolean invulnerable(Player player) {
        return player.isInvulnerable();
    }

    private void restore(Player player, Track track) {
        apply(player, track, Behaviours.empty());
    }

    private void message(Player player, Track track, Behaviours behaviours, BehaviourKey key) {
        Optional<String> text = behaviours.get(key);
        if (text.isEmpty()) {
            return;
        }
        String channel = behaviours.get(BehaviourKey.MESSAGE_CHANNEL).orElse("chat");
        Component rendered = StyledText.render(text.get());
        switch (channel) {
            case "title" -> {
                String[] parts = text.get().split("\\|", 2);
                player.showTitle(Title.title(
                        StyledText.render(parts[0]),
                        parts.length > 1 ? StyledText.render(parts[1]) : Component.empty(),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))));
            }
            case "actionbar" -> player.sendActionBar(rendered);
            case "bossbar" -> {
                if (track.bar != null) {
                    player.hideBossBar(track.bar);
                }
                BossBar bar = BossBar.bossBar(rendered, 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                track.bar = bar;
                player.showBossBar(bar);
            }
            default -> player.sendMessage(rendered);
        }
    }

    private void refuse(Player player, @Nullable RegionSet set, BehaviourKey custom, GuardMessageKey fallback) {
        Optional<String> text = set == null ? Optional.empty() : set.behaviour(custom);
        if (text.isPresent()) {
            player.sendActionBar(StyledText.render(text.get()));
        } else {
            guard.send(player, fallback, Map.of(), guard.config().denyChannel());
        }
    }

    private @Nullable Location pushBack(Track track, Location from, @Nullable RegionSet set, boolean teleport) {
        if (teleport) {
            return null;
        }
        boolean push = set == null
                || set.behaviour(BehaviourKey.ENTER_PUSH_BACK)
                        .map(Boolean::parseBoolean)
                        .orElse(true);
        Location back = push && track.safe != null ? track.safe.clone() : from.clone();
        back.setYaw(from.getYaw());
        back.setPitch(from.getPitch());
        return back;
    }

    private @Nullable Location voidCheck(Player player, Location to, @Nullable RegionSet set) {
        if (set == null) {
            return to;
        }
        Optional<String> rule = set.behaviour(BehaviourKey.VOID_TELEPORT);
        if (rule.isEmpty()) {
            return to;
        }
        String[] parts = rule.get().split(" ", -1);
        if (to.getY() >= Integer.parseInt(parts[0])) {
            return to;
        }
        World world = parts.length == 2 ? to.getWorld() : player.getServer().getWorld(parts[1]);
        if (world == null) {
            return to;
        }
        Location target = parts.length == 2
                ? world.getSpawnLocation()
                : new Location(
                        world,
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]));
        player.setFallDistance(0);
        java.util.concurrent.CompletableFuture<Boolean> unused = player.teleportAsync(target);
        return to;
    }

    private List<ClickAction> parse(String raw) {
        return parsedActions.computeIfAbsent(raw, PlayerTracks::parseActions);
    }

    /** {@code type: value | type: value}; a bad entry is dropped. */
    static List<ClickAction> parseActions(String raw) {
        List<ClickAction> out = new ArrayList<>();
        for (String entry : raw.split("\\s\\|\\s", -1)) {
            int colon = entry.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String word = entry.substring(0, colon).strip();
            String value = entry.substring(colon + 1).strip();
            ClickActionValueCheck.parseType(word).ifPresent(type -> {
                if (ClickActionValueCheck.check(type, value).isValid()) {
                    out.add(new ClickAction(ClickTrigger.ANY, type, value));
                }
            });
        }
        return List.copyOf(out);
    }

    private static boolean sameRegions(@Nullable RegionSet a, @Nullable RegionSet b) {
        if (a == b) {
            return true;
        }
        if (a != null && b != null) {
            return a.sameRegions(b);
        }
        RegionSet present = a != null ? a : Objects.requireNonNull(b);
        return present.stored().isEmpty() && present.behaviours().isEmpty();
    }

    private static List<Region> minus(@Nullable RegionSet a, @Nullable RegionSet b) {
        if (a == null) {
            return List.of();
        }
        List<Region> out = new ArrayList<>();
        Set<Object> other = new HashSet<>();
        if (b != null) {
            for (Region region : b.stored()) {
                other.add(region.key());
            }
        }
        for (Region region : a.stored()) {
            if (!other.contains(region.key())) {
                out.add(region);
            }
        }
        return out;
    }

    private static @Nullable PotionEffectType effect(String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        try {
            return Registry.EFFECT.get(NamespacedKey.minecraft(key));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
