package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ReaderText;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link CommunicationPlaceholders} over the communication context's runtime state: the global {@link ChatLock}
 * the {@code /togglechat} command flips and the {@link BroadcastOptOutStore} the {@code /broadcasttoggle} command
 * flips. Built during communication wiring from the same instances those commands hold, so a placeholder matches
 * the live chat lock and the player's announcer subscription.
 *
 * <p>{@link #chatEnabled()} reports the inverse of the lock. Chat is open while it is not held. {@link
 * #receivesBroadcasts(PlayerRef)} reads the store's per-player bit; the store resolves the connected player, so
 * the read is meaningful for an online player.
 *
 * <p>{@link #prefix} and {@link #suffix} read the player's chat prefix and suffix, and a prefix written as a catalogue
 * line reads in the language of the player the line is drawn for.
 */
@NullMarked
public final class StoreCommunicationPlaceholders implements CommunicationPlaceholders {

    private final ChatLock chatLock;
    private final BroadcastOptOutStore optOutStore;
    private final Function<UUID, ReaderText.Affix> affixes;
    private final BiFunction<UUID, String, String> lines;

    /**
     * @param affixes each player's chat prefix and suffix
     * @param lines a catalogue line in one reader's language, any other text as written
     */
    public StoreCommunicationPlaceholders(
            ChatLock chatLock,
            BroadcastOptOutStore optOutStore,
            Function<UUID, ReaderText.Affix> affixes,
            BiFunction<UUID, String, String> lines) {
        this.chatLock = Objects.requireNonNull(chatLock, "chatLock");
        this.optOutStore = Objects.requireNonNull(optOutStore, "optOutStore");
        this.affixes = Objects.requireNonNull(affixes, "affixes");
        this.lines = Objects.requireNonNull(lines, "lines");
    }

    @Override
    public String prefix(PlayerRef who, PlayerRef reader) {
        return lines.apply(reader.uuid(), affixes.apply(who.uuid()).prefix());
    }

    @Override
    public String suffix(PlayerRef who, PlayerRef reader) {
        return lines.apply(reader.uuid(), affixes.apply(who.uuid()).suffix());
    }

    @Override
    public boolean chatEnabled() {
        return !chatLock.isLocked();
    }

    @Override
    public boolean receivesBroadcasts(PlayerRef who) {
        return optOutStore.receivesBroadcasts(Objects.requireNonNull(who, "who"));
    }
}
