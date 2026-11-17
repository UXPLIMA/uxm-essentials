package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.shared.application.settings.PlayerSwitches;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@code /msgtoggle} and {@code /rtoggle} switches over the switches every backend shares, so a player who turned
 * private messages or replies off keeps them off on every backend. Both are on until the player turns them off. Only
 * the backend the player is on reads them: a message to a player on another backend is judged there.
 */
public final class MessagingSwitches {

    /** Whether the player takes private messages. */
    public static final String MESSAGES = "messages";

    /** Whether the player takes part in reply routing. */
    public static final String REPLIES = "replies";

    /** Whether the staff member is shown every private message. */
    public static final String SOCIAL_SPY = "social-spy";

    /** Every switch the messaging module keeps. */
    public static final Set<String> NAMES = Set.of(MESSAGES, REPLIES, SOCIAL_SPY);

    private final PlayerSwitches switches;

    public MessagingSwitches(PlayerSwitches switches) {
        this.switches = Objects.requireNonNull(switches, "switches");
    }

    /** The {@code /msgtoggle} switch. */
    public MessageToggleStore messages() {
        return new MessageToggleStore() {
            @Override
            public boolean acceptsMessages(PlayerRef who) {
                return switches.isOn(who.uuid(), MESSAGES, true);
            }

            @Override
            public boolean toggle(PlayerRef who) {
                return switches.flip(who.uuid(), MESSAGES, true).orElseGet(() -> acceptsMessages(who));
            }

            @Override
            public boolean ready(PlayerRef who) {
                return switches.ready(who.uuid());
            }
        };
    }

    /** The {@code /rtoggle} switch. */
    public ReplyRoutingStore replies() {
        return new ReplyRoutingStore() {
            @Override
            public boolean acceptsReplies(PlayerRef who) {
                return switches.isOn(who.uuid(), REPLIES, true);
            }

            @Override
            public boolean toggle(PlayerRef who) {
                return switches.flip(who.uuid(), REPLIES, true).orElseGet(() -> acceptsReplies(who));
            }

            @Override
            public boolean ready(PlayerRef who) {
                return switches.ready(who.uuid());
            }
        };
    }
}
