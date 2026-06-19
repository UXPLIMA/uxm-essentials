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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
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

    private final Messages messages;
    private final Scheduler scheduler;
    private final FixedMenuLayout layout;

    public WarpParticleSelectorView(Messages messages, Scheduler scheduler, FixedMenuLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** The shipped geometry: the option grid limit plus the custom/back/remove buttons, externalised to conf. */
    public static FixedMenuLayout defaultLayout() {
        return FixedMenuLayout.builder(3, Material.GRAY_STAINED_GLASS_PANE)
                .slotOnly("option-limit", 18)
                .element("custom", 18, Material.ANVIL)
                .element("back", 22, Material.BARRIER)
                .element("remove", 26, Material.LAVA_BUCKET)
                .build();
    }

    /** The three-row chest's slot count: the configured row count times the nine slots per row. */
    private int size() {
        return layout.rows() * 9;
    }

    int optionLimit() {
        return layout.slot("option-limit");
    }

    int customSlot() {
        return layout.slot("custom");
    }

    int backSlot() {
        return layout.slot("back");
    }

    int removeSlot() {
        return layout.slot("remove");
    }

    public void open(
            Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner, boolean isDeparture) {
        scheduler.onEntity(viewer, () -> {
            WarpParticleSelectorHolder holder =
                    new WarpParticleSelectorHolder(viewer, warpName, warpOwner, isDeparture);
            WarpsMessageKey titleKey = isDeparture
                    ? WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_TITLE_DEPARTURE
                    : WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_TITLE_ARRIVAL;
            Inventory inventory = Bukkit.createInventory(holder, size(), text(viewer, titleKey));
            holder.attach(inventory);
            populate(inventory, viewer);
            player.openInventory(inventory);
        });
    }

    private void populate(Inventory inventory, PlayerRef viewer) {
        ItemStack filler =
                ItemBuilder.of(layout.fillerMaterial()).name(Component.empty()).build();
        int size = size();
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        List<ParticleOption> options = getOptions();
        for (int i = 0; i < Math.min(options.size(), optionLimit()); i++) {
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
                customSlot(),
                ItemBuilder.of(layout.material("custom"))
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_LORE)))
                        .build());

        inventory.setItem(
                backSlot(),
                ItemBuilder.of(layout.material("back"))
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                        .build());

        inventory.setItem(
                removeSlot(),
                ItemBuilder.of(layout.material("remove"))
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_LORE)))
                        .build());
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
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
