package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitNpcSkins;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
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
 * The {@code /npc} management list: a config-driven, paginated grid of every stored NPC drawn through the shared
 * {@link EntityListView}, each icon showing the NPC's name, type, world, and position and opening
 * {@link NpcEditorView} on click. A create button opens an anvil for a new name and creates a fresh fake-player
 * NPC at the operator's feet (with the operator's own skin) through the existing {@code create} use case. The
 * view holds no domain logic — the entity supplier and the create call are the same ones the {@code /npc list}
 * and {@code /npc create} subcommands use.
 */
public final class NpcListView {

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final EntityListView<Npc> view;

    public NpcListView(
            GuiText guiText,
            Scheduler scheduler,
            NpcRepository repository,
            NpcServices services,
            AnvilInput anvil,
            EntityListLayout layout,
            NpcEditorView editor) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(anvil, "anvil");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(editor, "editor");
        this.view = EntityListView.<Npc>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(NpcMessageKey.NPC_GUI_LIST_TITLE)
                .navNames(NpcMessageKey.NPC_GUI_LIST_PREV, NpcMessageKey.NPC_GUI_LIST_NEXT)
                .entities(repository::all)
                .iconRenderer(this::icon)
                .onSelect((player, npc) -> editor.open(player, BukkitRefs.toRef(player), npc))
                .onCreate(NpcMessageKey.NPC_GUI_LIST_CREATE, player -> promptCreate(player, services, anvil, layout))
                .build();
    }

    /** Open the NPC list for {@code viewer} on their entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        view.open(player, viewer);
    }

    private ItemStack icon(PlayerRef viewer, Npc npc) {
        Position at = npc.location();
        Map<String, String> placeholders = Map.of(
                "name", npc.name().value(),
                "type", npc.entityType(),
                "world", at.world().name(),
                "x", Long.toString(Math.round(at.x())),
                "y", Long.toString(Math.round(at.y())),
                "z", Long.toString(Math.round(at.z())));
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(guiText.text(viewer, NpcMessageKey.NPC_GUI_LIST_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, NpcMessageKey.NPC_GUI_LIST_ENTRY_LORE, placeholders))
                .build();
    }

    private void promptCreate(Player player, NpcServices services, AnvilInput anvil, EntityListLayout layout) {
        ItemStack prompt = ItemBuilder.of(layout.createIcon())
                .name(guiText.text(BukkitRefs.toRef(player), NpcMessageKey.NPC_GUI_LIST_CREATE_PROMPT))
                .build();
        anvil.open(player, prompt, result -> handleCreate(player, services, result));
    }

    private void handleCreate(Player player, NpcServices services, AnvilResult result) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        if (!(result instanceof AnvilResult.Submitted submitted)
                || submitted.text().isBlank()) {
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
            return;
        }
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        NpcName name = NpcName.of(submitted.text());
        NpcSkin skin = BukkitNpcSkins.of(player).orElse(null);
        scheduler.async(() -> {
            services.create().create(viewer, name, at, skin);
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }
}
