package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankAction;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the bank members menu with the menu engine and opens it. A paginated grid of one player head per member
 * of a shared bank, showing the member's name and role. A right click removes that member through the same
 * {@link BankService} the {@code /bank removemember} command takes, then reopens the refreshed list; the add button
 * opens the name prompt, and the back button returns to the engine {@link BankActionsMenu} hub through the
 * {@link BankNavigation} supplier.
 *
 * <p>The open re-fetches the bank fresh and resolves its members off the tick thread (all repository/domain reads,
 * no Bukkit call) and hands the fresh bank in as the menu subject; the {@code economy:bank-members} list source only
 * reads that subject. The {@code bank_member} / {@code bank_member_role} placeholders fill each head from the bound
 * member, prefixed so they never collide with another menu's fields, and the {@code bank_members_title} title
 * argument reads the subject's bank name. The {@code economy:can-add-member} view condition gates the add button to a
 * member who may add, mirroring the old menu. Every label resolves from the economy catalog, so no user-facing text
 * lives here. The geometry mirrors the original list: a grid across the top five rows, the prev/next arrows at the
 * corners of the bottom row, and the back/add buttons between them.
 */
@NullMarked
public final class BankMembersMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-bank-members";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-bank-members.conf";

    private final Menus menus;
    private final BankService bankService;
    private final TextInput textInput;
    private final Scheduler scheduler;
    private final PlayerLookup players;
    private final Messages messages;
    private final Supplier<BankNavigation> navigation;

    public BankMembersMenu(
            Menus menus,
            BankService bankService,
            TextInput textInput,
            Scheduler scheduler,
            PlayerLookup players,
            Messages messages,
            Supplier<BankNavigation> navigation) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.bankService = Objects.requireNonNull(bankService, "bankService");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    /** Register the list source, the per-member placeholders, the add-gate condition, the actions, and the spec. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("economy:bank-members", ctx -> subject(ctx).members());
        bindings.placeholder("bank_members_title", ctx -> subject(ctx).name());
        bindings.placeholder("bank_member", ctx -> member(ctx).player().name());
        bindings.placeholder("bank_member_role", ctx -> member(ctx).role().name());
        bindings.condition(
                "economy:can-add-member",
                (ctx, args) -> subject(ctx).hasPermission(ctx.viewer(), BankAction.ADD_MEMBER));
        bindings.action("economy:remove-member", this::removeMember);
        bindings.action("economy:add-member", this::addMember);
        bindings.action("economy:bank-back", this::back);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Re-fetch the bank fresh off the tick thread, then open the members list for {@code player}. */
    public void open(Player player, SharedBank bank) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(bank, "bank");
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler.async(() -> {
            Optional<SharedBank> fresh = bankService.getBank(bank.id());
            if (fresh.isEmpty()) {
                scheduler.onEntity(
                        viewer, () -> navigation.get().bankActionsView().open(player, bank));
                return;
            }
            menus.open(viewer, SPEC_ID, fresh.get());
        });
    }

    /** Right-click a head: remove that member through the bank service off-thread, then reopen the refreshed list. */
    private void removeMember(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        SharedBank bank = ctx.subject(SharedBank.class);
        BankMember member = ctx.entry(BankMember.class);
        scheduler.async(() -> {
            Result<Unit, BankError> res = bankService.removeMember(viewer, bank.id(), member.player());
            scheduler.onEntity(viewer, () -> {
                if (res.isOk()) {
                    player.sendMessage(text(
                            viewer,
                            EconomyMessageKey.BANK_MEMBERS_GUI_REMOVED,
                            Map.of("player", member.player().name())));
                } else {
                    player.sendMessage(text(viewer, EconomyMessageKey.BANK_MEMBERS_GUI_REMOVE_FAILED, Map.of()));
                }
                open(player, bank);
            });
        });
    }

    /** Left-click the add button: open the bespoke name prompt, exactly as the old add button did. */
    private void addMember(MenuActionContext ctx) {
        promptAddMember(ctx.player(), ctx.subject(SharedBank.class));
    }

    /** Left-click the back button: return to the bespoke bank-actions hub on the viewer's entity thread. */
    private void back(MenuActionContext ctx) {
        navigation.get().bankActionsView().open(ctx.player(), ctx.subject(SharedBank.class));
    }

    private SharedBank subject(MenuContext ctx) {
        return ctx.subject(SharedBank.class);
    }

    private BankMember member(MenuContext ctx) {
        return ctx.entry(BankMember.class);
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders)).decoration(TextDecoration.ITALIC, false);
    }

    private void promptAddMember(Player player, SharedBank bank) {
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("bank.member-add", EconomyMessageKey.BANK_MEMBERS_GUI_ADD_PROMPT),
                targetName -> {
                    String cleanName = targetName.trim();
                    if (cleanName.isEmpty()) {
                        player.sendMessage(text(viewer, EconomyMessageKey.BANK_MEMBERS_GUI_NAME_EMPTY, Map.of()));
                        open(player, bank);
                        return;
                    }
                    submitAdd(player, viewer, bank, cleanName);
                },
                () -> open(player, bank));
    }

    private void submitAdd(Player player, PlayerRef viewer, SharedBank bank, String cleanName) {
        scheduler.async(() -> {
            Optional<PlayerRef> resolved = players.findByName(cleanName);
            if (resolved.isEmpty()) {
                scheduler.onEntity(viewer, () -> {
                    player.sendMessage(text(viewer, EconomyMessageKey.BANK_MEMBERS_GUI_ADD_FAILED, Map.of()));
                    open(player, bank);
                });
                return;
            }
            Result<Unit, BankError> res = bankService.addMember(viewer, bank.id(), resolved.get(), BankRole.MEMBER);
            scheduler.onEntity(viewer, () -> {
                if (res.isOk()) {
                    player.sendMessage(
                            text(viewer, EconomyMessageKey.BANK_MEMBERS_GUI_ADDED, Map.of("player", cleanName)));
                } else {
                    player.sendMessage(text(viewer, EconomyMessageKey.BANK_MEMBERS_GUI_ADD_FAILED, Map.of()));
                }
                open(player, bank);
            });
        });
    }
}
