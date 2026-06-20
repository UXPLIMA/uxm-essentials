package com.uxplima.uxmessentials.holograms.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.item.ItemBuilder;

/**
 * The {@code /hologram} management list: a config-driven, paginated grid of every stored hologram drawn through
 * the shared {@link EntityListView}, each icon showing the hologram's name, world, and line count and opening
 * {@link HologramEditorView} on click. A create button opens an anvil for a new name and creates a fresh text
 * hologram at the operator's feet through the existing {@code create} use case. The view holds no domain logic —
 * the entity supplier and the create call are the same ones the {@code /hologram list} and {@code /hologram
 * create} subcommands use.
 */
public final class HologramListView {

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final EntityListView<Hologram> view;

    public HologramListView(
            GuiText guiText,
            Scheduler scheduler,
            HologramRepository repository,
            com.uxplima.uxmessentials.holograms.adapter.HologramServices services,
            AnvilInput anvil,
            EntityListLayout layout,
            HologramEditorView editor) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(anvil, "anvil");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(editor, "editor");
        this.view = EntityListView.<Hologram>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(HologramsMessageKey.HOLOGRAM_GUI_LIST_TITLE)
                .navNames(HologramsMessageKey.HOLOGRAM_GUI_LIST_PREV, HologramsMessageKey.HOLOGRAM_GUI_LIST_NEXT)
                .entities(repository::all)
                .iconRenderer(this::icon)
                .onSelect((player, holo) -> editor.open(player, BukkitRefs.toRef(player), holo))
                .onCreate(
                        HologramsMessageKey.HOLOGRAM_GUI_LIST_CREATE,
                        player -> promptCreate(player, services, anvil, layout))
                .build();
    }

    /** Open the hologram list for {@code viewer} on their entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        view.open(player, viewer);
    }

    private ItemStack icon(PlayerRef viewer, Hologram holo) {
        Map<String, String> placeholders = Map.of(
                "name", holo.name().value(),
                "world", holo.location().world().name(),
                "lines", Integer.toString(holo.lineCount()));
        return ItemBuilder.of(Material.ARMOR_STAND)
                .name(guiText.text(viewer, HologramsMessageKey.HOLOGRAM_GUI_LIST_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, HologramsMessageKey.HOLOGRAM_GUI_LIST_ENTRY_LORE, placeholders))
                .build();
    }

    private void promptCreate(
            Player player,
            com.uxplima.uxmessentials.holograms.adapter.HologramServices services,
            AnvilInput anvil,
            EntityListLayout layout) {
        ItemStack prompt = ItemBuilder.of(layout.createIcon())
                .name(guiText.text(BukkitRefs.toRef(player), HologramsMessageKey.HOLOGRAM_GUI_LIST_CREATE_PROMPT))
                .build();
        anvil.open(player, prompt, result -> handleCreate(player, services, result));
    }

    private void handleCreate(
            Player player, com.uxplima.uxmessentials.holograms.adapter.HologramServices services, AnvilResult result) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        if (!(result instanceof AnvilResult.Submitted submitted)
                || submitted.text().isBlank()) {
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
            return;
        }
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        HologramName name = HologramName.of(submitted.text());
        // A new text hologram needs at least one non-blank line; the name reads as a sensible placeholder the
        // operator immediately edits in the line list.
        scheduler.async(() -> {
            services.create().create(viewer, name, at, HologramLine.of(name.value()));
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }
}
