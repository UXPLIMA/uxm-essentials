package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlState;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandLabels;
import org.jspecify.annotations.NullMarked;

/**
 * Normalises the case of the leading command label before anything else acts on the command. Running at
 * {@link EventPriority#LOWEST}, it rewrites the event message so {@code /GAMEMODE creative} becomes
 * {@code /gamemode creative}: the whitelist gate, the plugin-hide, and the vanilla dispatcher all then see one canonical
 * label, and an uppercase command can no longer dodge the filter or fail to run. Only the base label is lowered - the
 * arguments (a world name, a target player, a chat body) are preserved exactly (see {@link CommandLabels#lowerBaseLabel}).
 *
 * <p>The rewrite is skipped for a message the normalisation leaves unchanged, so an already-lowercase command is not
 * re-set on the event. It does nothing while {@code auto-lowercase-base-commands} is off, so a server that relies on
 * case-sensitive command input can turn it off.
 */
@NullMarked
public final class CommandNormalizationListener implements Listener {

    private final CommandControlState state;

    public CommandNormalizationListener(CommandControlState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!state.current().config().autoLowercaseBaseCommands()) {
            return;
        }
        String message = event.getMessage();
        String normalised = CommandLabels.lowerBaseLabel(message);
        if (!normalised.equals(message)) {
            event.setMessage(normalised);
        }
    }
}
