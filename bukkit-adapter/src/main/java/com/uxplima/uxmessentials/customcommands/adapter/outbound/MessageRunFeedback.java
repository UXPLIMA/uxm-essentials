package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.customcommands.application.CustomCommandsMessageKey;
import com.uxplima.uxmessentials.customcommands.application.RunOutcome;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.application.port.RunFeedback;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DurationText;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Turns a run outcome into the line the actor reads. One switch over the sealed outcome keeps the gate order and
 * the wording in step: a gate that gains a case here is a gate the use case really has.
 *
 * <p>Two outcomes say nothing. A successful run speaks through its own actions, and a warmup that was configured
 * as zero never started, so announcing either would only add noise. A refused permission prefers the definition's
 * own {@code deny-message} when it names one, because that line is operator content written for that command.
 */
public final class MessageRunFeedback implements RunFeedback {

    private final Messages messages;
    private final MessageSink sink;
    private final CommandFee fee;

    public MessageRunFeedback(Messages messages, MessageSink sink, CommandFee fee) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.fee = Objects.requireNonNull(fee, "fee");
    }

    @Override
    public void report(PlayerRef who, CustomCommand command, RunOutcome outcome) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(outcome, "outcome");
        switch (outcome) {
            case RunOutcome.Ok ignored -> {}
            case RunOutcome.ConsoleDenied ignored -> send(who, CustomCommandsMessageKey.CUSTOMCOMMAND_CONSOLE_DENIED);
            case RunOutcome.NoPermission ignored -> denyPermission(who, command);
            case RunOutcome.DepthExceeded ignored -> send(who, CustomCommandsMessageKey.CUSTOMCOMMAND_DEPTH_EXCEEDED);
            case RunOutcome.RequirementsFailed ignored -> requirementsFailed(who, command);
            case RunOutcome.OnCooldown onCooldown ->
                send(
                        who,
                        CustomCommandsMessageKey.CUSTOMCOMMAND_ON_COOLDOWN,
                        Map.of("time", DurationText.humanize(onCooldown.remaining())));
            case RunOutcome.WarmupStarted started -> warmupStarted(who, started);
            case RunOutcome.WarmupCancelled ignored ->
                send(who, CustomCommandsMessageKey.CUSTOMCOMMAND_WARMUP_CANCELLED);
            case RunOutcome.CannotAfford cannotAfford ->
                send(
                        who,
                        CustomCommandsMessageKey.CUSTOMCOMMAND_CANNOT_AFFORD,
                        Map.of("cost", fee.format(cannotAfford.cost())));
        }
    }

    /** A refused permission draws the definition's own deny line when it has one, else the shared refusal. */
    private void denyPermission(PlayerRef who, CustomCommand command) {
        command.denyMessage()
                .ifPresentOrElse(
                        line -> sink.deliver(who, line),
                        () -> send(who, CustomCommandsMessageKey.CUSTOMCOMMAND_NO_PERMISSION));
    }

    /** An unmet requirement says nothing when the definition runs its own deny chain, which already spoke. */
    private void requirementsFailed(PlayerRef who, CustomCommand command) {
        if (command.requirementDeny().isEmpty()) {
            send(who, CustomCommandsMessageKey.CUSTOMCOMMAND_REQUIREMENTS_FAILED);
        }
    }

    /** A warmup announces the wait, unless it was configured as none and therefore never started. */
    private void warmupStarted(PlayerRef who, RunOutcome.WarmupStarted started) {
        if (started.countdown().isZero()) {
            return;
        }
        send(
                who,
                CustomCommandsMessageKey.CUSTOMCOMMAND_WARMUP_STARTED,
                Map.of("time", DurationText.humanize(started.countdown())));
    }

    private void send(PlayerRef who, MessageKey key) {
        send(who, key, Map.of());
    }

    private void send(PlayerRef who, MessageKey key, Map<String, String> placeholders) {
        sink.deliver(who, messages.resolve(who, key, placeholders));
    }
}
