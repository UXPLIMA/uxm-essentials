package com.uxplima.uxmessentials.teleport.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryBackLocationStore;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryRequestRegistry;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PdcTeleportFlags;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PrewarmedSafeLocationQueue;
import com.uxplima.uxmessentials.teleport.application.AcceptTeleport;
import com.uxplima.uxmessentials.teleport.application.CaptureBack;
import com.uxplima.uxmessentials.teleport.application.ListPendingRequests;
import com.uxplima.uxmessentials.teleport.application.RequestTeleport;
import com.uxplima.uxmessentials.teleport.application.ResolveBiomeRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveSpawn;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.BiomeCatalog;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed teleport use cases and the adapter-side state the inbound handlers and listeners share.
 * Built once per module start by {@code TeleportWiring} from the kernel ports and this context's own
 * outbound adapters; held so the Brigadier commands and the move/death/expiry listeners read the same
 * engine, registry, queue, and warmup tracker. On module stop the adapter drains the queue and clears the
 * in-memory stores through the handles exposed here.
 */
@NullMarked
public final class TeleportServices {

    private final RequestTeleport requestTeleport;
    private final AcceptTeleport acceptTeleport;
    private final ListPendingRequests listPendingRequests;
    private final CaptureBack captureBack;
    private final ResolveRtp resolveRtp;
    private final ResolveBiomeRtp resolveBiomeRtp;
    private final BiomeCatalog biomeCatalog;
    private final ResolveSpawn resolveSpawn;
    private final TeleportEngine engine;
    private final Notifier notifier;
    private final TeleportSettings settings;
    private final WarmupTracker warmupTracker;
    private final InMemoryRequestRegistry requests;
    private final InMemoryBackLocationStore backStore;
    private final PdcTeleportFlags flags;
    private final PrewarmedSafeLocationQueue rtpQueue;
    private final TeleportExecutor executor;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final Scheduler scheduler;

    public TeleportServices(Builder builder) {
        this.requestTeleport = Objects.requireNonNull(builder.requestTeleport, "requestTeleport");
        this.acceptTeleport = Objects.requireNonNull(builder.acceptTeleport, "acceptTeleport");
        this.listPendingRequests = Objects.requireNonNull(builder.listPendingRequests, "listPendingRequests");
        this.captureBack = Objects.requireNonNull(builder.captureBack, "captureBack");
        this.resolveRtp = Objects.requireNonNull(builder.resolveRtp, "resolveRtp");
        this.resolveBiomeRtp = Objects.requireNonNull(builder.resolveBiomeRtp, "resolveBiomeRtp");
        this.biomeCatalog = Objects.requireNonNull(builder.biomeCatalog, "biomeCatalog");
        this.resolveSpawn = Objects.requireNonNull(builder.resolveSpawn, "resolveSpawn");
        this.engine = Objects.requireNonNull(builder.engine, "engine");
        this.notifier = Objects.requireNonNull(builder.notifier, "notifier");
        this.settings = Objects.requireNonNull(builder.settings, "settings");
        this.warmupTracker = Objects.requireNonNull(builder.warmupTracker, "warmupTracker");
        this.requests = Objects.requireNonNull(builder.requests, "requests");
        this.backStore = Objects.requireNonNull(builder.backStore, "backStore");
        this.flags = Objects.requireNonNull(builder.flags, "flags");
        this.rtpQueue = Objects.requireNonNull(builder.rtpQueue, "rtpQueue");
        this.executor = Objects.requireNonNull(builder.executor, "executor");
        this.players = Objects.requireNonNull(builder.players, "players");
        this.worlds = Objects.requireNonNull(builder.worlds, "worlds");
        this.scheduler = Objects.requireNonNull(builder.scheduler, "scheduler");
    }

    public RequestTeleport requestTeleport() {
        return requestTeleport;
    }

    public AcceptTeleport acceptTeleport() {
        return acceptTeleport;
    }

    public ListPendingRequests listPendingRequests() {
        return listPendingRequests;
    }

    public CaptureBack captureBack() {
        return captureBack;
    }

    public ResolveRtp resolveRtp() {
        return resolveRtp;
    }

    /** The {@code /rtp biome <biome>} use case. */
    public ResolveBiomeRtp resolveBiomeRtp() {
        return resolveBiomeRtp;
    }

    /** The biome-key catalog, for the {@code /rtp biome} argument's tab completion. */
    public BiomeCatalog biomeCatalog() {
        return biomeCatalog;
    }

    public ResolveSpawn resolveSpawn() {
        return resolveSpawn;
    }

    public TeleportEngine engine() {
        return engine;
    }

    public Notifier notifier() {
        return notifier;
    }

    public TeleportSettings settings() {
        return settings;
    }

    public WarmupTracker warmupTracker() {
        return warmupTracker;
    }

