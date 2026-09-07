package com.uxplima.uxmessentials.shared.menu;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * A viewer for a test that needs one and no server.
 *
 * <p>uxmLib's menu engine spends a live {@link Player} rather than a record of a uuid and a name, which is right:
 * a menu cannot be drawn for somebody who is not here. A test that only ever asked what a context carries used to
 * build the record in one line, and this is the same line for the type the engine takes.
 *
 * <p>It answers exactly what a viewer is asked for on that path: the uuid, the name, and that they are online. A
 * test that needs a viewer to do anything else stands up MockBukkit and uses a real one.
 */
public final class TestViewer {

    private TestViewer() {}

    /** A viewer called {@code name}, with a uuid of its own. */
    public static Player named(String name) {
        return of(UUID.randomUUID(), name);
    }

    /** A viewer with exactly this uuid and this name, for a test that pins one or the other. */
    public static Player of(UUID id, String name) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Player viewer = mock(Player.class);
        lenient().when(viewer.getUniqueId()).thenReturn(id);
        lenient().when(viewer.getName()).thenReturn(name);
        lenient().when(viewer.name()).thenReturn(Component.text(name));
        lenient().when(viewer.isOnline()).thenReturn(true);
        return viewer;
    }
}
