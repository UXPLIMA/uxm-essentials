package com.uxplima.uxmessentials.teleport.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /spawn} family use case: {@code /spawn}, {@code /setspawn}, plus the multi-spawn operator verbs
 * {@code /setmainspawn} (the global default a world falls back to), {@code /removespawn} (drop a world's own
 * spawn), and {@code /mirrorspawn} (redirect a world's {@code /spawn} to another world). Resolution folds a
 * per-world {@link SpawnMirror} (when present) so a {@code /spawn} issued in world A may resolve to world B's
 * spawn, then prefers that world's own spawn, then the main spawn, then the directory's bottom-of-chain
 * default.
 *
 * <p>The main spawn is chosen per server: the one stored for this server's own scope, else the one of the first
 * shared set it belongs to that has one, else the network-wide one. {@code /setmainspawn} writes the scope this
 * server was given as its default, so on a network a lobby's spawn no longer moves every other server's. All writes go through the spawn directory; resolution reads the live target spawn through it rather
 * than a copied location, so a moved target spawn is reflected immediately.
 */
public final class ResolveSpawn {

    /** The scope word that names the network-wide main spawn. */
    public static final String NETWORK = "network";

    private final SpawnDirectory spawns;
    private final WorldLookup worlds;
    private final TeleportEngine engine;
    private final Notifier notifier;
    private final LocalServer local;
    private final @Nullable ServerScope defaultMainScope;

    /**
     * @param local this server and the shared sets it belongs to, which pick the main spawn that applies here
     * @param defaultMainScope the scope a plain {@code /setmainspawn} writes, or {@code null} for the network-wide
     *     main spawn
     */
    public ResolveSpawn(
            SpawnDirectory spawns,
            WorldLookup worlds,
            TeleportEngine engine,
            Notifier notifier,
            LocalServer local,
            @Nullable ServerScope defaultMainScope) {
        this.spawns = Objects.requireNonNull(spawns, "spawns");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.local = Objects.requireNonNull(local, "local");
        this.defaultMainScope = defaultMainScope;
    }

    /** {@code /spawn}: resolve the (possibly mirrored) default spawn for {@code currentWorld} and go. */
    public Result<Unit, TeleportError> toDefaultSpawn(PlayerRef who, WorldRef currentWorld) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(currentWorld, "currentWorld");
        Optional<Position> spawn = resolveDefault(currentWorld);
        return go(who, spawn);
    }

    /** {@code /spawn <name>}: resolve a named spawn and go. */
    public Result<Unit, TeleportError> toNamedSpawn(PlayerRef who, String name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        return go(who, spawns.namedSpawn(name));
    }

    /** {@code /setspawn}: set the default spawn for {@code world} to {@code position}. */
    public void setDefaultSpawn(WorldRef world, Position position) {
        spawns.setDefaultSpawn(Objects.requireNonNull(world, "world"), Objects.requireNonNull(position, "position"));
    }

    /** {@code /setspawn <name>}: set a named spawn to {@code position}. */
    public void setNamedSpawn(String name, Position position) {
        spawns.setNamedSpawn(Objects.requireNonNull(name, "name"), Objects.requireNonNull(position, "position"));
    }

    /** {@code /setmainspawn}: set this server's default main spawn scope to {@code position} and confirm. */
    public void setMain(PlayerRef who, Position position) {
        writeMain(who, defaultMainScope, position);
    }

    /**
     * {@code /setmainspawn scope <scope>}: set the main spawn of the scope {@code written} names ({@code network}
     * for every server, otherwise what {@link LocalServer#parse} reads) and confirm; false after telling
     * {@code who} the scope does not read.
     */
    public boolean setMain(PlayerRef who, String written, Position position) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(written, "written");
        Objects.requireNonNull(position, "position");
        if (written.strip().equalsIgnoreCase(NETWORK)) {
            writeMain(who, null, position);
            return true;
        }
        Optional<ServerScope> scope = local.parse(written);
        if (scope.isEmpty()) {
            notifier.send(who, TeleportMessageKey.SPAWN_SCOPE_INVALID, Map.of("scope", written));
            return false;
        }
        writeMain(who, scope.get(), position);
        return true;
    }

    private void writeMain(PlayerRef who, @Nullable ServerScope scope, Position position) {
        Objects.requireNonNull(who, "who");
        spawns.setMainSpawn(scope, Objects.requireNonNull(position, "position"));
        if (scope == null) {
            notifier.send(who, TeleportMessageKey.SPAWN_MAIN_SET);
        } else {
            notifier.send(who, TeleportMessageKey.SPAWN_MAIN_SET_SCOPED, Map.of("scope", scope.value()));
        }
    }

    /** The scope words {@code /setmainspawn scope} completes: the network, this server and its shared sets. */
    public List<String> scopeSuggestions() {
        List<String> words = new ArrayList<>();
        words.add(NETWORK);
        words.addAll(local.suggestions());
        return List.copyOf(words);
    }

    /**
     * The main spawn that applies on this server: this server's own, else the first of its shared sets that has
     * one, else the network-wide one.
     */
    public Optional<Position> resolveMain() {
        return mainFor(spawns, local);
    }

    /** The main spawn {@code spawns} holds for the server {@code local}, chosen the way {@link #resolveMain} does. */
    public static Optional<Position> mainFor(SpawnDirectory spawns, LocalServer local) {
        for (ServerScope scope : local.loadedScopes()) {
            Optional<Position> scoped = spawns.mainSpawn(scope);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return spawns.mainSpawn(null);
    }

    /** {@code /removespawn}: drop {@code world}'s own spawn so it falls back to the main spawn; confirm. */
    public void removeWorldSpawn(PlayerRef who, WorldRef world) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        boolean removed = spawns.removeDefaultSpawn(world);
        notifier.send(who, removed ? TeleportMessageKey.SPAWN_REMOVED : TeleportMessageKey.SPAWN_REMOVE_NONE);
    }

    /**
     * {@code /mirrorspawn <world>}: redirect {@code currentWorld}'s {@code /spawn} to {@code targetWorldName}'s
     * spawn. Rejects an unknown target world and a self-mirror, notifying the actor in each case.
     */
    public void mirror(PlayerRef who, WorldRef currentWorld, String targetWorldName) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(currentWorld, "currentWorld");
        Objects.requireNonNull(targetWorldName, "targetWorldName");
        Optional<WorldRef> target = worlds.findByName(targetWorldName);
        if (target.isEmpty()) {
            notifier.send(who, TeleportMessageKey.SPAWN_MIRROR_UNKNOWN_WORLD, Map.of("world", targetWorldName));
            return;
        }
        if (target.get().uid().equals(currentWorld.uid())) {
            notifier.send(who, TeleportMessageKey.SPAWN_MIRROR_SELF);
            return;
        }
        spawns.setMirror(new SpawnMirror(currentWorld.uid(), target.get().uid()));
        notifier.send(
                who,
                TeleportMessageKey.SPAWN_MIRRORED,
                Map.of("world", target.get().name()));
    }

    /**
     * The resolved default spawn for a world: a mirror (when configured) redirects to the target world, then
     * resolution prefers that world's own operator-set spawn, then the global main spawn, then the directory's
     * bottom-of-chain default (which folds in the vanilla world spawn).
     */
    public Optional<Position> resolveDefault(WorldRef world) {
        Objects.requireNonNull(world, "world");
        WorldRef effective = spawns.mirrorFor(world).flatMap(this::targetWorld).orElse(world);
        return spawns.operatorSpawn(effective).or(this::resolveMain).or(() -> spawns.defaultSpawn(effective));
    }

    private Result<Unit, TeleportError> go(PlayerRef who, Optional<Position> spawn) {
        if (spawn.isEmpty()) {
            notifier.send(who, TeleportMessageKey.SPAWN_UNRESOLVED);
            return Result.err(TeleportError.SPAWN_UNRESOLVED);
        }
        engine.launch(who, Destination.at(spawn.get()), TeleportKind.SPAWN);
        notifier.send(who, TeleportMessageKey.SPAWN_TELEPORTED);
        return Result.ok();
    }

    private Optional<WorldRef> targetWorld(SpawnMirror mirror) {
        UUID target = mirror.targetWorld();
        return worlds.findByUid(target);
    }
}
