package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.JailGuiViews;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationGuiViews;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentGuiFlow;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds the moderation context's Brigadier command surface (docs/10-feature-modules.md §15.9) as
 * {@link CommandRegistration}s over the constructed {@link ModerationServices}. Collected in one greppable
 * table so the literal/permission pairing matches the permissions reference and the kernel's
 * {@code ModerationCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each. The
 * single-argument verbs ({@code /unmute /unjail /freeze /unfreeze /seen /seenip /warns}) share the
 * {@link SimpleTargetCommand} shape, each binding its own node and use-case action. {@code /alts} is the
 * off-tick alt-detection read on the seen node and {@code /commandspy} is the staff command-watch toggle.
 */
@NullMarked
public final class ModerationCommands {

    private ModerationCommands() {}

    /**
     * Every moderation command, in surface order. {@code guiFlow} is the bare-command picker→confirm opener the
     * six sanction commands ({@code /ban}, {@code /mute}, {@code /tempban}, {@code /tempmute}, {@code /warn},
     * {@code /banip}) expose through {@code guiRoot()}; it is {@code null} only in a configuration that built no
     * GUI flow (every other command ignores it).
     *
     * <p>The read-only GUI collaborators thread to the matching command's bare-root opener: {@code guiViews}
     * (the active-punishments list) to {@code /banlist} and (with {@code picker}) the history view to
     * {@code /banhistory}, and {@code guiText} + {@code textInput} to the {@code /checkban}/{@code /checkmute} name
     * prompts. The {@code jailGui} threads to the three jail commands' bare roots ({@code /jail} opens the hub,
     * {@code /jails} the jail-list manager, {@code /jailedplayers} the release list). Each is {@code null} only
     * when no GUI surface was built, in which case the owning command exposes no opener and keeps its raw chat
     * behaviour.
     */
    public static List<CommandRegistration> all(
            ModerationServices services,
            Messages messages,
            MessageSink sink,
            Scheduler scheduler,
            boolean silentByDefault,
            @Nullable PunishmentGuiFlow guiFlow,
            @Nullable ModerationGuiViews guiViews,
            @Nullable PlayerPickerView picker,
            @Nullable GuiText guiText,
            @Nullable TextInput textInput,
            @Nullable JailGuiViews jailGui) {
        return List.of(
                new MuteCommand(services, messages, sink, silentByDefault, guiFlow),
                new TempmuteCommand(services, messages, sink, silentByDefault, guiFlow),
                target(
                        "unmute",
                        "uxmessentials.moderation.unmute",
                        "Lift a player's mute",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.unmute().unmute(a, t)),
                new JailCommand(services, messages, sink, jailGui),
                target(
                        "unjail",
                        "uxmessentials.moderation.unjail",
                        "Release a jailed player",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.unjail().unjail(a, t)),
                new ToggleJailCommand(services, messages, sink),
                new JailsCommand(services, messages, sink, scheduler, jailGui),
                new JailedPlayersCommand(services, messages, sink, scheduler, jailGui),
                new SetJailCommand(services, messages, sink),
                new TempbanCommand(services, messages, sink, silentByDefault, guiFlow),
                new BanCommand(services, messages, sink, silentByDefault, guiFlow),
                new UnbanCommand(services, messages, sink),
                new BanListCommand(services, messages, sink, scheduler, guiViews),
                new MuteListCommand(services, messages, sink, scheduler),
                new BanHistoryCommand(services, messages, sink, scheduler, picker, guiViews),
                new MuteHistoryCommand(services, messages, sink, scheduler),
                new HistoryCommand(services, messages, sink, scheduler),
                new StaffHistoryCommand(services, messages, sink, scheduler),
                new StaffRollbackCommand(services, messages, sink, scheduler),
                new ModStatsCommand(services, messages, sink, scheduler),
                new CheckBanCommand(services, messages, sink, scheduler, guiText, textInput),
                new CheckMuteCommand(services, messages, sink, scheduler, guiText, textInput),
                new KickCommand(services, messages, sink, silentByDefault),
                new KickallCommand(services, messages, sink),
                new WarnCommand(services, messages, sink, silentByDefault, guiFlow),
                new TempwarnCommand(services, messages, sink, silentByDefault),
                new UnwarnCommand(services, messages, sink),
                target(
                        "warns",
                        "uxmessentials.moderation.warn",
                        "Review a player's warnings",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.reviewWarns().review(a, t)),
                new SanctionCommand(services, messages, sink, scheduler),
                new BanipCommand(services, messages, sink, guiFlow),
                new TempbanipCommand(services, messages, sink),
                new UnbanipCommand(services, messages, sink),
                target(
                        "freeze",
                        "uxmessentials.moderation.freeze",
                        "Freeze a player in place",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.freeze().freeze(a, t)),
                target(
                        "unfreeze",
                        "uxmessentials.moderation.freeze",
                        "Release a frozen player",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.freeze().unfreeze(a, t)),
                target(
                        "seen",
                        "uxmessentials.moderation.seen",
                        "Show a player's last-seen",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.seen().seen(a, t)),
                target(
                        "seenip",
                        "uxmessentials.moderation.seen",
                        "Show a player's last IP and alts",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.seen().seenIp(a, t)),
                new AltsCommand(services, messages, sink, scheduler),
                new CommandSpyCommand(services, messages, sink),
                new LockdownCommand(services, messages, sink, scheduler),
                new SudoCommand(scheduler, messages, sink));
    }

    private static SimpleTargetCommand target(
            String literal,
            String permission,
            String description,
            ModerationServices services,
            Messages messages,
            MessageSink sink,
            java.util.function.BiConsumer<
                            com.uxplima.uxmessentials.shared.domain.PlayerRef,
                            com.uxplima.uxmessentials.shared.domain.PlayerRef>
                    action) {
        return new SimpleTargetCommand(literal, permission, description, action, services, messages, sink);
    }
}
