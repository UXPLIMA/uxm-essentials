package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcCommandRunner;
import com.uxplima.uxmessentials.npc.domain.NpcAction;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.BuiltinTokens;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit/Adventure {@link NpcActionRunner}: it filters an NPC's action chain by click trigger and runs the
 * matching actions in order, each fail-soft. Command dispatch (console/player) reuses the same {@link
 * NpcCommandRunner} and {@code [console]}/{@code [player]}/{@code {player}} convention the single click command
 * uses; message/action-bar/title text is resolved through the shared built-in tokens then the PlaceholderAPI +
 * MiniMessage transform, so an action value may embed {@code {player}}, built-in {@code {token}}s, and {@code
 * %papi%} placeholders; a sound value is {@code KEY[:vol[:pitch]]}; a connect value is a target server name.
 *
 * <p>Every action is wrapped so one bad value (an unknown sound, a malformed title, a connect with no proxy)
 * logs a one-line warning and is skipped — the chain never aborts and the listener never sees a throwable.
 */
@NullMarked
public final class BukkitNpcActionRunner implements NpcActionRunner {

    private static final String CONSOLE_PREFIX = "[console]";
    private static final String PLAYER_PREFIX = "[player]";
    private static final String PLAYER_TOKEN = "{player}";

    private final NpcCommandRunner commandRunner;
    private final NpcServerConnector connector;
    private final Logger log;

    public BukkitNpcActionRunner(NpcCommandRunner commandRunner, NpcServerConnector connector, Logger log) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void run(Player viewer, List<NpcAction> actions, boolean attack) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(actions, "actions");
        for (NpcAction action : actions) {
            if (action.trigger().matches(attack)) {
                runOne(viewer, action);
            }
        }
    }

    private void runOne(Player viewer, NpcAction action) {
        try {
            dispatch(viewer, action);
        } catch (RuntimeException failure) {
            // Fail-soft: a single malformed action must not abort the rest of the chain.
            log.warn("event=npc_action_failed type={} value={}", action.type().name(), action.value());
        }
    }

    private void dispatch(Player viewer, NpcAction action) {
        String value = action.value();
        switch (action.type()) {
            case RUN_CONSOLE -> runConsole(viewer, value);
            case RUN_PLAYER -> commandRunner.runAsPlayer(viewer, withPlayer(viewer, stripPrefix(value)));
            case MESSAGE -> viewer.sendMessage(component(viewer, value));
            case ACTIONBAR -> viewer.sendActionBar(component(viewer, value));
            case TITLE -> showTitle(viewer, value);
            case SOUND -> playSound(viewer, value);
            case CONNECT -> connector.connect(viewer, value.strip());
        }
    }

    private void runConsole(Player viewer, String value) {
        commandRunner.runAsConsole(withPlayer(viewer, stripPrefix(value)));
    }

    private void showTitle(Player viewer, String value) {
        int split = value.indexOf('|');
        String titleSource = split < 0 ? value : value.substring(0, split);
        String subtitleSource = split < 0 ? "" : value.substring(split + 1);
        Component title = component(viewer, titleSource);
        Component subtitle = subtitleSource.isEmpty() ? Component.empty() : component(viewer, subtitleSource);
        viewer.showTitle(Title.title(title, subtitle));
    }

    private void playSound(Player viewer, String value) {
        SoundSpec spec = SoundSpec.parse(value);
        Sound sound = BukkitRegistryKeys.resolveSound(spec.key());
        if (sound == null) {
            log.warn("event=npc_action_unknown_sound value={}", value);
            return;
        }
        viewer.playSound(
                Objects.requireNonNull(viewer.getLocation(), "viewer location"), sound, spec.volume(), spec.pitch());
    }

    /** Resolve a value to a component for the viewer: built-in tokens, then the PAPI + MiniMessage transform. */
    private static Component component(Player viewer, String source) {
        String withTokens = BuiltinTokens.apply(viewer, source);
        return HudText.render(viewer.getUniqueId(), withTokens);
    }

    private static String withPlayer(Player viewer, String command) {
        return command.replace(PLAYER_TOKEN, viewer.getName());
    }

    /** Drop a leading {@code [console]}/{@code [player]} routing prefix; the type already chose the dispatcher. */
    private static String stripPrefix(String value) {
        String stripped = value.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.startsWith(CONSOLE_PREFIX)) {
            return stripped.substring(CONSOLE_PREFIX.length()).strip();
        }
        if (lower.startsWith(PLAYER_PREFIX)) {
            return stripped.substring(PLAYER_PREFIX.length()).strip();
        }
        return stripped;
    }

    /**
     * A parsed {@code KEY[:volume[:pitch]]} sound value. The key may itself be namespaced ({@code
     * minecraft:entity.player.levelup}), so volume and pitch are read as the trailing one or two numeric
     * {@code :}-separated segments and everything before them is the key — splitting on the first colon would
     * eat a namespace. A missing volume/pitch defaults to {@code 1.0}.
     */
    record SoundSpec(String key, float volume, float pitch) {

        static SoundSpec parse(String value) {
            // The trailing numeric segments are, left to right, volume then pitch. Peel them off the right
            // (so a single trailing number is the volume), and whatever remains — rejoined on ':' — is the key,
            // so a namespaced key keeps its own colon instead of being eaten by a split-on-first-colon.
            List<String> parts = new ArrayList<>(Splitter.on(':').trimResults().splitToList(value));
            Float last = trailingFloat(parts);
            if (last == null) {
                return new SoundSpec(String.join(":", parts), 1.0f, 1.0f);
            }
            Float secondLast = trailingFloat(parts);
            if (secondLast == null) {
                // One trailing number: it is the volume; pitch defaults.
                return new SoundSpec(String.join(":", parts), last, 1.0f);
            }
            // Two trailing numbers: volume then pitch in left-to-right order.
            return new SoundSpec(String.join(":", parts), secondLast, last);
        }

        /**
         * If the last segment is a float and is not the only segment left (the key must survive), remove and
         * return it; otherwise leave the list untouched and return {@code null}.
         */
        private static @Nullable Float trailingFloat(List<String> parts) {
            if (parts.size() <= 1) {
                return null;
            }
            String last = parts.get(parts.size() - 1);
            try {
                float parsed = Float.parseFloat(last);
                parts.remove(parts.size() - 1);
                return parsed;
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
    }
}
