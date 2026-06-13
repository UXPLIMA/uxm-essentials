package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.InviteToHome;
import com.uxplima.uxmessentials.homes.application.ListHomeInvites;
import com.uxplima.uxmessentials.homes.application.UninviteFromHome;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The invited-players menu, opened from a home's {@link HomeActionView}. A {@link PaginatedGui} of the players
 * the owner has invited to one of their homes; each entry resolves a UUID to a name through the kernel
 * {@link PlayerLookup} and revokes that invite on click. An add button opens a vanilla anvil to type a player
 * name to invite, and a back button returns to the action menu. Every visible string resolves from a
 * {@link MessageKey} in the viewer's locale, never an inline literal.
 *
 * <p>Every invite read or write hits the DB, so it runs off the tick thread through the kernel
 * {@link Scheduler}; the menu builds and opens on the viewer's entity thread only after the invite set has been
 * loaded async. The add and revoke decision branches are extracted into {@link #handleAddInput} and
 * {@link #revoke} so they can be unit-tested without driving a live anvil or inventory.
 */
@NullMarked
public final class InvitedPlayersMenu {

    private final Messages messages;
    private final Scheduler scheduler;
    private final ListHomeInvites listInvites;
    private final InviteToHome inviteToHome;
    private final UninviteFromHome uninviteFromHome;
    private final PlayerLookup players;
    private final HomeNotifier notifier;
    private final AnvilInput anvil;
    private final InvitesMenuLayout layout;
    private final MiniMessage miniMessage;

    public InvitedPlayersMenu(
            Messages messages,
            Scheduler scheduler,
            ListHomeInvites listInvites,
            InviteToHome inviteToHome,
            UninviteFromHome uninviteFromHome,
            PlayerLookup players,
            HomeNotifier notifier,
            AnvilInput anvil,
            InvitesMenuLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.listInvites = Objects.requireNonNull(listInvites, "listInvites");
        this.inviteToHome = Objects.requireNonNull(inviteToHome, "inviteToHome");
        this.uninviteFromHome = Objects.requireNonNull(uninviteFromHome, "uninviteFromHome");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the menu for {@code home}; {@code onBack} reopens the action menu from the back button. */
    public void open(Player player, PlayerRef viewer, Home home, Runnable onBack) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(onBack, "onBack");
        // The invite read hits the DB; resolve it off-thread, then build and open on the viewer's entity thread.
        scheduler.async(() -> {
            List<InvitedPlayer> entries = resolveEntries(home);
            scheduler.onEntity(viewer, () -> buildAndOpen(player, viewer, home, onBack, entries));
        });
    }

    private List<InvitedPlayer> resolveEntries(Home home) {
        Set<UUID> invited = listInvites.of(home.owner(), home.slot());
        List<InvitedPlayer> entries = new ArrayList<>();
        for (UUID uuid : invited) {
            String name = players.findByUuid(uuid).map(PlayerRef::name).orElse(uuid.toString());
            entries.add(new InvitedPlayer(uuid, name));
        }
        entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return entries;
    }

    private void buildAndOpen(
            Player player, PlayerRef viewer, Home home, Runnable onBack, List<InvitedPlayer> entries) {
        PaginatedGui gui = Guis.paginated()
                .title(text(viewer, HomesMessageKey.HOME_INVITES_TITLE, Map.of()))
                .rows(layout.rows())
                .contentSlots(layout.contentSlots())
                .build();
        fill(gui);
        if (entries.isEmpty()) {
            gui.set(layout.contentSlots().get(0), GuiItem.display(emptyIcon(viewer)));
        } else {
            for (InvitedPlayer entry : entries) {
                gui.addPageItem(GuiItem.button(
                        entryIcon(viewer, entry),
                        e -> revoke(player, viewer, home, entry.uuid(), entry.name(), onBack)));
            }
        }
        pinNavigation(player, viewer, gui, home, onBack);
        gui.open(player);
    }

    private void pinNavigation(Player player, PlayerRef viewer, PaginatedGui gui, Home home, Runnable onBack) {
        gui.set(layout.prevSlot(), GuiItem.previousPage(gui, navIcon(viewer, HomesMessageKey.HOME_INVITES_PREV)));
        gui.set(layout.nextSlot(), GuiItem.nextPage(gui, navIcon(viewer, HomesMessageKey.HOME_INVITES_NEXT)));
        gui.set(layout.addSlot(), GuiItem.button(addIcon(viewer), e -> promptAdd(player, viewer, home, onBack)));
        gui.set(layout.backSlot(), GuiItem.button(backIcon(viewer), e -> onBack.run()));
    }

    /** Open the anvil prompt for a player name; the submission flows through {@link #handleAddInput}. */
    private void promptAdd(Player player, PlayerRef viewer, Home home, Runnable onBack) {
        scheduler.onEntity(
                viewer,
                () -> anvil.open(player, addPrompt(viewer), result -> {
                    if (result instanceof AnvilResult.Submitted submitted) {
                        handleAddInput(player, viewer, home, onBack, submitted.text());
                    } else {
                        open(player, viewer, home, onBack);
                    }
                }));
    }

    /**
     * Resolve the typed name and invite that player to {@code home}, then refresh the menu. A name the server
     * cannot resolve tells the viewer it was unknown and reopens unchanged — no invite is added. Package-private
     * so the resolve-then-invite decision can be unit-tested without driving a live anvil.
     */
    void handleAddInput(Player player, PlayerRef viewer, Home home, Runnable onBack, String input) {
        String name = input.strip();
        Optional<PlayerRef> resolved = players.findByName(name);
        if (resolved.isEmpty()) {
            notifier.send(viewer, HomesMessageKey.HOME_INVITES_UNKNOWN_PLAYER, Map.of("player", name));
            open(player, viewer, home, onBack);
            return;
        }
        PlayerRef invited = resolved.get();
        scheduler.async(() -> {
            inviteToHome.invite(home.owner(), home.slot(), invited);
            scheduler.onEntity(viewer, () -> open(player, viewer, home, onBack));
        });
    }

    /**
     * Revoke {@code invited}'s access to {@code home} off-thread, then refresh the menu on the viewer's entity
     * thread. Package-private so the revoke-then-refresh branch can be unit-tested without a live inventory.
     */
    void revoke(Player player, PlayerRef viewer, Home home, UUID invited, String name, Runnable onBack) {
        scheduler.async(() -> {
            uninviteFromHome.uninvite(home.owner(), home.slot(), new PlayerRef(invited, name));
            scheduler.onEntity(viewer, () -> open(player, viewer, home, onBack));
        });
    }

    private void fill(PaginatedGui gui) {
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            if (!layout.contentSlots().contains(slot)) {
                gui.set(slot, GuiItem.display(filler));
            }
        }
    }

    private ItemStack entryIcon(PlayerRef viewer, InvitedPlayer entry) {
        return ItemBuilder.of(layout.entryMaterial())
                .name(text(viewer, HomesMessageKey.HOME_INVITES_ENTRY_NAME, Map.of("player", entry.name())))
                .lore(List.of(text(viewer, HomesMessageKey.HOME_INVITES_ENTRY_LORE, Map.of())))
                .build();
    }

    private ItemStack emptyIcon(PlayerRef viewer) {
        return ItemBuilder.of(layout.entryMaterial())
                .name(text(viewer, HomesMessageKey.HOME_INVITES_EMPTY_NAME, Map.of()))
                .build();
    }

    private ItemStack addIcon(PlayerRef viewer) {
        return ItemBuilder.of(layout.addMaterial())
                .name(text(viewer, HomesMessageKey.HOME_INVITES_ADD_NAME, Map.of()))
                .lore(List.of(text(viewer, HomesMessageKey.HOME_INVITES_ADD_LORE, Map.of())))
                .build();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(layout.backMaterial())
                .name(text(viewer, HomesMessageKey.HOME_INVITES_BACK, Map.of()))
                .build();
    }

    private ItemStack addPrompt(PlayerRef viewer) {
        return ItemBuilder.of(layout.addMaterial())
                .name(text(viewer, HomesMessageKey.HOME_INVITES_ADD_PROMPT, Map.of()))
                .build();
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(layout.navMaterial())
                .name(text(viewer, key, Map.of()))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }

    /** One invited player resolved to a display name for the menu. */
    private record InvitedPlayer(UUID uuid, String name) {}
}
