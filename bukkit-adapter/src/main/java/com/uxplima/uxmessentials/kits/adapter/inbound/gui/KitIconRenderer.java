package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the display icons the {@code /kits} browse menu shows — one per kit and one per category — resolving
 * each icon's player-relative name, material, and lore against the viewer's permission, cooldown, one-time, and
 * affordability state. The renderer holds no menu or scheduler state: it reads the kit/category and the viewer
 * and returns an {@link ItemStack}, so it is a pure presentation collaborator the menu view delegates to. Every
 * line resolves from a {@link MessageKey} in the viewer's locale unless the kit carries its own override text,
 * and cost/cooldown placeholders (plus any PlaceholderAPI tokens) are substituted before MiniMessage parsing.
 */
@NullMarked
final class KitIconRenderer {

    private final Messages messages;
    private final KitAccess access;
    private final GuiLayout layout;
    private final MiniMessage miniMessage;

    KitIconRenderer(Messages messages, KitAccess access, GuiLayout layout, MiniMessage miniMessage) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.access = Objects.requireNonNull(access, "access");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
    }

    ItemStack categoryIcon(PlayerRef viewer, KitCategory category) {
        Component name =
                text(viewer, KitsMessageKey.KIT_MENU_CATEGORY_NAME, Map.of("category", category.displayName()));
        List<Component> loreLines = new ArrayList<>();
        if (!category.displayLore().isEmpty()) {
            for (String customLine : category.displayLore()) {
                loreLines.add(miniMessage.deserialize(customLine));
            }
        } else {
            loreLines.add(text(viewer, KitsMessageKey.KIT_MENU_CATEGORY_LORE, Map.of()));
        }

        Material mat = Material.BOOK;
        if (category.displayMaterial().isPresent()) {
            try {
                Material parsed =
                        Material.matchMaterial(category.displayMaterial().get().toUpperCase(java.util.Locale.ROOT));
                if (parsed != null && !parsed.isAir()) {
                    mat = parsed;
                }
            } catch (IllegalArgumentException absent) {
                // Keep default
            }
        }
        return ItemBuilder.of(mat).name(name).lore(loreLines).build();
    }

    ItemStack icon(PlayerRef viewer, KitDefinition kit) {
        return ItemBuilder.of(resolveMaterial(viewer, kit))
                .name(resolveName(viewer, kit))
                .lore(resolveLore(viewer, kit))
                .build();
    }

    private String formatDuration(java.time.Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds <= 0) {
            return "0s";
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private java.time.Duration remainingCooldown(PlayerRef viewer, KitDefinition kit) {
        var res = access.remaining(viewer, kit);
        if (res.isErr()) {
            return res.errorOrThrow();
        }
        return java.time.Duration.ZERO;
    }

    private String processPlaceholders(PlayerRef viewer, KitDefinition kit, String text) {
        String processed = text;
        processed = processed.replace("%cost%", kit.cost().amount().toPlainString());
        if (processed.contains("%cooldown%")) {
            processed = processed.replace("%cooldown%", formatDuration(remainingCooldown(viewer, kit)));
        }
        if (PlaceholderApiSupport.isPresent()) {
            processed = PlaceholderApiSupport.messageBridge(viewer.uuid()).apply(processed);
        }
        return processed;
    }

    /** The player-relative display state that selects which name/material/lore override a kit icon shows. */
    private enum DisplayState {
        NO_PERMISSION,
        CLAIMED,
        ON_COOLDOWN,
        UNAFFORDABLE,
        NORMAL
    }

    private DisplayState stateOf(PlayerRef viewer, KitDefinition kit) {
        if (!access.hasPermission(viewer, kit)) {
            return DisplayState.NO_PERMISSION;
        }
        if (access.hasClaimedOneTime(viewer, kit)) {
            return DisplayState.CLAIMED;
        }
        if (access.isOnCooldown(viewer, kit)) {
            return DisplayState.ON_COOLDOWN;
        }
        if (!access.canAfford(viewer, kit)) {
            return DisplayState.UNAFFORDABLE;
        }
        return DisplayState.NORMAL;
    }

    private Component resolveName(PlayerRef viewer, KitDefinition kit) {
        Optional<String> nameOpt =
                switch (stateOf(viewer, kit)) {
                    case NO_PERMISSION -> kit.noPermissionName();
                    case CLAIMED -> kit.claimedName();
                    case ON_COOLDOWN -> kit.cooldownName();
                    case UNAFFORDABLE -> kit.unaffordableName();
                    case NORMAL -> kit.displayName();
                };

        String rawName = nameOpt.orElseGet(() -> messages.resolve(
                viewer,
                KitsMessageKey.KIT_MENU_ENTRY_NAME,
                Map.of("kit", kit.id().value())));
        return miniMessage.deserialize(processPlaceholders(viewer, kit, rawName));
    }

    private Material resolveMaterial(PlayerRef viewer, KitDefinition kit) {
        Optional<String> matOpt =
                switch (stateOf(viewer, kit)) {
                    case NO_PERMISSION -> kit.noPermissionMaterial();
                    case CLAIMED -> kit.claimedMaterial();
                    case ON_COOLDOWN -> kit.cooldownMaterial();
                    case UNAFFORDABLE -> kit.unaffordableMaterial();
                    case NORMAL -> kit.displayMaterial();
                };

        if (matOpt.isPresent()) {
            try {
                Material mat = Material.matchMaterial(matOpt.get().toUpperCase(java.util.Locale.ROOT));
                if (mat != null && !mat.isAir()) {
                    return mat;
                }
            } catch (IllegalArgumentException absent) {
                // fallback
            }
        }

        // Fallback to default material resolution
        if (kit.items().isEmpty()) {
            return layout.fallbackIcon();
        }
        Material type = KitItemCodec.decode(kit.items().get(0)).getType();
        return type.isAir() ? layout.fallbackIcon() : type;
    }

    private List<Component> resolveLore(PlayerRef viewer, KitDefinition kit) {
        List<String> stateLore =
                switch (stateOf(viewer, kit)) {
                    case NO_PERMISSION -> kit.noPermissionLore();
                    case CLAIMED -> kit.claimedLore();
                    case ON_COOLDOWN -> kit.cooldownLore();
                    case UNAFFORDABLE -> kit.unaffordableLore();
                    case NORMAL -> List.of();
                };
        boolean hasOverride = !stateLore.isEmpty();
        List<String> rawLore = hasOverride ? stateLore : kit.displayLore();

        List<Component> lines = new ArrayList<>();
        if (!rawLore.isEmpty()) {
            for (String line : rawLore) {
                lines.add(miniMessage.deserialize(processPlaceholders(viewer, kit, line)));
            }
        }

        // Only append default status lore lines if we did not use a state override lore
        if (!hasOverride) {
            lines.add(text(
                    viewer,
                    KitsMessageKey.KIT_MENU_LORE_COOLDOWN,
                    Map.of("seconds", Long.toString(kit.cooldownSeconds()))));
            if (kit.isOneTime()) {
                lines.add(text(viewer, KitsMessageKey.KIT_MENU_LORE_ONETIME, Map.of()));
            }
            if (kit.hasCost()) {
                lines.add(text(
                        viewer,
                        KitsMessageKey.KIT_MENU_LORE_COST,
                        Map.of("amount", kit.cost().amount().toPlainString())));
            }
            lines.add(text(
                    viewer,
                    KitsMessageKey.KIT_MENU_LORE_CLAIMABLE,
                    Map.of("kit", kit.id().value())));
        }
        return lines;
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}
