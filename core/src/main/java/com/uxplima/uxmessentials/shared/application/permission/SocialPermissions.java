package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/**
 * The permission table for the contexts players talk and emote through. Data, not logic: one row per node, read by
 * {@link PermissionCatalog} and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class SocialPermissions {

    private static final ModuleId MESSAGING = ModuleId.of("messaging");
    private static final ModuleId PRESENCE = ModuleId.of("presence");
    private static final ModuleId COMMUNICATION = ModuleId.of("communication");
    private static final ModuleId POSES = ModuleId.of("poses");

    public static final String HELPOP_RECEIVE = "uxmessentials.helpop.receive";
    public static final String HELPOP_USE = "uxmessentials.helpop.use";
    public static final String MAIL_SENDALL = "uxmessentials.mail.sendall";
    public static final String MAIL_USE = "uxmessentials.mail.use";
    public static final String MESSAGING_GUI = "uxmessentials.messaging.gui";
    public static final String MODULE_MESSAGING = "uxmessentials.module.messaging";
    public static final String MSG_COLOR = "uxmessentials.msg.color";
    public static final String MSG_IGNORE = "uxmessentials.msg.ignore";
    public static final String MSG_REPLY = "uxmessentials.msg.reply";
    public static final String MSG_SOCIALSPY = "uxmessentials.msg.socialspy";
    public static final String MSG_TOGGLE = "uxmessentials.msg.toggle";
    public static final String MSG_USE = "uxmessentials.msg.use";
    public static final String MSGSETTINGS_USE = "uxmessentials.msgsettings.use";
    public static final String AFK_USE = "uxmessentials.afk.use";
    public static final String GC_USE = "uxmessentials.gc.use";
    public static final String LIST_USE = "uxmessentials.list.use";
    public static final String MODULE_PRESENCE = "uxmessentials.module.presence";
    public static final String NICK_OTHERS = "uxmessentials.nick.others";
    public static final String NICK_USE = "uxmessentials.nick.use";
    public static final String PRESENCE_GUI = "uxmessentials.presence.gui";
    public static final String PRESENCESETTINGS_USE = "uxmessentials.presencesettings.use";
    public static final String REALNAME_USE = "uxmessentials.realname.use";
    public static final String STAFF_MEMBER = "uxmessentials.staff.member";
    public static final String STAFF_USE = "uxmessentials.staff.use";
    public static final String WHOIS_USE = "uxmessentials.whois.use";
    public static final String ANNOUNCE_ADMIN = "uxmessentials.announce.admin";
    public static final String ANNOUNCE_ADMIN_EDITOR = "uxmessentials.announce.admin.editor";
    public static final String ANNOUNCE_ADMIN_RELOAD = "uxmessentials.announce.admin.reload";
    public static final String ANNOUNCE_ADMIN_LIST = "uxmessentials.announce.admin.list";
    public static final String ANNOUNCE_ADMIN_PREVIEW = "uxmessentials.announce.admin.preview";
    public static final String ANNOUNCE_ADMIN_TOGGLE = "uxmessentials.announce.admin.toggle";
    public static final String COMMUNICATION_BROADCAST = "uxmessentials.communication.broadcast";
    public static final String COMMUNICATION_BROADCASTTOGGLE = "uxmessentials.communication.broadcasttoggle";
    public static final String COMMUNICATION_BROADCASTWORLD = "uxmessentials.communication.broadcastworld";
    public static final String COMMUNICATION_CHAT_BYPASS = "uxmessentials.bypass.chat-lock";
    public static final String COMMUNICATION_CHAT_FORMAT = "uxmessentials.communication.chat.format";
    public static final String COMMUNICATION_CLEARCHAT = "uxmessentials.communication.clearchat";
    public static final String COMMUNICATION_CLEARCHAT_EXEMPT = "uxmessentials.bypass.clear-chat";
    public static final String COMMUNICATION_GUI = "uxmessentials.communication.gui";
    public static final String COMMUNICATION_INFO_INFO = "uxmessentials.communication.info.info";
    public static final String COMMUNICATION_INFO_MOTD = "uxmessentials.communication.info.motd";
    public static final String COMMUNICATION_INFO_RULES = "uxmessentials.communication.info.rules";
    public static final String COMMUNICATION_ME = "uxmessentials.communication.me";
    public static final String COMMUNICATION_TOGGLECHAT = "uxmessentials.communication.togglechat";
    public static final String MODULE_COMMUNICATION = "uxmessentials.module.communication";
    public static final String BELLYFLOP_USE = "uxmessentials.bellyflop.use";
    public static final String CRAWL_USE = "uxmessentials.crawl.use";
    public static final String LAY_USE = "uxmessentials.lay.use";
    public static final String MODULE_POSES = "uxmessentials.module.poses";
    public static final String PLAYERSIT_USE = "uxmessentials.playersit.use";
    public static final String POSES_GUI = "uxmessentials.poses.gui";
    public static final String POSES_TOGGLE = "uxmessentials.poses.toggle";
    public static final String POSES_REVEAL = "uxmessentials.poses.reveal";
    public static final String SIT_USE = "uxmessentials.sit.use";
    public static final String SPIN_USE = "uxmessentials.spin.use";

    private static final String INFO_PAGE_PREFIX = "uxmessentials.communication.info.";

    /** The node a player needs to read the information page behind {@code command}. */
    public static String infoPage(String command) {
        return INFO_PAGE_PREFIX + command;
    }

    private SocialPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(messaging(), presence(), communication(), poses())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PermissionSpec> messaging() {
        return List.of(
                PermissionSpec.of(
                        HELPOP_RECEIVE, "Receive /helpop requests (staff side).", PermissionDefault.OP, MESSAGING),
                PermissionSpec.of(
                        HELPOP_USE,
                        "/helpop <text> to open a player-to-staff support request.",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        MAIL_SENDALL,
                        "/mail sendall <text> to broadcast mail to every online player (staff).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        MAIL_USE,
                        "/mail read / /mail send <player> <text> / /mail clear (/mailclear): persistent offline mail.",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        MESSAGING_GUI,
                        "See and open the messaging settings panel and mailbox on the /uxmess gui management hub.",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        MODULE_MESSAGING,
                        "Hot-reload / inspect the messaging module (private messages, mail and social spy).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        MSG_COLOR,
                        "Render MiniMessage tags in PM/mail bodies (default plain text).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        MSG_IGNORE,
                        "/ignore <player> / /unignore <player> / /ignorelist to manage and view your own ignore list.",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        MSG_REPLY,
                        "/reply <text> to answer your last conversation (reply-TTL bounded).",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        MSG_SOCIALSPY,
                        "/socialspy to observe other players' private messages (staff).",
                        PermissionDefault.OP,
                        MESSAGING),
                PermissionSpec.of(
                        MSG_TOGGLE,
                        "/msgtoggle to refuse incoming /msg / /reply; /rtoggle to refuse only incoming /reply routing (mail still delivers).",
                        PermissionDefault.TRUE,
                        MESSAGING),
                PermissionSpec.of(
                        MSG_USE, "/msg <player> <text> to send a private message.", PermissionDefault.TRUE, MESSAGING),
                PermissionSpec.of(
                        MSGSETTINGS_USE,
                        "/msgsettings opens your personal messaging settings panel (accept-messages, social spy).",
                        PermissionDefault.TRUE,
                        MESSAGING));
    }

    private static List<PermissionSpec> presence() {
        return List.of(
                PermissionSpec.of(
                        AFK_USE,
                        "/afk [reason] to toggle your AFK state (auto-AFK also applies on idle).",
                        PermissionDefault.TRUE,
                        PRESENCE),
                PermissionSpec.of(
                        GC_USE,
                        "/gc to show server health: TPS, uptime, memory and loaded chunks.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(LIST_USE, "/list to see who is online.", PermissionDefault.TRUE, PRESENCE),
                PermissionSpec.of(
                        MODULE_PRESENCE,
                        "Hot-reload / inspect the presence module (AFK, nicknames and vanish state).",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        NICK_OTHERS,
                        "/nick <player> <name> to set another player's display name.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        NICK_USE,
                        "/nick <name> | off to set or clear your display name.",
                        PermissionDefault.TRUE,
                        PRESENCE),
                PermissionSpec.of(
                        PRESENCE_GUI,
                        "Show the presence settings panel on the /uxmess gui hub.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(
                        PRESENCESETTINGS_USE,
                        "/presencesettings opens your personal presence settings panel.",
                        PermissionDefault.TRUE,
                        PRESENCE),
                PermissionSpec.of(
                        REALNAME_USE,
                        "/realname <player> to look up a player's real account name.",
                        PermissionDefault.TRUE,
                        PRESENCE),
                PermissionSpec.of(
                        STAFF_MEMBER,
                        "Marks a player as staff so they appear in /staff.",
                        PermissionDefault.OP,
                        PRESENCE),
                PermissionSpec.of(STAFF_USE, "/staff to list online staff members.", PermissionDefault.OP, PRESENCE),
                PermissionSpec.of(
                        WHOIS_USE,
                        "/whois <player> to view an online player's account, identity and status.",
                        PermissionDefault.OP,
                        PRESENCE));
    }

    private static List<PermissionSpec> communication() {
        return List.of(
                PermissionSpec.of(
                        ANNOUNCE_ADMIN,
                        "/announce reload|list|preview|toggle to manage the rotating announcer.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        ANNOUNCE_ADMIN_EDITOR,
                        "/announce editor to author announcements in the GUI.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        ANNOUNCE_ADMIN_RELOAD,
                        "/announce reload to re-read announcer.conf and re-arm the rotation.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        ANNOUNCE_ADMIN_LIST,
                        "/announce list to read the rotation and the channels each announcement pushes to.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        ANNOUNCE_ADMIN_PREVIEW,
                        "/announce preview <id> to show one announcement to yourself alone.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        ANNOUNCE_ADMIN_TOGGLE,
                        "/announce toggle to stop or resume receiving the rotating announcements.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_BROADCAST,
                        "/broadcast to send a one-off announcement to all online players.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_BROADCASTTOGGLE,
                        "/broadcasttoggle to stop or resume receiving the rotating server announcements.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_BROADCASTWORLD,
                        "/broadcastworld (alias /bcw) to send a one-off announcement only to players in your world.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_CHAT_BYPASS,
                        "Keep chatting while public chat is locked by /togglechat.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_CHAT_FORMAT,
                        "Use MiniMessage formatting in your own public chat messages (when allow-player-format is on).",
                        PermissionDefault.FALSE,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_CLEARCHAT,
                        "/clearchat (alias /chatclear) to flush the chat for online players.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_CLEARCHAT_EXEMPT,
                        "Keep your chat scrollback when staff run /clearchat.",
                        PermissionDefault.FALSE,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_GUI,
                        "Open the communication admin panel (/communication gui and on the /uxmess gui hub).",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.family(
                        infoPage("<page>"),
                        "Read one information page an operator added, such as /discord; the shipped info, motd and "
                                + "rules pages have nodes of their own that every player holds.",
                        // A page the operator adds may be meant for staff alone, so it stays closed until its node is
                        // granted; the shipped pages are fixed rows open to everyone.
                        PermissionDefault.OP,
                        PermissionShape.LABEL,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_INFO_INFO,
                        "/info to read the shipped welcome / quick-start info page.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_INFO_MOTD,
                        "/motd to read the shipped message-of-the-day info page.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_INFO_RULES,
                        "/rules to read the shipped server-rules info page.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_ME,
                        "/me to broadcast a third-person action message to all online players.",
                        PermissionDefault.TRUE,
                        COMMUNICATION),
                PermissionSpec.of(
                        COMMUNICATION_TOGGLECHAT,
                        "/togglechat (alias /mutechat) to lock or unlock public chat for non-staff.",
                        PermissionDefault.OP,
                        COMMUNICATION),
                PermissionSpec.of(
                        MODULE_COMMUNICATION,
                        "Hot-reload / inspect the communication module (connection messages, announcer, info pages).",
                        PermissionDefault.OP,
                        COMMUNICATION));
    }

    private static List<PermissionSpec> poses() {
        return List.of(
                PermissionSpec.of(
                        BELLYFLOP_USE,
                        "/bellyflop: flop onto your front where you stand.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        CRAWL_USE,
                        "/crawl: crawl through a one-block gap; run again to stand up.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        LAY_USE, "/lay: lie down on your back where you stand.", PermissionDefault.TRUE, POSES),
                PermissionSpec.of(
                        MODULE_POSES,
                        "Hot-reload / inspect the poses module (built-in GSit-parity sitting and posing).",
                        PermissionDefault.OP,
                        POSES),
                PermissionSpec.of(
                        PLAYERSIT_USE,
                        "Right-click another player to sit on them (the stacking pose), when player-sit is enabled.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        POSES_GUI,
                        "/poses (or /poses gui): open your personal poses settings & status panel.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        POSES_TOGGLE,
                        "/poses toggle: allow or refuse other players sitting on you.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(
                        POSES_REVEAL,
                        "/poses reveal <player>: give back a body a pose left unseen.",
                        PermissionDefault.OP,
                        POSES),
                PermissionSpec.family(
                        SharedPermissions.featureCooldown("poses") + ".<seconds>",
                        "The wait between starting poses, in seconds; the shortest tier held wins and 0 removes it.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER,
                        POSES),
                PermissionSpec.of(
                        SIT_USE,
                        "/sit and right-click-to-sit: sit down where you stand or on a sittable block.",
                        PermissionDefault.TRUE,
                        POSES),
                PermissionSpec.of(SPIN_USE, "/spin: sit and spin in place.", PermissionDefault.TRUE, POSES));
    }
}
