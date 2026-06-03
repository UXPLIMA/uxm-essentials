package com.uxplima.uxmessentials.presence.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.ClearNick;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.ResolveVisibility;
import com.uxplima.uxmessentials.presence.application.SetNick;
import com.uxplima.uxmessentials.presence.application.ToggleVanish;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed presence use cases the commands, listeners, and AFK sweep share, built once per module start
 * by {@code PresenceWiring} from the kernel ports and the context's own in-memory store, visibility applier, and
 * audience. Held so every collaborator reads the same use cases; the context's only adapter-side runtime state
 * is the in-memory presence map (cleared on stop) and the self-rescheduling sweep (stopped on disable).
 *
 * @param markAfk {@code /afk [reason]} toggle and the sweep's auto-mark
 * @param clearAfk the sync activity listeners' return-from-AFK on move/chat/command
 * @param toggleVanish {@code /vanish}
 * @param resolveVisibility the read-only vanish query messaging and teleport consume cross-context
 * @param setNick {@code /nick <name>} and {@code /nick <player> <name>}
 * @param clearNick {@code /nick off}
 */
@NullMarked
public record PresenceServices(
        MarkAfk markAfk,
        ClearAfkOnActivity clearAfk,
        ToggleVanish toggleVanish,
        ResolveVisibility resolveVisibility,
        SetNick setNick,
        ClearNick clearNick) {

    public PresenceServices {
        Objects.requireNonNull(markAfk, "markAfk");
        Objects.requireNonNull(clearAfk, "clearAfk");
        Objects.requireNonNull(toggleVanish, "toggleVanish");
        Objects.requireNonNull(resolveVisibility, "resolveVisibility");
        Objects.requireNonNull(setNick, "setNick");
        Objects.requireNonNull(clearNick, "clearNick");
    }
}
