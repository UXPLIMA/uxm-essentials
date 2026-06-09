package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class WarpSoundSelectorView {

    /** The 27-slot (three-row) selector chest, with the option grid, custom-input, back and remove buttons. */
    static final int SIZE = 27;

    static final int OPTION_LIMIT = 18;
    static final int CUSTOM_SLOT = 18;
    static final int BACK_SLOT = 22;
    static final int REMOVE_SLOT = 26;

    private final Messages messages;
    private final Scheduler scheduler;
    private final MiniMessage miniMessage;

    public WarpSoundSelectorView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(
            Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner, boolean isDeparture) {
        scheduler.onEntity(viewer, () -> {
            WarpSoundSelectorHolder holder = new WarpSoundSelectorHolder(viewer, warpName, warpOwner, isDeparture);
            WarpsMessageKey titleKey = isDeparture
                    ? WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_TITLE_DEPARTURE
                    : WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_TITLE_ARRIVAL;
            Inventory inventory = Bukkit.createInventory(holder, SIZE, text(viewer, titleKey));
            holder.attach(inventory);
            populate(inventory, viewer);
            player.openInventory(inventory);
        });
    }

    private void populate(Inventory inventory, PlayerRef viewer) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        List<SoundOption> options = getOptions();
        for (int i = 0; i < Math.min(options.size(), OPTION_LIMIT); i++) {
            SoundOption opt = options.get(i);
            inventory.setItem(
                    i,
                    ItemBuilder.of(opt.material())
                            .name(text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_ENTRY_NAME,
                                    Map.of("sound", opt.displayName())))
                            .lore(List.of(text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_ENTRY_LORE,
                                    Map.of("sound", opt.soundName()))))
                            .build());
        }

        inventory.setItem(
                CUSTOM_SLOT,
                ItemBuilder.of(Material.ANVIL)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_CUSTOM_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_CUSTOM_LORE)))
                        .build());

        inventory.setItem(
                BACK_SLOT,
                ItemBuilder.of(Material.BARRIER)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                        .build());

        inventory.setItem(
                REMOVE_SLOT,
                ItemBuilder.of(Material.LAVA_BUCKET)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_REMOVE_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_SOUND_SELECTOR_REMOVE_LORE)))
                        .build());
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return miniMessage.deserialize(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }

    public List<SoundOption> getOptions() {
        return List.of(
                new SoundOption("minecraft:entity.enderman.teleport", Material.ENDER_PEARL, "Enderman Teleport"),
                new SoundOption("minecraft:entity.player.teleport", Material.CHORUS_FRUIT, "Player Teleport"),
                new SoundOption("minecraft:block.portal.travel", Material.OBSIDIAN, "Portal Travel"),
                new SoundOption("minecraft:block.note_block.chime", Material.NOTE_BLOCK, "Note Block Chime"),
                new SoundOption("minecraft:block.note_block.bell", Material.BELL, "Note Block Bell"),
                new SoundOption("minecraft:block.note_block.flute", Material.FEATHER, "Note Block Flute"),
                new SoundOption("minecraft:block.note_block.guitar", Material.STRING, "Note Block Guitar"),
                new SoundOption("minecraft:block.note_block.harp", Material.REDSTONE, "Note Block Harp"),
                new SoundOption("minecraft:block.beacon.activate", Material.BEACON, "Beacon Activate"),
                new SoundOption(
                        "minecraft:entity.experience_orb.pickup", Material.EXPERIENCE_BOTTLE, "Experience Pickup"),
                new SoundOption("minecraft:entity.firework_rocket.launch", Material.FIREWORK_ROCKET, "Firework Launch"),
                new SoundOption("minecraft:entity.lightning_bolt.thunder", Material.LIGHTNING_ROD, "Thunder Strike"),
                new SoundOption("minecraft:entity.wither.spawn", Material.WITHER_SKELETON_SKULL, "Wither Spawn"),
                new SoundOption("minecraft:block.anvil.use", Material.ANVIL, "Anvil Use"));
    }

    public record SoundOption(String soundName, Material material, String displayName) {}
}