    public InMemoryRequestRegistry requests() {
        return requests;
    }

    public InMemoryBackLocationStore backStore() {
        return backStore;
    }

    public PdcTeleportFlags flags() {
        return flags;
    }

    public PrewarmedSafeLocationQueue rtpQueue() {
        return rtpQueue;
    }

    public TeleportExecutor executor() {
        return executor;
    }

    public PlayerLookup players() {
        return players;
    }

    public WorldLookup worlds() {
        return worlds;
    }

    /** The Folia-aware scheduler the auto-accept branch hops to the target's region thread through. */
    public Scheduler scheduler() {
        return scheduler;
    }

    /** Release every in-memory store and queue this context holds. Called on module stop. */
    public void drain() {
        warmupTracker.clear();
        requests.clear();
        backStore.clear();
        flags.clear();
        rtpQueue.clear();
    }

    /** A small builder so {@code TeleportWiring} assembles the services without a 16-argument constructor. */
    public static final class Builder {
        private @org.jspecify.annotations.Nullable RequestTeleport requestTeleport;
        private @org.jspecify.annotations.Nullable AcceptTeleport acceptTeleport;
        private @org.jspecify.annotations.Nullable ListPendingRequests listPendingRequests;
        private @org.jspecify.annotations.Nullable CaptureBack captureBack;
        private @org.jspecify.annotations.Nullable ResolveRtp resolveRtp;
        private @org.jspecify.annotations.Nullable ResolveBiomeRtp resolveBiomeRtp;
        private @org.jspecify.annotations.Nullable BiomeCatalog biomeCatalog;
        private @org.jspecify.annotations.Nullable ResolveSpawn resolveSpawn;
        private @org.jspecify.annotations.Nullable TeleportEngine engine;
        private @org.jspecify.annotations.Nullable Notifier notifier;
        private @org.jspecify.annotations.Nullable TeleportSettings settings;
        private @org.jspecify.annotations.Nullable WarmupTracker warmupTracker;
        private @org.jspecify.annotations.Nullable InMemoryRequestRegistry requests;
        private @org.jspecify.annotations.Nullable InMemoryBackLocationStore backStore;
        private @org.jspecify.annotations.Nullable PdcTeleportFlags flags;
        private @org.jspecify.annotations.Nullable PrewarmedSafeLocationQueue rtpQueue;
        private @org.jspecify.annotations.Nullable TeleportExecutor executor;
        private @org.jspecify.annotations.Nullable PlayerLookup players;
        private @org.jspecify.annotations.Nullable WorldLookup worlds;
        private @org.jspecify.annotations.Nullable Scheduler scheduler;

        public Builder requestTeleport(RequestTeleport value) {
            this.requestTeleport = value;
            return this;
        }

        public Builder acceptTeleport(AcceptTeleport value) {
            this.acceptTeleport = value;
            return this;
        }

        public Builder listPendingRequests(ListPendingRequests value) {
            this.listPendingRequests = value;
            return this;
        }

        public Builder captureBack(CaptureBack value) {
            this.captureBack = value;
            return this;
        }

        public Builder resolveRtp(ResolveRtp value) {
            this.resolveRtp = value;
            return this;
        }

        public Builder resolveBiomeRtp(ResolveBiomeRtp value) {
            this.resolveBiomeRtp = value;
            return this;
        }

        public Builder biomeCatalog(BiomeCatalog value) {
            this.biomeCatalog = value;
            return this;
        }

        public Builder resolveSpawn(ResolveSpawn value) {
            this.resolveSpawn = value;
            return this;
        }

        public Builder engine(TeleportEngine value) {
            this.engine = value;
            return this;
        }

        public Builder notifier(Notifier value) {
            this.notifier = value;
            return this;
        }

        public Builder settings(TeleportSettings value) {
            this.settings = value;
            return this;
        }

        public Builder warmupTracker(WarmupTracker value) {
            this.warmupTracker = value;
            return this;
        }

        public Builder requests(InMemoryRequestRegistry value) {
            this.requests = value;
            return this;
        }

        public Builder backStore(InMemoryBackLocationStore value) {
            this.backStore = value;
            return this;
        }

        public Builder flags(PdcTeleportFlags value) {
            this.flags = value;
            return this;
        }

        public Builder rtpQueue(PrewarmedSafeLocationQueue value) {
            this.rtpQueue = value;
            return this;
        }

        public Builder executor(TeleportExecutor value) {
            this.executor = value;
            return this;
        }

        public Builder players(PlayerLookup value) {
            this.players = value;
            return this;
        }

        public Builder worlds(WorldLookup value) {
            this.worlds = value;
            return this;
        }

        public Builder scheduler(Scheduler value) {
            this.scheduler = value;
            return this;
        }

        public TeleportServices build() {
            return new TeleportServices(this);
        }
    }
}
