package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts players talk and emote through. Data, not logic: one row per node, read by
 * {@link PermissionCatalog} and through it by the server registration, the reference page and the in-game listing.
 */
final class SocialPermissions {

    private static final ModuleId MESSAGING = ModuleId.of("messaging");
    private static final ModuleId PRESENCE = ModuleId.of("presence");
    private static final ModuleId COMMUNICATION = ModuleId.of("communication");
    private static final ModuleId DISCORDLINK = ModuleId.of("discordlink");
    private static final ModuleId POSES = ModuleId.of("poses");

    private SocialPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(messaging(), presence(), communication(), discordlink(), poses())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PermissionSpec> messaging() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.helpop.receive",
                        "Receive /helpop requests (staff side).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.helpop.use",
                        "/helpop <text> to open a player-to-staff support request.",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.mail.sendall",
                        "/mail sendall <text> to broadcast mail to every online player (staff).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.mail.use",
                        "/mail read / /mail send <player> <text> / /mail clear (/mailclear): persistent offline mail.",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.messaging.gui",
                        "See and open the messaging settings panel and mailbox on the /uxmess gui management hub.",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.module.messaging",
                        "Hot-reload / inspect the messaging module (private messages, mail and social spy).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.msg.color",
                        "Render MiniMessage tags in PM/mail bodies (default plain text).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.msg.ignore",
                        "/ignore <player> / /unignore <player> / /ignorelist to manage and view your own ignore list.",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.msg.reply",
                        "/reply <text> to answer your last conversation (reply-TTL bounded).",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.msg.socialspy",
                        "/socialspy to observe other players' private messages (staff).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.msg.toggle",
                        "/msgtoggle to refuse incoming /msg / /reply; /rtoggle to refuse only incoming /reply routing (mail still delivers).",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.msg.use",
                        "/msg <player> <text> to send a private message.",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        "uxmessentials.msgsettings.use",
                        "/msgsettings opens your personal messaging settings panel (accept-messages, social spy).",
                        PermissionDefault.TRUE,
                        MESSAGING));
    }

    private static List<PermissionSpec> presence() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.afk.use",
                        "/afk [reason] to toggle your AFK state (auto-AFK also applies on idle).",
                        PermissionDefault.TRUE,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.gc.use",
                        "/gc to show server health: TPS, uptime, memory and loaded chunks.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.list.use", "/list to see who is online.", PermissionDefault.TRUE, PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.module.presence",
                        "Hot-reload / inspect the presence module (AFK, nicknames and vanish state).",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.nick.others",
                        "/nick <player> <name> to set another player's display name.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.nick.use",
                        "/nick <name> | off to set or clear your display name.",
                        PermissionDefault.TRUE,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.presence.gui",
                        "Show the presence settings panel on the /uxmess gui hub.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.presencesettings.use",
                        "/presencesettings opens your personal presence settings panel.",
                        PermissionDefault.TRUE,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.realname.use",
                        "/realname <player> to look up a player's real account name.",
                        PermissionDefault.TRUE,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.staff.member",
                        "Marks a player as staff so they appear in /staff.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.staff.use",
                        "/staff to list online staff members.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        "uxmessentials.whois.use",
                        "/whois <player> to view an online player's account, identity and status.",
                        PermissionDefault.OP,
                        PRESENCE));
    }

    private static List<PermissionSpec> communication() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.announce.admin",
                        "/announce reload|list|preview|toggle to manage the rotating announcer.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.broadcast",
                        "/broadcast to send a one-off announcement to all online players.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.broadcasttoggle",
                        "/broadcasttoggle to stop or resume receiving the rotating server announcements.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.broadcastworld",
                        "/broadcastworld (alias /bcw) to send a one-off announcement only to players in your world.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.chat.bypass",
                        "Keep chatting while public chat is locked by /togglechat.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.chat.format",
                        "Use MiniMessage formatting in your own public chat messages (when allow-player-format is on).",
                        PermissionDefault.FALSE,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.clearchat",
                        "/clearchat (alias /chatclear) to flush the chat for online players.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.clearchat.exempt",
                        "Keep your chat scrollback when staff run /clearchat.",
                        PermissionDefault.FALSE,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.gui",
                        "Open the communication admin panel (/communication gui and on the /uxmess gui hub).",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.family(
                        "uxmessentials.communication.info.<page>",
                        "Read one information page, such as /motd or /rules.",
                        PermissionDefault.TRUE,
                        PermissionShape.LABEL,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.info.info",
                        "/info to read the shipped welcome / quick-start info page.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.info.motd",
                        "/motd to read the shipped message-of-the-day info page.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.info.rules",
                        "/rules to read the shipped server-rules info page.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.me",
                        "/me to broadcast a third-person action message to all online players.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.communication.togglechat",
                        "/togglechat (alias /mutechat) to lock or unlock public chat for non-staff.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        "uxmessentials.module.communication",
                        "Hot-reload / inspect the communication module (connection messages, announcer, info pages).",
                        PermissionDefault.OP,
                        COMMUNICATION));
    }

    private static List<PermissionSpec> discordlink() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.discord.gui",
                        "/discordlink gui (and the discordlink entry on the /uxmess gui hub) to open the link-status panel: your binding, a generate-code button, and a confirm-gated unlink.",
                        PermissionDefault.TRUE,
                        DISCORDLINK),
                PermissionSpec.of(
                        "uxmessentials.discord.link",
                        "/discordlink (issue a code), /discordlink status (show your binding), and /discordunlink (remove it): all act only on your own account.",
                        PermissionDefault.TRUE,
                        DISCORDLINK),
                PermissionSpec.of(
                        "uxmessentials.module.discordlink",
                        "Hot-reload / inspect the discordlink module (account linking and Discord notifications).",
                        PermissionDefault.OP,
                        DISCORDLINK));
    }

    private static List<PermissionSpec> poses() {
        return List.of(
                PermissionSpec.of(
                        "uxmessentials.bellyflop.use",
                        "/bellyflop: flop onto your front where you stand.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        "uxmessentials.crawl.use",
                        "/crawl: crawl through a one-block gap; run again to stand up.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        "uxmessentials.lay.use",
                        "/lay: lie down on your back where you stand.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        "uxmessentials.module.poses",
                        "Hot-reload / inspect the poses module (built-in GSit-parity sitting and posing).",
                        PermissionDefault.OP,
                        POSES),
                PermissionSpec.of(
                        "uxmessentials.playersit.use",
                        "Right-click another player to sit on them (the stacking pose), when player-sit is enabled.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        "uxmessentials.poses.gui",
                        "/poses (or /poses gui): open your personal poses settings & status panel.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        "uxmessentials.poses.toggle",
                        "/poses toggle: allow or refuse other players sitting on you.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        "uxmessentials.sit.use",
                        "/sit and right-click-to-sit: sit down where you stand or on a sittable block.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        "uxmessentials.spin.use", "/spin: sit and spin in place.", PermissionDefault.TRUE, POSES));
    }
}
