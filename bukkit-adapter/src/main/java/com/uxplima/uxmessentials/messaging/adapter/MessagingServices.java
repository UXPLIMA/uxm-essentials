package com.uxplima.uxmessentials.messaging.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.HelpOp;
import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.ListIgnores;
import com.uxplima.uxmessentials.messaging.application.MsgToggle;
import com.uxplima.uxmessentials.messaging.application.ReadMail;
import com.uxplima.uxmessentials.messaging.application.Reply;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.application.SocialSpy;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed messaging use cases the Brigadier commands share, built once per module start by
 * {@code MessagingWiring} from the kernel ports, the jOOQ mail/ignore stores, the in-memory reply store, and
 * the soft-couple gates. Held so every command reads the same use cases. The {@link PlayerLookup} and
 * {@link VanishVisibility} are carried here so the {@code /msg} command can resolve a target with the same
 * vanish-aware {@code canSee} seam the teleport context applies — a vanished target the sender cannot see is
 * offered as unknown.
 *
 * @param sendMessage {@code /msg}
 * @param reply {@code /reply}
 * @param sendMail {@code /mail send}
 * @param readMail {@code /mail read}
 * @param clearMail {@code /mail clear}, {@code /mailclear}
 * @param msgToggle {@code /msgtoggle}
 * @param ignore {@code /ignore}
 * @param unignore {@code /unignore}
 * @param listIgnores {@code /ignorelist}
 * @param socialSpy {@code /socialspy}
 * @param helpOp {@code /helpop}
 * @param players name → ref resolution for {@code /msg} and {@code /ignore} targets
 * @param vanish vanish-aware visibility for {@code /msg} target resolution
 */
@NullMarked
public record MessagingServices(
        SendMessage sendMessage,
        Reply reply,
        SendMail sendMail,
        ReadMail readMail,
        ClearMail clearMail,
        MsgToggle msgToggle,
        Ignore ignore,
        Unignore unignore,
        ListIgnores listIgnores,
        SocialSpy socialSpy,
        HelpOp helpOp,
        PlayerLookup players,
        VanishVisibility vanish) {

    public MessagingServices {
        Objects.requireNonNull(sendMessage, "sendMessage");
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(sendMail, "sendMail");
        Objects.requireNonNull(readMail, "readMail");
        Objects.requireNonNull(clearMail, "clearMail");
        Objects.requireNonNull(msgToggle, "msgToggle");
        Objects.requireNonNull(ignore, "ignore");
        Objects.requireNonNull(unignore, "unignore");
        Objects.requireNonNull(listIgnores, "listIgnores");
        Objects.requireNonNull(socialSpy, "socialSpy");
        Objects.requireNonNull(helpOp, "helpOp");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(vanish, "vanish");
    }
}
