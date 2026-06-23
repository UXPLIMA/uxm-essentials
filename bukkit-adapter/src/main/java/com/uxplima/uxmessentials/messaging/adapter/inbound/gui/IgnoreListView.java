package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The ignore-list manager (opened by {@code /ignore} with no arguments): a config-driven, paginated grid of the
 * players the viewer ignores, drawn through the shared {@link EntityListView}, each icon a clickable head that
 * un-ignores that player. A create button prompts for a name through the shared text-input seam and ignores that
 * player. The view holds no
 * domain logic — the un-ignore and the ignore route through the same {@code Unignore} / {@code Ignore} use cases
 * the {@code /unignore} and {@code /ignore <player>} verbs use, so a notifier line still fires and the store
 * stays consistent.
 *
 * <p>The owner's ignore list is a DB read, so the open resolves it off the tick thread into a snapshot and hops
 * back to the viewer's entity thread to render — mirroring the moderation list's open. A click un-ignores off
 * the tick thread and reopens, so the list always reflects the current store.
 */
@NullMarked
public final class IgnoreListView {

    private final Scheduler scheduler;
    private final IgnoreStore ignores;
    private final Ignore ignoreUseCase;
    private final Unignore unignoreUseCase;
    private final PlayerLookup players;
    private final TextInput textInput;
    private final GuiText guiText;
    private final AtomicReference<List<IgnoreEntry>> snapshot = new AtomicReference<>(List.of());
    private final EntityListView<IgnoreEntry> view;

    public IgnoreListView(
            GuiText guiText,
            Scheduler scheduler,
            IgnoreStore ignores,
            Ignore ignoreUseCase,
            Unignore unignoreUseCase,
            PlayerLookup players,
            TextInput textInput,
            EntityListLayout layout) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.ignoreUseCase = Objects.requireNonNull(ignoreUseCase, "ignoreUseCase");
        this.unignoreUseCase = Objects.requireNonNull(unignoreUseCase, "unignoreUseCase");
        this.players = Objects.requireNonNull(players, "players");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.view = EntityListView.<IgnoreEntry>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(Objects.requireNonNull(layout, "layout"))
                .title(MessagingMessageKey.GUI_IGNORE_TITLE)
                .navNames(MessagingMessageKey.GUI_IGNORE_PREV, MessagingMessageKey.GUI_IGNORE_NEXT)
                .entities(snapshot::get)
                .iconRenderer(this::icon)
                .onSelect(this::unignore)
                .onCreate(MessagingMessageKey.GUI_IGNORE_ADD, this::promptAdd)
                .build();
    }

    /** Resolve the viewer's ignore list off-thread, then open the list on their entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            snapshot.set(ignores.load(viewer).entries());
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }

    private ItemStack icon(PlayerRef viewer, IgnoreEntry entry) {
        Map<String, String> placeholders = Map.of("player", entry.ignored().name());
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(guiText.text(viewer, MessagingMessageKey.GUI_IGNORE_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, MessagingMessageKey.GUI_IGNORE_ENTRY_LORE, placeholders))
                .build();
    }

    private void unignore(Player player, IgnoreEntry entry) {
        PlayerRef owner = BukkitRefs.toRef(player);
        scheduler.async(() -> {
            unignoreUseCase.unignore(owner, entry.ignored());
            open(player, owner);
        });
    }

    private void promptAdd(Player player) {
        PlayerRef owner = BukkitRefs.toRef(player);
        textInput.prompt(
                player,
                owner,
                InputRequest.of("messaging.ignore-add", MessagingMessageKey.GUI_IGNORE_ADD_PROMPT),
                name -> addByName(player, name),
                () -> open(player, owner));
    }

    /**
     * Resolve {@code name} to an online player and ignore them through the use case, then reopen the list. An
     * unknown name reopens the list unchanged (the same online-resolution the {@code /ignore <player>} verb
     * applies; the self-check and idempotency are the use case's). The input seam routes a submitted line here; it
     * is also the seam a test drives directly without firing a live prompt.
     */
    void addByName(Player player, String name) {
        PlayerRef owner = BukkitRefs.toRef(player);
        Optional<PlayerRef> target = players.findOnlineByName(name);
        scheduler.async(() -> {
            target.ifPresent(ref -> ignoreUseCase.ignore(owner, ref));
            open(player, owner);
        });
    }
}
