package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The read-only {@link WorldEditorScreen#GENERATION} screen: a fixed three-row chest that reports the edited
 * world's immutable {@link WorldSpec} — its environment, generation type, seed, and external generator (or
 * {@code vanilla} when none) — over a single back button. None of the four info slots mutate anything; the spec is
 * fixed at creation, so this screen only shows it. Each info item's name is the {@code GEN_*} catalog line with its
 * {@code value} bound, which is why there is no separate lore.
 *
 * <p>{@link #open} opens an inventory in the viewer's screen, so it runs on their entity thread through the kernel
 * {@link Scheduler}, re-resolving the live player there; a world missing from the repository falls back to a normal
 * spec so the screen still renders sensibly.
 */
@NullMarked
public final class WorldGenerationView {

    private static final int ENV_SLOT = 10;
    private static final int TYPE_SLOT = 12;
    private static final int SEED_SLOT = 14;
    private static final int GENERATOR_SLOT = 16;
    private static final int BACK_SLOT = 22;

    private final WorldEditorText text;
    private final WorldRepository repository;
    private final Scheduler scheduler;
    private final GuiLayout layout;

    public WorldGenerationView(
            WorldEditorText text, WorldRepository repository, Scheduler scheduler, GuiLayout layout) {
        this.text = Objects.requireNonNull(text, "text");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** Open the read-only generation screen for {@code world}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, WorldName world) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(world, "world");
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                openResolved(live, viewer, world);
            }
        });
    }

    /** Whether {@code slot} is the back button, the screen's only interactive slot. */
    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    private void openResolved(Player player, PlayerRef viewer, WorldName world) {
        WorldEditorHolder holder = new WorldEditorHolder(viewer, WorldEditorScreen.GENERATION, world, 0);
        int size = layout.rows() * 9;
        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                text.text(viewer, WorldEditorMessageKey.GENERATION_TITLE, Map.of("world", world.value())));
        holder.attach(inventory);
        populate(inventory, viewer, world, size);
        player.openInventory(inventory);
    }

    private void populate(Inventory inventory, PlayerRef viewer, WorldName world, int size) {
        WorldSpec spec = repository.find(world).map(ManagedWorld::spec).orElseGet(WorldSpec::normal);
        place(
                inventory,
                ENV_SLOT,
                size,
                info(
                        viewer,
                        Material.GRASS_BLOCK,
                        WorldEditorMessageKey.GEN_ENVIRONMENT,
                        spec.environment().name()));
        place(
                inventory,
                TYPE_SLOT,
                size,
                info(
                        viewer,
                        Material.MAP,
                        WorldEditorMessageKey.GEN_TYPE,
                        spec.worldType().name()));
        place(
                inventory,
                SEED_SLOT,
                size,
                info(
                        viewer,
                        Material.WHEAT_SEEDS,
                        WorldEditorMessageKey.GEN_SEED,
                        spec.seed().map(String::valueOf).orElse("(random)")));
        place(
                inventory,
                GENERATOR_SLOT,
                size,
                info(
                        viewer,
                        Material.COMMAND_BLOCK,
                        WorldEditorMessageKey.GEN_GENERATOR,
                        spec.generator().map(GeneratorRef::value).orElse("vanilla")));
        place(
                inventory,
                BACK_SLOT,
                size,
                ItemBuilder.of(Material.BARRIER)
                        .name(text.text(viewer, WorldEditorMessageKey.NAV_BACK))
                        .build());
    }

    private ItemStack info(PlayerRef viewer, Material material, MessageKey key, String value) {
        return ItemBuilder.of(material)
                .name(text.text(viewer, key, Map.of("value", value)))
                .build();
    }

    private static void place(Inventory inventory, int slot, int size, ItemStack item) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, item);
        }
    }
}
