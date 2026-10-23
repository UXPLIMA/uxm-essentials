package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardEntry;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardProviders;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec;
import com.uxplima.uxmessentials.holograms.domain.Transform;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.display.HologramPackets;
import com.uxplima.uxmlib.packet.display.PacketHologram;
import com.uxplima.uxmlib.packet.display.PacketHologramClicks;
import com.uxplima.uxmlib.packet.display.PacketLine;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Every stored hologram, drawn for each viewer with packets. Nothing is spawned in the world: a hologram is a
 * {@link PacketHologram} that sends each player near it their own copy, so there is no entity to lose on a chunk
 * reload, to leave behind after a crash, or to share between two viewers who read different words.
 *
 * <p>A text hologram reads the same for everybody unless one of its lines says otherwise, which
 * {@link HologramViewerText#hasPerViewerText} decides; that text is resolved once per frame and handed to
 * every viewer. A text that differs per viewer is resolved on that viewer's own thread, because it reads their live
 * state, and kept until something it depends on changes: their language, their page, the animation frame, or a
 * refresh. A viewer is drawn the hologram only once their own text is ready, so no frame ever shows them another
 * viewer's words.
 */
@NullMarked
public final class PacketHologramView implements HologramView, HologramPageCycler {

    /** How far above the linked NPC's feet a linked hologram floats, so it sits over the NPC's head. */
    static final double LINKED_NPC_Y_OFFSET = 2.2;

    /** The height of one line of text, which a grow-up hologram is raised by for each of its lines. */
    static final double LINE_HEIGHT = 0.28;

    /** A text hologram's click box is as wide as a block and a little more. */
    private static final float BOX_WIDTH = 1.2f;

    /** A model's click box: about the size of the model. */
    private static final float MODEL_BOX = 1.2f;

    /** What a click on a hologram's box does: called on the clicking player's own thread. */
    @FunctionalInterface
    public interface ClickHandler {
        void clicked(Player player, HologramName hologram, boolean attack);
    }

    private final HologramPackets packets;
    private final com.uxplima.uxmlib.scheduler.Scheduler frames;
    private final Scheduler scheduler;
    private final Logger log;
    private final HologramViewerText text;
    private final UnaryOperator<String> globalPlaceholders;
    private final HologramViewers viewers;
    private final LinkedNpcLocator linkedNpcs;
    private final LeaderboardProviders leaderboards;
    private final HologramPageState pageState;
    private final PacketHologramClicks clicks;
    private final ClickHandler onClick;
    private final Map<String, Live> live = new ConcurrentHashMap<>();

    public PacketHologramView(
            HologramPackets packets,
            com.uxplima.uxmlib.scheduler.Scheduler frames,
            Scheduler scheduler,
            Logger log,
            HologramViewerText text,
            UnaryOperator<String> globalPlaceholders,
            HologramViewers viewers,
            LinkedNpcLocator linkedNpcs,
            LeaderboardProviders leaderboards,
            HologramPageState pageState,
            PacketHologramClicks clicks,
            ClickHandler onClick) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.frames = Objects.requireNonNull(frames, "frames");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.text = Objects.requireNonNull(text, "text");
        this.globalPlaceholders = Objects.requireNonNull(globalPlaceholders, "globalPlaceholders");
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.linkedNpcs = Objects.requireNonNull(linkedNpcs, "linkedNpcs");
        this.leaderboards = Objects.requireNonNull(leaderboards, "leaderboards");
        this.pageState = Objects.requireNonNull(pageState, "pageState");
        this.clicks = Objects.requireNonNull(clicks, "clicks");
        this.onClick = Objects.requireNonNull(onClick, "onClick");
    }

    @Override
    public void render(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        LeaderboardSpec leaderboard = hologram.leaderboard();
        if (leaderboard == null) {
            show(hologram);
            return;
        }
        // A leaderboard's rows come from a provider that may read a database, so they are fetched off the tick.
        scheduler.async(() -> show(hologram.withLines(rowsOf(leaderboard))));
    }

    /**
     * Read the hologram again: a leaderboard fetches its rows again, every text is resolved again and an animated
     * line moves to its next frame. What a viewer holds is repainted, never spawned again.
     */
    public void refresh(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (!live.containsKey(hologram.name().value())) {
            return;
        }
        LeaderboardSpec leaderboard = hologram.leaderboard();
        if (leaderboard == null) {
            advance(hologram);
            return;
        }
        scheduler.async(() -> advance(hologram.withLines(rowsOf(leaderboard))));
    }

    /** Draw again every hologram linked to the NPC {@code npcName}, so it follows the NPC to where it now stands. */
    public void reanchorLinkedTo(String npcName) {
        Objects.requireNonNull(npcName, "npcName");
        for (Live hologram : live.values()) {
            if (npcName.equals(hologram.stored.linkedNpcName())) {
                render(hologram.stored);
            }
        }
    }

    @Override
    public void despawn(HologramName name) {
        Objects.requireNonNull(name, "name");
        pageState.clear(name.value());
        Live gone = live.remove(name.value());
        if (gone != null) {
            takeOff(gone);
        }
    }

    /** Take every hologram off every client, as the module stops. */
    public void despawnAll() {
        for (String name : List.copyOf(live.keySet())) {
            Live gone = live.remove(name);
            if (gone != null) {
                takeOff(gone);
            }
        }
        pageState.clearAll();
    }

    /** A MANUAL viewer was shown or hidden the hologram: draw or take it off them now, not on the next frame. */
    @Override
    public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        Live hologram = live.get(name.value());
        if (hologram != null) {
            hologram.updateSoon();
        }
    }

    /** Turn {@code viewer} to the next page of {@code name}; nobody else's page moves. */
    @Override
    public void cyclePage(Player viewer, HologramName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Live hologram = live.get(name.value());
        if (hologram == null || !hologram.shown.isMultiPage()) {
            return;
        }
        pageState.advance(name.value(), viewer.getUniqueId(), hologram.shown.pageCount());
        hologram.updateSoon();
    }

    /** Forget what was resolved for {@code viewer}, who left the server. */
    public void forget(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        for (Live hologram : live.values()) {
            hologram.texts.remove(viewer);
            hologram.pending.remove(viewer);
        }
    }

    /** A hologram a look crosses, and how far along the look, in blocks from the eye. */
    public record Aimed(HologramName name, double distance) {}

    /**
     * The clickable hologram {@code viewer} is looking at within {@code reach} blocks, the nearest when the look
     * crosses several. Only a hologram drawn for the viewer counts, matched against the text as they read it.
     * Called on the viewer's own thread.
     */
    public Optional<Aimed> aimedAt(Player viewer, double reach) {
        Objects.requireNonNull(viewer, "viewer");
        Location eye = viewer.getEyeLocation();
        Vector look = eye.getDirection();
        UUID world = viewer.getWorld().getUID();
        @Nullable Aimed nearest = null;
        for (Live hologram : live.values()) {
            if (!clickable(hologram.shown) || !hologram.anchor.world().uid().equals(world)) {
                continue;
            }
            OptionalDouble along = hologram.hit(viewer, eye, look, reach);
            if (along.isPresent() && (nearest == null || along.getAsDouble() < nearest.distance())) {
                nearest = new Aimed(hologram.shown.name(), along.getAsDouble());
            }
        }
        return Optional.ofNullable(nearest);
    }

    private void show(Hologram hologram) {
        Position anchor = anchorFor(hologram, linkedNpcs);
        World world = Bukkit.getWorld(anchor.world().uid());
        if (world == null) {
            log.warn(
                    "skipping hologram {}. World {} is not loaded",
                    hologram.name().value(),
                    anchor.world().name());
            return;
        }
        @Nullable PacketLine model = modelOf(hologram);
        if (hologram.type() != HologramType.TEXT && model == null) {
            log.warn(
                    "skipping hologram {}. Its {} model {} does not exist",
                    hologram.name().value(),
                    hologram.type().name().toLowerCase(Locale.ROOT),
                    modelName(hologram));
            Live gone = live.remove(hologram.name().value());
            if (gone != null) {
                takeOff(gone);
            }
            return;
        }
        Live next = new Live(hologram, anchor, model);
        PacketHologram drawn = PacketHologram.showLines(
                packets,
                frames,
                placeAt(world, hologram, anchor),
                PacketAppearances.of(hologram.appearance()),
                next::linesFor,
                PacketAppearances.viewDistance(hologram.visibility(), hologram.appearance()),
                PacketHologram.DEFAULT_REFRESH_PERIOD);
        next.attach(drawn);
        drawn.setAudience(next::mayView);
        if (clickable(hologram)) {
            Transform transform = hologram.appearance().transform();
            drawn.setClickBox(
                    BOX_WIDTH * transform.scaleX(),
                    boxHeight(hologram) * transform.scaleY(),
                    -transform.translationY());
            HologramName name = hologram.name();
            clicks.register(drawn, (player, attack) -> onClick.clicked(player, name, attack));
        }
        Live previous = live.put(hologram.name().value(), next);
        if (previous != null) {
            takeOff(previous);
        }
    }

    /** A new read of a hologram already drawn: the same entities, new words. */
    private void advance(Hologram hologram) {
        Live current = live.get(hologram.name().value());
        if (current != null) {
            current.advance(hologram);
        }
    }

    private void takeOff(Live gone) {
        PacketHologram drawn = gone.drawn;
        if (drawn == null) {
            return;
        }
        clicks.unregister(drawn);
        scheduler.onRegion(gone.anchor, drawn::remove);
    }

    /** The top rows of {@code spec}'s provider, one ranked line each; a single line saying so when there are none. */
    private List<HologramLine> rowsOf(LeaderboardSpec spec) {
        List<LeaderboardEntry> entries = leaderboards
                .find(spec.providerId())
                .map(provider -> provider.top(spec.limit()))
                .orElse(List.of());
        if (entries.isEmpty()) {
            return List.of(new HologramLine("<gray>(no data)"));
        }
        List<HologramLine> rows = new ArrayList<>(entries.size());
        int rank = 1;
        for (LeaderboardEntry entry : entries) {
            rows.add(new HologramLine(
                    "<gray>#" + rank + " <white>" + entry.name() + " <gray>- <green>" + entry.score()));
            rank++;
        }
        return rows;
    }

    /**
     * Where the hologram floats: above its NPC when it is linked to one that exists, otherwise where it was
     * placed. A link to an NPC that is gone falls back rather than hiding the hologram.
     */
    static Position anchorFor(Hologram hologram, LinkedNpcLocator linkedNpcs) {
        String linked = hologram.linkedNpcName();
        if (linked == null) {
            return hologram.location();
        }
        return linkedNpcs
                .locate(linked)
                .map(npc -> new Position(
                        npc.world(), npc.x(), npc.y() + LINKED_NPC_Y_OFFSET, npc.z(), npc.yaw(), npc.pitch()))
                .orElseGet(hologram::location);
    }

    /**
     * The point the hologram is drawn at. A grow-up hologram is raised by its text, so its bottom sits on the anchor
     * and new lines push it up. The stored rotation turns it on top of the way it was placed facing, which only a
     * fixed billboard shows.
     */
    private static Location placeAt(World world, Hologram hologram, Position anchor) {
        Location at = BukkitRefs.toLocation(world, anchor);
        if (hologram.growUp()) {
            at.add(0, hologram.lineCount() * LINE_HEIGHT, 0);
        }
        at.setYaw(anchor.yaw() + hologram.rotation().yaw());
        at.setPitch(anchor.pitch() + hologram.rotation().pitch());
        return at;
    }

    /** The height the text's lowest line is drawn at: the anchor, raised for a grow-up hologram and by its offset. */
    private static double footOf(Hologram hologram, Position anchor) {
        double foot = anchor.y() + hologram.appearance().transform().translationY();
        return hologram.growUp() ? foot + hologram.lineCount() * LINE_HEIGHT : foot;
    }

    /** A box is drawn for a hologram a click does something on: a command, an action, or a page to turn. */
    private static boolean clickable(Hologram hologram) {
        String command = hologram.clickCommand();
        return (command != null && !command.isBlank()) || hologram.hasActions() || hologram.isMultiPage();
    }

    /** Tall enough for the tallest page of text, or the size of a model. */
    private static float boxHeight(Hologram hologram) {
        if (hologram.type() != HologramType.TEXT) {
            return MODEL_BOX;
        }
        return Math.max(1.0f, hologram.maxPageLineCount() * (float) LINE_HEIGHT + 0.4f);
    }

    /** The one line a model hologram is drawn as, or none for text or for a model that does not exist. */
    private static @Nullable PacketLine modelOf(Hologram hologram) {
        return switch (hologram.type()) {
            case TEXT -> null;
            case ITEM -> itemLine(HologramModels.itemOf(hologram.itemMaterial()));
            case HEAD -> itemLine(HologramModels.headOf(hologram.headTexture()));
            case BLOCK -> {
                @Nullable BlockData block = HologramModels.blockOf(hologram.blockData());
                yield block == null ? null : PacketLine.block(block);
            }
            case ENTITY -> {
                @Nullable EntityType type = HologramModels.entityTypeOf(hologram.entityType());
                yield type == null ? null : PacketLine.mob(type.getKey().getKey());
            }
        };
    }

    private static @Nullable PacketLine itemLine(@Nullable ItemStack item) {
        return item == null ? null : PacketLine.item(item);
    }

    private static String modelName(Hologram hologram) {
        return String.valueOf(
                switch (hologram.type()) {
                    case TEXT -> "";
                    case ITEM -> hologram.itemMaterial();
                    case BLOCK -> hologram.blockData();
                    case HEAD -> hologram.headTexture();
                    case ENTITY -> hologram.entityType();
                });
    }

    /** What one viewer's text depends on: when any of it changes, their text is resolved again. */
    private record TextKey(Locale language, int page, int frame) {}

    /** One viewer's resolved text, or none when resolving it failed for this key. */
    private record ViewerText(TextKey key, @Nullable Component text) {}

    /** The text everybody reads, for one frame. */
    private record SharedText(int frame, Component text) {}

    /** One drawn hologram: what it shows, where, and each viewer's text. */
    private final class Live {

        private final Hologram stored;
        private final Position anchor;
        private final @Nullable PacketLine model;
        private final boolean perViewer;
        private final Map<UUID, ViewerText> texts = new ConcurrentHashMap<>();
        private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
        private volatile Hologram shown;
        /** Moves on every read: the animation frame, and the mark every resolved text is checked against. */
        private final AtomicInteger frame = new AtomicInteger();

        private volatile @Nullable SharedText shared;
        private volatile @Nullable PacketHologram drawn;

        Live(Hologram shown, Position anchor, @Nullable PacketLine model) {
            this.stored = shown;
            this.shown = shown;
            this.anchor = anchor;
            this.model = model;
            this.perViewer = text.hasPerViewerText(shown);
        }

        void attach(PacketHologram packetHologram) {
            this.drawn = packetHologram;
        }

        /** A new read: new rows for a leaderboard, the next animation frame, and every text resolved again. */
        void advance(Hologram next) {
            shown = next;
            frame.incrementAndGet();
            updateSoon();
        }

        /** Push a change out on the hologram's own thread now, rather than at the next frame. */
        void updateSoon() {
            PacketHologram packetHologram = drawn;
            if (packetHologram != null) {
                scheduler.onRegion(anchor, packetHologram::update);
            }
        }

        /**
         * Who is drawn the hologram: a viewer it is visible to, who is not on its blacklist, and whose own text is
         * ready. Asked on the hologram's thread for every player in range, every frame. A viewer whose text is
         * missing or out of date has it resolved on their own thread, and keeps the text they hold until then.
         */
        boolean mayView(Player viewer) {
            Hologram current = shown;
            if (viewers.isBlacklisted(current, viewer)
                    || !viewers.maySee(current, viewer, viewers.shownViewersFor(current))) {
                return false;
            }
            if (model != null || !perViewer) {
                return true;
            }
            UUID id = viewer.getUniqueId();
            TextKey key = new TextKey(HudText.languageOf(id), text.pageOf(viewer, current), frame.get());
            @Nullable ViewerText held = texts.get(id);
            if (held == null || !held.key().equals(key)) {
                resolveFor(viewer, key);
            }
            return held != null && held.text() != null;
        }

        /** How far along the look from {@code eye} it meets this hologram as {@code viewer} is drawn it, if it does. */
        OptionalDouble hit(Player viewer, Location eye, Vector look, double reach) {
            Hologram current = shown;
            if (viewers.isBlacklisted(current, viewer)
                    || !viewers.maySee(current, viewer, viewers.shownViewersFor(current))) {
                return OptionalDouble.empty();
            }
            HologramAim.@Nullable Panel panel = panelFor(viewer, current);
            if (panel == null) {
                return OptionalDouble.empty();
            }
            Billboard billboard = current.appearance().billboard();
            if (billboard == Billboard.CENTER || billboard == Billboard.VERTICAL) {
                return HologramAim.hit(
                        eye.getX(), eye.getY(), eye.getZ(), look.getX(), look.getY(), look.getZ(), panel, reach);
            }
            // A board that keeps its facing faces the way it was placed, turned by its stored rotation.
            double yaw = Math.toRadians(anchor.yaw() + current.rotation().yaw());
            return HologramAim.hitFacing(
                    eye.getX(),
                    eye.getY(),
                    eye.getZ(),
                    look.getX(),
                    look.getY(),
                    look.getZ(),
                    panel,
                    -Math.sin(yaw),
                    Math.cos(yaw),
                    reach);
        }

        /** The text as {@code viewer} reads it, as a panel; none while their text is not resolved yet. */
        private HologramAim.@Nullable Panel panelFor(Player viewer, Hologram current) {
            Transform transform = current.appearance().transform();
            double foot = footOf(current, anchor);
            if (model != null) {
                double size = MODEL_BOX * transform.scaleY();
                return new HologramAim.Panel(
                        anchor.x(), anchor.z(), foot - size / 2, size, MODEL_BOX * transform.scaleX() / 2);
            }
            @Nullable Component words;
            if (perViewer) {
                @Nullable ViewerText held = texts.get(viewer.getUniqueId());
                words = held == null ? null : held.text();
            } else {
                // Read as last drawn, never resolved here: resolving runs placeholders, which belong on the
                // hologram's own thread, and this is the clicking player's.
                @Nullable SharedText drawnText = shared;
                words = drawnText == null ? null : drawnText.text();
            }
            if (words == null) {
                return null;
            }
            int pixels = Math.min(
                    HologramAim.widestLinePixels(words), current.appearance().lineWidth());
            double halfWidth = pixels * HologramAim.BLOCKS_PER_PIXEL * transform.scaleX() / 2;
            double height = HologramAim.lineCount(words) * LINE_HEIGHT * transform.scaleY();
            return new HologramAim.Panel(anchor.x(), anchor.z(), foot, height, halfWidth);
        }

        /** The lines {@code viewer} is drawn: the model, the shared text, or their own text. */
        List<PacketLine> linesFor(Player viewer) {
            if (model != null) {
                return List.of(model);
            }
            if (!perViewer) {
                return List.of(PacketLine.text(sharedText()));
            }
            @Nullable ViewerText held = texts.get(viewer.getUniqueId());
            @Nullable Component own = held == null ? null : held.text();
            return List.of(PacketLine.text(own == null ? Component.empty() : own));
        }

        /** The text everybody reads, resolved once for each frame and kept for every viewer of it. */
        private Component sharedText() {
            int currentFrame = frame.get();
            @Nullable SharedText current = shared;
            if (current != null && current.frame() == currentFrame) {
                return current.text();
            }
            Component resolved = text.sharedText(shown, globalPlaceholders, currentFrame);
            shared = new SharedText(currentFrame, resolved);
            return resolved;
        }

        /** Resolve {@code viewer}'s text on their own thread, once at a time, and draw it as soon as it is ready. */
        private void resolveFor(Player viewer, TextKey key) {
            UUID id = viewer.getUniqueId();
            if (!pending.add(id)) {
                return;
            }
            Hologram current = shown;
            scheduler.onEntity(BukkitRefs.toRef(viewer), () -> {
                try {
                    texts.put(id, new ViewerText(key, text.textFor(viewer, current, key.frame())));
                } catch (RuntimeException failed) {
                    // Kept as a failure for this key, so a text that cannot be read is not tried on every frame.
                    texts.put(id, new ViewerText(key, null));
                    log.warn(
                            "hologram {} could not be read for {}: {}",
                            current.name().value(),
                            viewer.getName(),
                            failed.toString());
                } finally {
                    pending.remove(id);
                }
                updateSoon();
            });
        }
    }
}
