package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.jspecify.annotations.NullMarked;

/**
 * How many of a thing a layout has room to draw, and one console line the first time the answer is fewer than all
 * of them.
 *
 * <p>Every window that paints a list into configured slots stops at the shorter of the two, which is all it can
 * do. What none of them used to do is say so. An operator who gives the editor six property slots for eight
 * properties, or the colour picker ten slots for sixteen swatches, sees a window that opens and looks deliberate,
 * with the tail simply absent. Editing a conf and having nothing happen is the same experience as editing the
 * wrong conf, and there was nothing anywhere to tell the two apart.
 *
 * <p>Reported once per distinct shortfall rather than once per draw, and keyed by the counts rather than by the
 * caller, because the property objects these run inside are rebuilt on every redraw: keying on an object would
 * report every time the window opens.
 */
@NullMarked
public final class LayoutFit {

    private static final Logger LOG = Logger.getLogger(LayoutFit.class.getName());

    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    private static final LayoutFit SHARED = new LayoutFit();

    private LayoutFit() {}

    /**
     * The number of {@code items} the {@code slots} have room for, warning once when some do not fit. {@code what}
     * names the layout key an operator would edit, so the line points at the file rather than at the class.
     */
    public static int drawable(String what, int items, List<Integer> slots) {
        return SHARED.fit(what, items, slots);
    }

    private int fit(String what, int items, List<Integer> slots) {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(slots, "slots");
        if (items > slots.size() && reported.add(what + ' ' + items + '/' + slots.size())) {
            LOG.warning("event=layout_too_small key=" + what + " needs=" + items + " slots=" + slots.size() + " drawn="
                    + slots.size() + " (the rest are not drawn)");
        }
        return Math.min(items, slots.size());
    }
}
