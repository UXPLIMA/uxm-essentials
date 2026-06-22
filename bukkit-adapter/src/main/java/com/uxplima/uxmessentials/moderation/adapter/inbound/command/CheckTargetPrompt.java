package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The bare {@code /checkban} and {@code /checkmute} anvil flow: open an anvil that prompts for a player name,
 * resolve the submitted name through the moderation {@code TargetResolver}, and hand the resolved target to the
 * existing chat-output check use case ({@code CheckBan.show} / {@code CheckMute.show}). The maintainer's
 * requirement is "sonuç chatta çıkar" — the anvil only captures the name; the result is the same chat line the
 * raw {@code /checkban <player>} / {@code /checkmute <player>} subcommand prints, not a GUI screen.
 *
 * <p>One instance per check command, parameterised by its prompt key and use-case call. The name resolves off the
 * tick thread (a profile lookup may block) and the check runs off-thread too; an unknown name gets the same
 * unknown-target reply the raw command's resolver gives. The prompt is opened on the viewer's entity thread, where
 * the anvil's own events run. Package-private {@link #checkByName} is the seam a test drives without a live anvil.
 */
@NullMarked
final class CheckTargetPrompt {

    private final ModerationServices services;
    private final GuiText guiText;
    private final AnvilInput anvil;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;
    private final MessageKey promptKey;
    private final BiConsumer<PlayerRef, PlayerRef> check;

    CheckTargetPrompt(
            ModerationServices services,
            GuiText guiText,
            AnvilInput anvil,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink,
            MessageKey promptKey,
            BiConsumer<PlayerRef, PlayerRef> check) {
        this.services = Objects.requireNonNull(services, "services");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.promptKey = Objects.requireNonNull(promptKey, "promptKey");
        this.check = Objects.requireNonNull(check, "check");
    }

    /** Open the name prompt for {@code viewer}; a submitted name flows through {@link #checkByName}. */
    void open(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        PlayerRef actor = BukkitRefs.toRef(viewer);
        scheduler.onEntity(actor, () -> anvil.open(viewer, prompt(actor), result -> handle(actor, result)));
    }

    private ItemStack prompt(PlayerRef actor) {
        return ItemBuilder.of(org.bukkit.Material.NAME_TAG)
                .name(guiText.text(actor, promptKey))
                .build();
    }

    private void handle(PlayerRef actor, AnvilResult result) {
        if (result instanceof AnvilResult.Submitted submitted
                && !submitted.text().isBlank()) {
            checkByName(actor, submitted.text());
        }
    }

    /**
     * Resolve {@code name} off the tick thread, then run the check off-thread too. An unknown name replies with
     * the shared unknown-target line (mirroring the raw command), and no check fires.
     */
    void checkByName(PlayerRef actor, String name) {
        String typed = name.strip();
        scheduler.async(() -> {
            Optional<PlayerRef> target = services.targets().resolve(typed);
            if (target.isEmpty()) {
                sink.deliver(
                        actor,
                        messages.resolve(
                                actor, ModerationCommandSupport.UNKNOWN_PLAYER, java.util.Map.of("player", typed)));
                return;
            }
            check.accept(actor, target.get());
        });
    }
}
