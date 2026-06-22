package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.NullMarked;

/**
 * Assembles the {@code /jail} management GUI's three capabilities and threads the navigation between them: the
 * jail-a-player flow ({@link JailGuiFlow}, capability A), the jail-list manager ({@link JailListView},
 * capability B), and the jailed-players release list ({@link JailedPlayersView}, capability C). The hub is the
 * jail flow's player picker carrying two footer buttons — [Jails] opening the jail list, [Jailed players]
 * opening the release list — so the operator reaches every capability from the one screen {@code /jail} opens.
 *
 * <p>The views are constructed once here and reused for every viewer. The footer buttons read the live player at
 * click time (re-deriving a {@link PlayerRef}) so a hub built once still routes each click to the clicker. The
 * three openers are exposed individually too, for the {@code /jails} and {@code /jailedplayers} bare-root
 * commands that jump straight to one capability. Layouts come from the module's {@code gui/*.conf}
 * (operator-editable, code default otherwise); every visible string is a catalog key.
 */
@NullMarked
public final class JailGuiViews {

    private static final String MODULE = "moderation";

    /** The jail-list create button sits in the bottom row; a paginated default puts nav at 45/53, create at 49. */
    private static final int CREATE_SLOT = 49;

    private final JailGuiFlow flow;
    private final JailListView jailList;
    private final JailedPlayersView jailedPlayers;

    private JailGuiViews(JailGuiFlow flow, JailListView jailList, JailedPlayersView jailedPlayers) {
        this.flow = flow;
        this.jailList = jailList;
        this.jailedPlayers = jailedPlayers;
    }

    /** Build the three jail views over the existing use cases, the shared pickers, and the module's GUI layouts. */
    public static JailGuiViews create(
            GuiText guiText,
            Scheduler scheduler,
            ModerationServices services,
            ModerationRepository repository,
            PlayerLookup players,
            PlayerPickerView picker,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.DurationPickerView durations,
            AnvilInput anvil,
            Messages messages,
            MessageSink sink,
            Clock clock,
            GuiLayouts layouts) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(picker, "picker");
        Objects.requireNonNull(durations, "durations");
        Objects.requireNonNull(anvil, "anvil");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(layouts, "layouts");

        EntityListLayout listLayout = layouts.loadEntityList(MODULE, "jail-list", jailListCodeDefault());
        EntityListLayout jailedLayout = layouts.loadEntityList(MODULE, "jailed-players", jailedCodeDefault());

        JailListView jailList = new JailListView(guiText, scheduler, services, anvil, listLayout);
        JailedPlayersView jailedPlayers =
                new JailedPlayersView(guiText, scheduler, services, repository, players, clock, jailedLayout);
        JailGuiFlow flow = new JailGuiFlow(guiText, scheduler, services, picker, durations, messages, sink);
        return new JailGuiViews(flow, jailList, jailedPlayers);
    }

    /** Open the hub — the jail-a-player picker with the [Jails] and [Jailed players] footer buttons. */
    public void openHub(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        flow.open(player, viewer, footers());
    }

    /** Open the jail-list manager directly (the {@code /jails} bare-root opener). */
    public void openJailList(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        jailList.open(player, viewer);
    }

    /** Open the jailed-players release list directly (the {@code /jailedplayers} bare-root opener). */
    public void openJailedPlayers(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        jailedPlayers.open(player, viewer);
    }

    /** The hub's two footer buttons, re-deriving the clicker's ref so a hub built once routes each click. */
    private JailGuiFlow.Footers footers() {
        return new JailGuiFlow.Footers(List.of(
                new PlayerPickerView.FooterButton(
                        ModerationMessageKey.MOD_GUI_JAIL_FOOTER_JAILS,
                        ModerationMessageKey.MOD_GUI_JAIL_FOOTER_JAILS_LORE,
                        Material.IRON_BARS,
                        clicker -> jailList.open(clicker, BukkitRefs.toRef(clicker))),
                new PlayerPickerView.FooterButton(
                        ModerationMessageKey.MOD_GUI_JAIL_FOOTER_JAILED,
                        ModerationMessageKey.MOD_GUI_JAIL_FOOTER_JAILED_LORE,
                        Material.PLAYER_HEAD,
                        clicker -> jailedPlayers.open(clicker, BukkitRefs.toRef(clicker)))));
    }

    private static EntityListLayout jailListCodeDefault() {
        return EntityListLayout.withCreate(Material.IRON_BARS, CREATE_SLOT, Material.ANVIL);
    }

    private static EntityListLayout jailedCodeDefault() {
        return EntityListLayout.paginatedDefault(Material.PLAYER_HEAD);
    }
}
