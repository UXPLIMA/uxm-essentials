package com.uxplima.uxmessentials.shared.adapter.outbound.chat;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import org.jspecify.annotations.Nullable;

/**
 * Whether uxmChat runs a feature this plugin also has: chat format, private messages, social spy, mail,
 * helpop, the announcer, join and quit lines, broadcast, clear chat, me and the chat toggle. A feature uxmChat
 * runs is left to it; a feature it has turned off stays here.
 *
 * <p>This plugin starts before the worlds and uxmChat after them, so while uxmChat is installed but has not
 * started yet every one of its features counts as running. Once the server ticks, a uxmChat that never
 * started runs nothing.
 */
public final class UxmChatStepBack {

    public static final String CHAT_FORMAT = "chat-format";
    public static final String PRIVATE_MESSAGES = "private-messages";
    public static final String SOCIAL_SPY = "social-spy";
    public static final String MAIL = "mail";
    public static final String HELPOP = "helpop";
    public static final String ANNOUNCEMENTS = "announcements";
    public static final String JOIN_QUIT = "join-quit";
    public static final String BROADCAST = "broadcast";
    public static final String CLEAR_CHAT = "clear-chat";
    public static final String ME = "me";
    public static final String TOGGLE_CHAT = "toggle-chat";
    public static final String IGNORE = "ignore";
    public static final String NICKNAMES = "nicknames";
    public static final String STAFF_LIST = "staff-list";

    private static final String PLUGIN = "uxmChat";
    private static final String SERVICE = "com.uxplima.chat.api.UxmChat";
    private static final Duration FRESH = Duration.ofSeconds(5);

    /** This plugin's command ids and the uxmChat feature that takes each over. */
    private static final Map<String, String> COMMANDS = Map.ofEntries(
            Map.entry("msg", PRIVATE_MESSAGES),
            Map.entry("reply", PRIVATE_MESSAGES),
            Map.entry("msgtoggle", PRIVATE_MESSAGES),
            Map.entry("rtoggle", PRIVATE_MESSAGES),
            Map.entry("msgsettings", PRIVATE_MESSAGES),
            Map.entry("ignore", IGNORE),
            Map.entry("unignore", IGNORE),
            Map.entry("ignorelist", IGNORE),
            Map.entry("nick", NICKNAMES),
            Map.entry("realname", NICKNAMES),
            Map.entry("stafflist", STAFF_LIST),
            Map.entry("socialspy", SOCIAL_SPY),
            Map.entry("mail", MAIL),
            Map.entry("mailclear", MAIL),
            Map.entry("helpop", HELPOP),
            Map.entry("broadcast", BROADCAST),
            Map.entry("broadcasttoggle", BROADCAST),
            Map.entry("broadcastworld", BROADCAST),
            Map.entry("clearchat", CLEAR_CHAT),
            Map.entry("me", ME),
            Map.entry("togglechat", TOGGLE_CHAT));

    private final Server server;
    private volatile @Nullable Seen seen;

    private record Seen(Set<String> features, long at) {}

    public UxmChatStepBack(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /** Whether uxmChat runs {@code feature}, one of the constants above. */
    public boolean covers(String feature) {
        Plugin chat = server.getPluginManager().getPlugin(PLUGIN);
        if (chat == null) {
            return false;
        }
        if (!chat.isEnabled()) {
            return server.getCurrentTick() == 0;
        }
        return active().map(features -> features.contains(feature)).orElse(false);
    }

    /**
     * Whether uxmChat takes over the command with this id, so this plugin does not register it.
     *
     * <p>Commands are decided when the server builds its command tree, after every plugin has enabled, so
     * here a uxmChat that is not enabled runs nothing: one that failed to start must not take this plugin's
     * commands with it.
     */
    public boolean coversCommand(String commandId) {
        String feature = COMMANDS.get(commandId);
        if (feature == null) {
            return false;
        }
        Plugin chat = server.getPluginManager().getPlugin(PLUGIN);
        return chat != null
                && chat.isEnabled()
                && active().map(features -> features.contains(feature)).orElse(false);
    }

    private Optional<Set<String>> active() {
        Seen last = seen;
        long now = System.nanoTime();
        if (last != null && now - last.at() < FRESH.toNanos()) {
            return Optional.of(last.features());
        }
        // An unreadable uxmChat is remembered too, so a chat line does not scan the services again.
        Set<String> read = read().orElse(Set.of());
        seen = new Seen(read, now);
        return Optional.of(read);
    }

    private Optional<Set<String>> read() {
        for (Class<?> service : server.getServicesManager().getKnownServices()) {
            if (!service.getName().equals(SERVICE)) {
                continue;
            }
            Object provider = server.getServicesManager().load(service);
            if (provider == null) {
                return Optional.empty();
            }
            try {
                Object features = service.getMethod("activeFeatures").invoke(provider);
                if (features instanceof Set<?> set) {
                    return Optional.of(set.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet()));
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException unreadable) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
