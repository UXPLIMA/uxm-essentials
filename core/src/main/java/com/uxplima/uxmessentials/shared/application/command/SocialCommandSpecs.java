package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.ContentPermissions;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;

/** How players talk and show themselves: chat, private messages, presence, poses and skins. Data, not logic: one row per command, read by {@link CommandSpecCatalog}. */
final class SocialCommandSpecs {

    private static final ModuleId COMMUNICATION = ModuleId.of("communication");
    private static final ModuleId MESSAGING = ModuleId.of("messaging");
    private static final ModuleId PRESENCE = ModuleId.of("presence");
    private static final ModuleId POSES = ModuleId.of("poses");
    private static final ModuleId SKIN = ModuleId.of("skin");

    private SocialCommandSpecs() {}

    static List<CommandSpec> all() {
        return List.of(
                CommandSpec.of(COMMUNICATION, "announce", SocialPermissions.ANNOUNCE_ADMIN),
                CommandSpec.of(COMMUNICATION, "broadcast", SocialPermissions.COMMUNICATION_BROADCAST),
                CommandSpec.of(COMMUNICATION, "broadcasttoggle", SocialPermissions.COMMUNICATION_BROADCASTTOGGLE),
                CommandSpec.of(COMMUNICATION, "broadcastworld", SocialPermissions.COMMUNICATION_BROADCASTWORLD, "bcw"),
                CommandSpec.of(COMMUNICATION, "clearchat", SocialPermissions.COMMUNICATION_CLEARCHAT, "chatclear"),
                CommandSpec.of(COMMUNICATION, "communication", SocialPermissions.COMMUNICATION_GUI),
                CommandSpec.of(COMMUNICATION, "info", SocialPermissions.COMMUNICATION_INFO_INFO),
                CommandSpec.of(COMMUNICATION, "me", SocialPermissions.COMMUNICATION_ME),
                CommandSpec.of(COMMUNICATION, "motd", SocialPermissions.COMMUNICATION_INFO_MOTD),
                CommandSpec.of(COMMUNICATION, "rules", SocialPermissions.COMMUNICATION_INFO_RULES),
                CommandSpec.of(COMMUNICATION, "togglechat", SocialPermissions.COMMUNICATION_TOGGLECHAT, "mutechat"),
                CommandSpec.of(MESSAGING, "helpop", SocialPermissions.HELPOP_USE),
                CommandSpec.of(MESSAGING, "ignore", SocialPermissions.MSG_IGNORE),
                CommandSpec.of(MESSAGING, "ignorelist", SocialPermissions.MSG_IGNORE),
                CommandSpec.of(MESSAGING, "mail", SocialPermissions.MAIL_USE),
                CommandSpec.of(MESSAGING, "mailclear", SocialPermissions.MAIL_USE),
                CommandSpec.of(MESSAGING, "msg", SocialPermissions.MSG_USE, "m", "tell", "whisper", "w"),
                CommandSpec.of(MESSAGING, "msgsettings", SocialPermissions.MSGSETTINGS_USE),
                CommandSpec.of(MESSAGING, "msgtoggle", SocialPermissions.MSG_TOGGLE),
                CommandSpec.of(MESSAGING, "reply", SocialPermissions.MSG_REPLY, "r"),
                CommandSpec.of(MESSAGING, "rtoggle", SocialPermissions.MSG_TOGGLE),
                CommandSpec.of(MESSAGING, "socialspy", SocialPermissions.MSG_SOCIALSPY),
                CommandSpec.of(MESSAGING, "unignore", SocialPermissions.MSG_IGNORE),
                CommandSpec.of(PRESENCE, "afk", SocialPermissions.AFK_USE, "away"),
                CommandSpec.of(PRESENCE, "gc", SocialPermissions.GC_USE, "lag", "tps", "mem"),
                CommandSpec.of(PRESENCE, "list", SocialPermissions.LIST_USE, "who", "online", "playerlist"),
                CommandSpec.of(PRESENCE, "nick", SocialPermissions.NICK_USE),
                CommandSpec.of(PRESENCE, "presencesettings", SocialPermissions.PRESENCESETTINGS_USE),
                CommandSpec.of(PRESENCE, "realname", SocialPermissions.REALNAME_USE),
                CommandSpec.of(PRESENCE, "staff", SocialPermissions.STAFF_USE),
                CommandSpec.of(PRESENCE, "whois", SocialPermissions.WHOIS_USE),
                CommandSpec.of(POSES, "bellyflop", SocialPermissions.BELLYFLOP_USE),
                CommandSpec.of(POSES, "crawl", SocialPermissions.CRAWL_USE),
                CommandSpec.of(POSES, "lay", SocialPermissions.LAY_USE),
                CommandSpec.of(POSES, "poses", SocialPermissions.POSES_GUI),
                CommandSpec.of(POSES, "sit", SocialPermissions.SIT_USE),
                CommandSpec.of(POSES, "spin", SocialPermissions.SPIN_USE),
                CommandSpec.of(SKIN, "skin", ContentPermissions.SKIN_USE, "skins"));
    }
}
