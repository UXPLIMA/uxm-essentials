package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldNotifier;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link WorldEditorScreen#CREATE} new-world configuration screen: a fixed three-row chest with one button per
 * field of an in-flight {@link WorldCreateDraft}. The name and seed are captured through the unified {@link TextInput}
 * seam (a click opens the configured anvil/chat prompt); the environment, world type, and generator are selectors a
 * click cycles in place. A clearly-labelled create button at the bottom builds the draft's {@link WorldName} and
 * {@code WorldSpec} and runs {@link CreateWorld} on the global region thread (the only place a Bukkit world handle may
 * be created), then reopens the world list so the new world appears. A back button returns to the list.
 *
 * <p>The draft lives on the {@link WorldEditorHolder}, so every cycle or input submission rebuilds the window from a
 * fresh draft — the screen holds no per-viewer mutable state of its own. The spec mapping mirrors
 * {@code WorldCommand.runCreate} exactly (defaults NORMAL/NORMAL, empty seed and generator), so a GUI-built world is
 * identical to a command-built one.
 *
 * <p>{@link #open} opens an inventory in the viewer's screen, so it is scheduled on their entity thread through the
 * kernel {@link Scheduler}, re-resolving the live player there.
 */
@NullMarked
public final class WorldCreateView {

    static final int NAME_SLOT = 4;
    static final int ENVIRONMENT_SLOT = 10;
    static final int TYPE_SLOT = 12;
    static final int GENERATOR_SLOT = 14;
    static final int SEED_SLOT = 16;
    static final int BACK_SLOT = 18;
    static final int CREATE_SLOT = 22;

    /** The config key the operator flips between anvil and chat for the new-world name prompt. */
    static final String NAME_INPUT_KEY = "world.create-name";
    /** The config key the operator flips between anvil and chat for the new-world seed prompt. */
    static final String SEED_INPUT_KEY = "world.create-seed";

    private final WorldEditorText text;
    private final CreateWorld createWorld;
    private final WorldNotifier notifier;
    private final BiConsumer<Player, PlayerRef> reopenList;
    private final TextInput textInput;
    private final Scheduler scheduler;
    private final GuiLayout layout;

    public WorldCreateView(
            WorldEditorText text,
            CreateWorld createWorld,
            WorldNotifier notifier,
            BiConsumer<Player, PlayerRef> reopenList,
            TextInput textInput,
            Scheduler scheduler,
            GuiLayout layout) {
        this.text = Objects.requireNonNull(text, "text");
        this.createWorld = Objects.requireNonNull(createWorld, "createWorld");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.reopenList = Objects.requireNonNull(reopenList, "reopenList");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** Open a fresh create screen for {@code viewer}, scheduled on their entity thread. */
    public void open(Player player, PlayerRef viewer) {
        open(player, viewer, WorldCreateDraft.empty());
    }

    /** Open the create screen showing {@code draft}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, WorldCreateDraft draft) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(draft, "draft");
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                openResolved(live, viewer, draft);
            }
        });
    }

    /**
     * Route a click on {@code slot} of the create screen carrying {@code draft}. A selector cycles in place (reopening
     * with the updated draft), the name/seed slots open their input prompt, the create button builds the world, and the
     * back button returns to the list. Clicks on any other slot are ignored. The {@code rightClick} flag cycles a
     * selector backward, matching the property-grid convention.
     */
    public void onClick(Player player, PlayerRef viewer, WorldCreateDraft draft, int slot, boolean rightClick) {
        switch (slot) {
            case ENVIRONMENT_SLOT ->
                open(player, viewer, draft.withEnvironment(cycleEnvironment(draft.environment(), rightClick)));
            case TYPE_SLOT -> open(player, viewer, draft.withWorldType(cycleType(draft.worldType(), rightClick)));
            case GENERATOR_SLOT ->
                open(player, viewer, draft.withGenerator(cycleGenerator(draft.generator(), rightClick)));
            case NAME_SLOT -> promptName(player, viewer, draft);
            case SEED_SLOT -> promptSeed(player, viewer, draft);
            case CREATE_SLOT -> create(player, viewer, draft);
            case BACK_SLOT -> reopenList.accept(player, viewer);
            default -> {
                // Filler/summary slots are inert.
            }
        }
    }

    private void promptName(Player player, PlayerRef viewer, WorldCreateDraft draft) {
        InputRequest request = InputRequest.of(NAME_INPUT_KEY, WorldEditorMessageKey.CREATE_NAME_PROMPT);
        textInput.prompt(
                player,
                viewer,
                request,
                line -> applyName(player, viewer, draft, line),
                () -> open(player, viewer, draft));
    }

    private void applyName(Player player, PlayerRef viewer, WorldCreateDraft draft, String line) {
        String trimmed = line.trim();
        try {
            WorldName.of(trimmed); // validate the same shape WorldName/runCreate enforce
            open(player, viewer, draft.withName(trimmed));
        } catch (IllegalArgumentException invalid) {
            notifier.send(viewer, WorldsMessageKey.WORLD_NAME_INVALID, Map.of("world", trimmed));
            open(player, viewer, draft);
        }
    }

    private void promptSeed(Player player, PlayerRef viewer, WorldCreateDraft draft) {
        InputRequest request = InputRequest.of(SEED_INPUT_KEY, WorldEditorMessageKey.CREATE_SEED_PROMPT);
        textInput.prompt(
                player,
                viewer,
                request,
                line -> applySeed(player, viewer, draft, line),
                () -> open(player, viewer, draft));
    }

    private void applySeed(Player player, PlayerRef viewer, WorldCreateDraft draft, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            open(player, viewer, draft.withSeed(Optional.empty()));
            return;
        }
        try {
            open(player, viewer, draft.withSeed(Optional.of(Long.parseLong(trimmed))));
        } catch (NumberFormatException invalid) {
            notifier.send(viewer, WorldEditorMessageKey.CREATE_SEED_INVALID, Map.of("value", trimmed));
            open(player, viewer, draft);
        }
    }

    private void create(Player player, PlayerRef viewer, WorldCreateDraft draft) {
        if (!draft.hasName()) {
            notifier.send(viewer, WorldEditorMessageKey.CREATE_NAME_REQUIRED, Map.of());
            open(player, viewer, draft);
            return;
        }
        WorldName name;
        try {
            name = WorldName.of(draft.name());
        } catch (IllegalArgumentException invalid) {
            notifier.send(viewer, WorldsMessageKey.WORLD_NAME_INVALID, Map.of("world", draft.name()));
            open(player, viewer, draft);
            return;
        }
        notifier.send(viewer, WorldsMessageKey.WORLD_CREATING, Map.of("world", name.value()));
        // Creating a Bukkit world handle is legal under Folia only on the global region thread, so hop there
        // (CreateWorld surfaces success/failure through the world MessageKeys), then reopen the list so the new
        // world shows up. Reopen self-schedules back onto the viewer's entity thread.
        scheduler.onGlobal(() -> {
            createWorld.create(viewer, name, draft.toSpec(), true);
            reopenList.accept(player, viewer);
        });
    }

    private void openResolved(Player player, PlayerRef viewer, WorldCreateDraft draft) {
        WorldEditorHolder holder = new WorldEditorHolder(viewer, WorldEditorScreen.CREATE, null, 0, draft);
        int size = layout.rows() * 9;
        Inventory inventory =
                Bukkit.createInventory(holder, size, text.text(viewer, WorldEditorMessageKey.CREATE_TITLE));
        holder.attach(inventory);
        populate(inventory, viewer, draft, size);
        player.openInventory(inventory);
    }

    private void populate(Inventory inventory, PlayerRef viewer, WorldCreateDraft draft, int size) {
        place(inventory, NAME_SLOT, size, nameButton(viewer, draft));
        place(
                inventory,
                ENVIRONMENT_SLOT,
                size,
                selector(
                        viewer,
                        Material.GRASS_BLOCK,
                        WorldEditorMessageKey.CREATE_ENVIRONMENT,
                        draft.environment().name()));
        place(
                inventory,
                TYPE_SLOT,
                size,
                selector(
                        viewer,
                        Material.MAP,
                        WorldEditorMessageKey.CREATE_TYPE,
                        draft.worldType().name()));
        place(
                inventory,
                GENERATOR_SLOT,
                size,
                selector(
                        viewer,
                        Material.COMMAND_BLOCK,
                        WorldEditorMessageKey.CREATE_GENERATOR,
                        draft.generatorId().orElse("vanilla")));
        place(inventory, SEED_SLOT, size, seedButton(viewer, draft));
        place(inventory, CREATE_SLOT, size, createButton(viewer));
        place(
                inventory,
                BACK_SLOT,
                size,
                ItemBuilder.of(Material.BARRIER)
                        .name(text.text(viewer, WorldEditorMessageKey.NAV_BACK))
                        .build());
    }

    private ItemStack nameButton(PlayerRef viewer, WorldCreateDraft draft) {
        String shown = draft.hasName() ? draft.name() : "(not set)";
        Component name = text.text(viewer, WorldEditorMessageKey.CREATE_NAME, Map.of("value", shown));
        Component lore = text.text(viewer, WorldEditorMessageKey.CREATE_NAME_LORE);
        return ItemBuilder.of(Material.NAME_TAG).name(name).lore(List.of(lore)).build();
    }

    private ItemStack seedButton(PlayerRef viewer, WorldCreateDraft draft) {
        String shown = draft.seed().map(String::valueOf).orElse("(random)");
        Component name = text.text(viewer, WorldEditorMessageKey.CREATE_SEED, Map.of("value", shown));
        Component lore = text.text(viewer, WorldEditorMessageKey.CREATE_SEED_LORE);
        return ItemBuilder.of(Material.WHEAT_SEEDS)
                .name(name)
                .lore(List.of(lore))
                .build();
    }

    private ItemStack createButton(PlayerRef viewer) {
        Component name = text.text(viewer, WorldEditorMessageKey.CREATE_CONFIRM);
        Component lore = text.text(viewer, WorldEditorMessageKey.CREATE_CONFIRM_LORE);
        return ItemBuilder.of(Material.NETHER_STAR)
                .name(name)
                .lore(List.of(lore))
                .build();
    }

    private ItemStack selector(PlayerRef viewer, Material material, MessageKey key, String value) {
        Component name = text.text(viewer, key, Map.of("value", value));
        Component hint = text.text(viewer, WorldEditorMessageKey.CREATE_CYCLE_HINT);
        return ItemBuilder.of(material).name(name).lore(List.of(hint)).build();
    }

    private static WorldEnvironment cycleEnvironment(WorldEnvironment current, boolean backward) {
        return cycle(WorldEnvironment.values(), current, backward);
    }

    private static WorldGenType cycleType(WorldGenType current, boolean backward) {
        return cycle(WorldGenType.values(), current, backward);
    }

    /** The generator cycle: vanilla (null) → void → flat → vanilla, matching the two built-ins the GUI offers. */
    private static @org.jspecify.annotations.Nullable String cycleGenerator(
            @org.jspecify.annotations.Nullable String current, boolean backward) {
        List<String> order = java.util.Arrays.asList(null, BuiltInGenerators.VOID, BuiltInGenerators.FLAT);
        int index = order.indexOf(current);
        int step = backward ? -1 : 1;
        int next = Math.floorMod(index + step, order.size());
        return order.get(next);
    }

    private static <E extends Enum<E>> E cycle(E[] values, E current, boolean backward) {
        List<E> order = java.util.Arrays.asList(values);
        int step = backward ? -1 : 1;
        int next = Math.floorMod(order.indexOf(current) + step, order.size());
        return order.get(next);
    }

    private static void place(Inventory inventory, int slot, int size, ItemStack item) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, item);
        }
    }
}
