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
public final class WarpParticleSelectorView {

    /** The 27-slot (three-row) selector chest, with the option grid, custom-input, back and remove buttons. */
    static final int SIZE = 27;

    static final int OPTION_LIMIT = 18;
    static final int CUSTOM_SLOT = 18;
    static final int BACK_SLOT = 22;
    static final int REMOVE_SLOT = 26;

    private final Messages messages;
    private final Scheduler scheduler;
    private final MiniMessage miniMessage;

    public WarpParticleSelectorView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(
            Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner, boolean isDeparture) {
        scheduler.onEntity(viewer, () -> {
            WarpParticleSelectorHolder holder =
                    new WarpParticleSelectorHolder(viewer, warpName, warpOwner, isDeparture);
            WarpsMessageKey titleKey = isDeparture
                    ? WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_TITLE_DEPARTURE
                    : WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_TITLE_ARRIVAL;
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

        List<ParticleOption> options = getOptions();
        for (int i = 0; i < Math.min(options.size(), OPTION_LIMIT); i++) {
            ParticleOption opt = options.get(i);
            inventory.setItem(
                    i,
                    ItemBuilder.of(opt.material())
                            .name(text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_NAME,
                                    Map.of("particle", opt.displayName())))
                            .lore(List.of(text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_LORE,
                                    Map.of("particle", opt.particleName()))))
                            .build());
        }

        inventory.setItem(
                CUSTOM_SLOT,
                ItemBuilder.of(Material.ANVIL)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_LORE)))
                        .build());

        inventory.setItem(
                BACK_SLOT,
                ItemBuilder.of(Material.BARRIER)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                        .build());

        inventory.setItem(
                REMOVE_SLOT,
                ItemBuilder.of(Material.LAVA_BUCKET)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_LORE)))
                        .build());
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return miniMessage.deserialize(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }

    public List<ParticleOption> getOptions() {
        return List.of(
                new ParticleOption("minecraft:portal", Material.OBSIDIAN, "Portal"),
                new ParticleOption("minecraft:witch", Material.BREWING_STAND, "Witch"),
                new ParticleOption("minecraft:dragon_breath", Material.DRAGON_BREATH, "Dragon Breath"),
                new ParticleOption("minecraft:flame", Material.TORCH, "Flame"),
                new ParticleOption("minecraft:happy_villager", Material.EMERALD, "Happy Villager"),
                new ParticleOption("minecraft:heart", Material.RED_DYE, "Heart"),
                new ParticleOption("minecraft:cloud", Material.FEATHER, "Cloud"),
                new ParticleOption("minecraft:reverse_portal", Material.CRYING_OBSIDIAN, "Reverse Portal"),
                new ParticleOption("minecraft:totem_of_undying", Material.TOTEM_OF_UNDYING, "Totem"),
                new ParticleOption("minecraft:smoke", Material.CAMPFIRE, "Smoke"),
                new ParticleOption("minecraft:enchant", Material.ENCHANTING_TABLE, "Enchant"),
                new ParticleOption("minecraft:glow", Material.GLOW_INK_SAC, "Glow"),
                new ParticleOption("minecraft:soul", Material.SOUL_SOIL, "Soul"),
                new ParticleOption("minecraft:explosion", Material.TNT, "Explosion"));
    }

    public record ParticleOption(String particleName, Material material, String displayName) {}
}
