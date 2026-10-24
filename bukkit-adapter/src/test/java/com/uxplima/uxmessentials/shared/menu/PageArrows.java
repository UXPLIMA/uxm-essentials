package com.uxplima.uxmessentials.shared.menu;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import com.uxplima.uxmlib.menu.spec.ItemType;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;

/**
 * The page arrows a shipped window file declares, for the golden tests that used to read them off a drawn window.
 *
 * <p>Each golden test froze the grid an old hand-built view drew, and those views drew a previous and a next arrow
 * on every page, the only page included. From uxmLib 0.119.0 to 0.210.0 an arrow with no page to turn to was hidden,
 * so a one-page fixture did not show them; since 0.211.0 they are drawn on every page again. The arrows are the
 * window's, at the same slots, with the same icon and the same words, and this reads them from the file so a golden
 * test holds all three.
 */
public final class PageArrows {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private PageArrows() {}

    /** Slot to {@code "MATERIAL key"} for every previous and next arrow in {@code resource}, the key without its @. */
    public static Map<Integer, String> declaredIn(String resource) {
        String hocon;
        try {
            hocon = Files.readString(RESOURCES.resolve(resource));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        Map<Integer, String> arrows = new TreeMap<>();
        for (MenuItemSpec item : new MenuSpecLoader().parse(hocon).items().values()) {
            if (item.type() != ItemType.NEXT && item.type() != ItemType.PREVIOUS) {
                continue;
            }
            String key = item.name().startsWith("@") ? item.name().substring(1) : item.name();
            for (int slot : item.slots().slots()) {
                arrows.put(slot, item.material() + " " + key);
            }
        }
        return arrows;
    }
}
