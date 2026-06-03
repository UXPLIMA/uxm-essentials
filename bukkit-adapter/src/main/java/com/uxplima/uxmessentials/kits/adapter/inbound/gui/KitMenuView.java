package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the read-only {@code /kits} browse menu as a uxmLib {@link PaginatedGui}: one display icon per kit the
 * player may claim, paged through the menu's content slots with previous/next buttons pinned in the reserved
 * bottom row. The kit list is the {@code ListKits.available} filter the chat list also uses, so the menu never
 * advertises a kit the player can no longer take. Each icon shows the kit's name and its cost/cooldown/one-time
 * detail as lore — every line resolved from a {@link MessageKey} in the viewer's locale, never an inline
 * literal. Clicking an icon claims that kit through the same {@link ClaimKit} use case the {@code /kit}
 * command drives — the view adds no claim, cooldown, or cost logic of its own; ClaimKit gates the claim and
 * sends the result message — and then closes the menu.
 *
 * <p>An icon's material is the kit's first item type (decoded through {@link KitItemCodec}), falling back to a
 * chest for an empty kit. {@link #open} touches the live player, so the caller schedules it on the viewer's
 * entity thread through the kernel {@link Scheduler}; a claim grants items into the live inventory, so it runs
 * on the viewer's entity thread through that same scheduler.
 */
@NullMarked
public final class KitMenuView {

    private static final int MENU_ROWS = 6;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final Material FALLBACK_ICON = Material.CHEST;

    private final Messages messages;
    private final Scheduler scheduler;
    private final ClaimKit claimKit;
    private final MiniMessage miniMessage;

    public KitMenuView(Messages messages, Scheduler scheduler, ClaimKit claimKit) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.claimKit = Objects.requireNonNull(claimKit, "claimKit");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the browse menu listing {@code kits} for {@code player}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, List<KitDefinition> kits) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kits, "kits");
        List<KitDefinition> snapshot = List.copyOf(kits);
        scheduler.onEntity(viewer, () -> {
            PaginatedGui gui =
                    Guis.paginated().title(title(viewer)).rows(MENU_ROWS).build();
            gui.populate(
                    snapshot,
                    ItemPopulator.of(kit -> icon(viewer, kit), (kit, event) -> onIconClick(player, viewer, gui, kit)));
            gui.set(PREV_SLOT, GuiItem.previousPage(gui, navIcon(viewer, KitsMessageKey.KIT_MENU_PREV)));
            gui.set(NEXT_SLOT, GuiItem.nextPage(gui, navIcon(viewer, KitsMessageKey.KIT_MENU_NEXT)));
            gui.open(player);
        });
    }

    /**
     * Claim the clicked kit and close the menu. The kit identity comes from the bound element, never from
     * re-reading the clicked icon. The claim grants items into the live inventory, so it runs on the viewer's
     * entity thread; {@link ClaimKit} gates the claim and sends the success or failure message itself.
     */
    private void onIconClick(Player player, PlayerRef viewer, PaginatedGui gui, KitDefinition kit) {
        scheduler.onEntity(viewer, () -> {
            claimKit.claim(viewer, kit.id());
            gui.close(player);
        });
    }

    private Component title(PlayerRef viewer) {
        return text(viewer, KitsMessageKey.KIT_MENU_TITLE, Map.of());
    }

    private ItemStack icon(PlayerRef viewer, KitDefinition kit) {
        return ItemBuilder.of(material(kit))
                .name(text(
                        viewer,
                        KitsMessageKey.KIT_MENU_ENTRY_NAME,
                        Map.of("kit", kit.id().value())))
                .lore(lore(viewer, kit))
                .build();
    }

    private List<Component> lore(PlayerRef viewer, KitDefinition kit) {
        List<Component> lines = new ArrayList<>();
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
        return lines;
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(Material.ARROW).name(text(viewer, key, Map.of())).build();
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }

    private static Material material(KitDefinition kit) {
        if (kit.items().isEmpty()) {
            return FALLBACK_ICON;
        }
        Material type = KitItemCodec.decode(kit.items().get(0)).getType();
        return type.isAir() ? FALLBACK_ICON : type;
    }
}
