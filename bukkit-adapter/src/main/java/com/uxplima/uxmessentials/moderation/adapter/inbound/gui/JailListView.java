package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The jail-management list (capability B of the {@code /jail} GUI): a config-driven, paginated grid of every
 * defined jail name (the config jails merged with the DB-backed {@code /setjail} jails) drawn through the shared
 * {@link EntityListView}, plus a "create jail" button. Clicking a jail opens a small edit screen offering
 * re-anchor (save the jail at the staff member's current location) and delete; the create button opens an anvil
 * for a name and saves a new jail at the staff member's current location.
 *
 * <p>The name union is a DB read, so the open resolves it off the tick thread and hops back to the viewer's
 * entity thread to render. Re-anchoring and creating both read the viewer's own location <em>on the viewer's
 * thread</em> (a region-local read) before delegating to the audited {@code SetJail} use case; delete delegates
 * to {@code DelJail}. The view holds no domain logic — it threads the existing use cases the {@code /setjail}
 * and {@code /jail del} commands take.
 */
@NullMarked
public final class JailListView {

    private static final int EDIT_ROWS = 3;
    private static final int EDIT_BACK_SLOT = 18;
    private static final int EDIT_ANCHOR_SLOT = 11;
    private static final int EDIT_DELETE_SLOT = 15;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material ANCHOR_ICON = Material.COMPASS;
    private static final Material DELETE_ICON = Material.LAVA_BUCKET;
    private static final Material BACK_ICON = Material.ARROW;
    private static final Material CREATE_ICON = Material.ANVIL;
    private static final Material JAIL_ICON = Material.IRON_BARS;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final ModerationServices services;
    private final AnvilInput anvil;
    private final AtomicReference<List<String>> snapshot = new AtomicReference<>(List.of());
    private final EntityListView<String> view;

    public JailListView(
            GuiText guiText,
            Scheduler scheduler,
            ModerationServices services,
            AnvilInput anvil,
            EntityListLayout layout) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.services = Objects.requireNonNull(services, "services");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        Objects.requireNonNull(layout, "layout");
        this.view = EntityListView.<String>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(ModerationMessageKey.MOD_GUI_JAIL_LIST_TITLE)
                .navNames(ModerationMessageKey.MOD_GUI_JAIL_LIST_PREV, ModerationMessageKey.MOD_GUI_JAIL_LIST_NEXT)
                .entities(snapshot::get)
                .iconRenderer(this::icon)
                .onSelect((player, name) -> openEdit(player, BukkitRefs.toRef(player), name))
                .onCreate(ModerationMessageKey.MOD_GUI_JAIL_LIST_CREATE, this::promptCreate)
                .build();
    }

    /** Resolve the jail-name union off-thread, then open the list on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            snapshot.set(services.listJails().names());
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }

    private ItemStack icon(PlayerRef viewer, String name) {
        Map<String, String> placeholders = Map.of("jail", name);
        return ItemBuilder.of(JAIL_ICON)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_LIST_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_LIST_ENTRY_LORE, placeholders))
                .build();
    }

    /** The per-jail edit screen: re-anchor at the viewer's location, delete, or go back to the list. */
    private void openEdit(Player player, PlayerRef viewer, String name) {
        scheduler.onEntity(viewer, () -> build(viewer, player, name).open(player));
    }

    private SimpleGui build(PlayerRef viewer, Player player, String name) {
        Map<String, String> ph = Map.of("jail", name);
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_EDIT_TITLE, ph))
                .rows(EDIT_ROWS)
                .build();
        fill(gui);
        gui.set(EDIT_ANCHOR_SLOT, GuiItem.button(anchorIcon(viewer, ph), e -> reAnchor(player, viewer, name)));
        gui.set(EDIT_DELETE_SLOT, GuiItem.button(deleteIcon(viewer, ph), e -> delete(player, viewer, name)));
        gui.set(EDIT_BACK_SLOT, GuiItem.button(backIcon(viewer), e -> open(player, viewer)));
        return gui;
    }

    /** Re-anchor the jail at the viewer's current location, read here on the viewer's own region thread. */
    private void reAnchor(Player player, PlayerRef viewer, String name) {
        Position at = position(player);
        services.setJail().set(viewer, name, at);
        open(player, viewer);
    }

    private void delete(Player player, PlayerRef viewer, String name) {
        services.delJail().delete(viewer, name);
        open(player, viewer);
    }

    /** Open the anvil for a new jail name; a submission saves it at the viewer's current location. */
    private void promptCreate(Player player) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        scheduler.onEntity(
                viewer,
                () -> anvil.open(player, createPrompt(viewer), result -> {
                    if (result instanceof AnvilResult.Submitted submitted
                            && !submitted.text().isBlank()) {
                        createJail(player, viewer, submitted.text().strip());
                    } else {
                        open(player, viewer);
                    }
                }));
    }

    /**
     * Save a new jail at the viewer's current location, read here on the viewer's own region thread.
     * Package-private so the create path is unit-tested without driving a live anvil submission.
     */
    void createJail(Player player, PlayerRef viewer, String name) {
        scheduler.onEntity(viewer, () -> {
            services.setJail().set(viewer, name, position(player));
            open(player, viewer);
        });
    }

    private ItemStack anchorIcon(PlayerRef viewer, Map<String, String> ph) {
        return ItemBuilder.of(ANCHOR_ICON)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_EDIT_ANCHOR))
                .lore(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_EDIT_ANCHOR_LORE, ph))
                .build();
    }

    private ItemStack deleteIcon(PlayerRef viewer, Map<String, String> ph) {
        return ItemBuilder.of(DELETE_ICON)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_EDIT_DELETE))
                .lore(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_EDIT_DELETE_LORE, ph))
                .build();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(BACK_ICON)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_EDIT_BACK))
                .build();
    }

    private ItemStack createPrompt(PlayerRef viewer) {
        return ItemBuilder.of(CREATE_ICON)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_CREATE_PROMPT))
                .build();
    }

    private void fill(SimpleGui gui) {
        ItemStack filler = ItemBuilder.of(FILLER).name(Component.empty()).build();
        for (int slot = 0; slot < EDIT_ROWS * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }

    private static Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }
}
