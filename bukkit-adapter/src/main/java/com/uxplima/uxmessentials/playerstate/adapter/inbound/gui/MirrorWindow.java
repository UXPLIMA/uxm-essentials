package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.domain.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.MenuText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.EngineLog;
import com.uxplima.uxmessentials.shared.adapter.outbound.LivePlayers;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.providers.ContentRegions;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecs;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The two managed mirror windows as the operator sees them: {@code modules/playerstate/gui/invsee.conf} and
 * {@code endersee.conf} plus the bindings behind them. The files own each window's height, its wording and whatever
 * pads the slots that map to nothing on a player; this class owns what a file cannot. The titles resolved from the
 * message catalog, and the one block of slots each file hands over as a {@code content {}} region, where the real
 * stacks live and the rules that stop an item being duplicated apply.
 *
 * <p>All four mirror paths share these two files: the online {@code /invsee} and {@code /endersee}, and the offline
 * pair read from disk. They differ in where the snapshot comes from and where the write-back goes, which is what a
 * {@link MirrorHolder} carries, not in what the window looks like.
 *
 * <p>The region's slot order maps a window slot to a place on the target, so a file whose region is not exactly the
 * size that mapping needs would silently reconcile a helmet into a boot. That is refused here at wiring time.
 */
@NullMarked
public final class MirrorWindow {

    static final String INVSEE_SPEC_ID = "playerstate-invsee";
    static final String INVSEE_REGION = "playerstate:invsee";
    static final String INVSEE_RESOURCE = "modules/playerstate/gui/invsee.conf";

    static final String ENDERSEE_SPEC_ID = "playerstate-endersee";
    static final String ENDERSEE_REGION = "playerstate:endersee";
    static final String ENDERSEE_RESOURCE = "modules/playerstate/gui/endersee.conf";

    /** The heights the bundled specs are written for; a file that omits {@code rows} falls back to them. */
    private static final int INVSEE_ROWS = 6;

    private static final int ENDERSEE_ROWS = 3;

    private final Messages messages;
    private final Menus menus;
    private final Map<MirrorKind, MenuSpec> specs = new EnumMap<>(MirrorKind.class);
    private final Map<MirrorKind, List<Integer>> slots = new EnumMap<>(MirrorKind.class);

    public MirrorWindow(Messages messages, Menus menus, Path dataFolder, Logger log) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.menus = Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(dataFolder, "dataFolder");
        load(MirrorKind.INVENTORY, INVSEE_RESOURCE, INVSEE_ROWS, dataFolder, log);
        load(MirrorKind.ENDER, ENDERSEE_RESOURCE, ENDERSEE_ROWS, dataFolder, log);
    }

    private void load(MirrorKind kind, String resource, int rows, Path dataFolder, Logger log) {
        MenuSpec spec = MenuSpecs.loadOrBundled(resource, dataFolder, rows, EngineLog.of(log));
        List<Integer> declared = ContentRegions.slots(spec, kind.regionId(), resource);
        if (declared.size() != kind.slotCount()) {
            throw new IllegalStateException(resource + ": the '" + kind.regionId() + "' region must declare exactly "
                    + kind.slotCount() + " slots, one per place on the target it mirrors, but declares "
                    + declared.size() + "; a shorter or longer region would reconcile items into the wrong places");
        }
        specs.put(kind, spec);
        slots.put(kind, declared);
    }

    /** Give both specs their behaviour and register them; the wiring calls this once, before the first open. */
    public void register(MenuBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        MenuText.markup(bindings, "invsee_title", ctx -> title(ctx, PlayerstateMessageKey.INVSEE_TITLE));
        MenuText.markup(bindings, "endersee_title", ctx -> title(ctx, PlayerstateMessageKey.ENDERSEE_TITLE));
        MirrorContent content = new MirrorContent();
        bindings.content(INVSEE_REGION, content);
        bindings.content(ENDERSEE_REGION, content);
        specs.forEach((kind, spec) -> menus.registerSpec(kind.specId(), spec));
    }

    /** Show {@code holder}'s window to its viewer, carrying the holder as the menu's subject. */
    void open(MirrorHolder holder) {
        LivePlayers.of(holder.viewer())
                .ifPresent(viewer -> menus.open(viewer, holder.kind().specId(), holder));
    }

    /** What {@code holder}'s window holds now, when its viewer still has it open. Read on the viewer's thread. */
    Optional<@Nullable ItemStack[]> contents(MirrorHolder holder) {
        // Both kinds are loaded in the constructor, so a missing entry would mean the window was never built.
        List<Integer> region = Objects.requireNonNull(slots.get(holder.kind()));
        return windowOf(holder).map(inv -> ContentRegions.read(inv, region));
    }

    /** Close {@code holder}'s window, if its viewer still has it open. Run on the viewer's thread. */
    void close(MirrorHolder holder) {
        if (windowOf(holder).isPresent()) {
            LivePlayers.of(holder.viewer()).ifPresent(viewer -> viewer.closeInventory());
        }
    }

    /** The open window of {@code holder} itself, not another mirror window of the same kind. */
    private Optional<Inventory> windowOf(MirrorHolder holder) {
        return LivePlayers.of(holder.viewer())
                .flatMap(viewer -> menus.openWindow(viewer, holder.kind().specId()))
                .filter(inv -> of(inv) == holder);
    }

    /** The mirror window {@code inventory} is, or {@code null} when it is not one. */
    static @Nullable MirrorHolder of(Inventory inventory) {
        return inventory.getHolder(false) instanceof MenuHolder menu
                        && menu.ctx().subjectRaw().orElse(null) instanceof MirrorHolder mirror
                ? mirror
                : null;
    }

    /** How many slots the window of {@code kind} mirrors; the array a write-back reconciles is this long. */
    static int slotCount(MirrorKind kind) {
        return kind.slotCount();
    }

    private String title(MenuContext ctx, MessageKey key) {
        MirrorHolder holder = ctx.subject(MirrorHolder.class);
        return messages.resolve(
                BukkitRefs.toRef(ctx.viewer()),
                key,
                Map.of("player", holder.target().name()));
    }
}
