package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;

/**
 * The audible half of a menu click. A button that reacts silently reads as a button that did nothing, so the engine
 * gives every gesture it accepts a short sound: a click for an ordinary action, a page turn for pagination, and a low
 * note for a refusal. The tones are the ones the style canon fixes, so every menu across every module sounds the same
 * without a spec having to say so.
 *
 * <p>The sound is feedback, never content: a gesture the engine ignored (a filler pane, a slot with no bound action)
 * stays silent, and a spec that plays its own {@code sound:} effect on that gesture wins, so an operator who wants a
 * cash register on a purchase button gets exactly that instead of two overlapping sounds.
 *
 * <p>Volumes sit low on purpose. A menu is clicked far more often than anything else in the game, and a loud click
 * turns a browsing session into noise.
 */
final class MenuFeedback {

    private static final String CLICK_KEY = "ui.button.click";
    private static final String PAGE_KEY = "item.book.page_turn";
    private static final String DENY_KEY = "block.note_block.bass";

    private static final float CLICK_VOLUME = 0.5f;
    private static final float CLICK_PITCH = 1.6f;
    private static final float PAGE_VOLUME = 0.7f;
    private static final float PAGE_PITCH = 1.0f;
    private static final float DENY_VOLUME = 0.6f;
    private static final float DENY_PITCH = 0.9f;

    private static final String SOUND_ACTION = "sound";
    private static final String RAW_SOUND_ACTION = "rawsound";
    private static final String RAW_SOUND_ALIAS = "raw-sound";

    private MenuFeedback() {}

    /**
     * Play the ordinary click tone for a gesture that ran {@code actions}. An empty list means the engine accepted
     * nothing, and a list that already carries a sound effect speaks for itself; both stay silent.
     */
    static void click(MenuHolder holder, List<Ref> actions) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(actions, "actions");
        if (actions.isEmpty() || declaresSound(actions)) {
            return;
        }
        play(holder, CLICK_KEY, CLICK_VOLUME, CLICK_PITCH);
    }

    /** Play the page-turn tone for a pagination button. */
    static void page(MenuHolder holder) {
        Objects.requireNonNull(holder, "holder");
        play(holder, PAGE_KEY, PAGE_VOLUME, PAGE_PITCH);
    }

    /** Play the refusal tone for a gesture whose requirements denied it. */
    static void deny(MenuHolder holder) {
        Objects.requireNonNull(holder, "holder");
        play(holder, DENY_KEY, DENY_VOLUME, DENY_PITCH);
    }

    /** Whether the gesture's own actions already play something, in which case the engine adds nothing. */
    private static boolean declaresSound(List<Ref> actions) {
        for (Ref ref : actions) {
            String id = ref.id();
            if (SOUND_ACTION.equals(id) || RAW_SOUND_ACTION.equals(id) || RAW_SOUND_ALIAS.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Play {@code key} to the viewer at their own position. The viewer is resolved live, so a player who logged off
     * between the click and this call is simply skipped rather than throwing into the click dispatch.
     */
    private static void play(MenuHolder holder, String key, float volume, float pitch) {
        Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
        if (live == null) {
            return;
        }
        live.playSound(Objects.requireNonNull(live.getLocation(), "player location"), key, volume, pitch);
    }
}
