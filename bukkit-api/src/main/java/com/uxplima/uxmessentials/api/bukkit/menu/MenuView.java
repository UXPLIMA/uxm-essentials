package com.uxplima.uxmessentials.api.bukkit.menu;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * What one open menu looks like to a handler you registered: who is viewing it, which page they are on, the
 * arguments it was opened with, and the list element being rendered when a list is in play.
 *
 * <p>The viewer is given as an id and a name rather than a live {@code Player}, because a handler can run off the
 * tick thread (a list source is queried asynchronously). Look the player up yourself, on a thread where that is
 * safe, when you need the live handle; a click handler is handed one directly through {@link MenuClick#player()}.
 *
 * <p>There is deliberately no "which menu is this" accessor. One id can be used by many menus, and the way to tell
 * them apart is the argument the spec passes with it: an action written {@code ["my-action:home-list"]} arrives as
 * {@link MenuClick#arg()}, and a requirement written {@code my-cond:home-list} arrives in its map. A placeholder
 * that must vary per menu is registered as one id per menu.
 */
@NullMarked
public interface MenuView {

    /** Who sees the menu, and whom every player-scoped placeholder resolves against. */
    UUID viewerId();

    /** The viewer's name at the time the menu was opened. */
    String viewerName();

    /**
     * Who triggered the open. The same player as the viewer for an ordinary self-open, and the opener when a menu
     * was opened for somebody else.
     */
    UUID executorId();

    /** The page being rendered, counting from zero. */
    int page();

    /** How many pages the menu's list spans, counting from one. */
    int pageCount();

    /** The typed command arguments the menu was opened with, keyed by argument name; empty when it took none. */
    Map<String, String> arguments();

    /**
     * The list element currently being rendered or clicked, when it is an instance of {@code type}.
     *
     * <p>This is how a list source you registered pays off: return your own objects from the source, then read them
     * back here in a placeholder or an action to render and act on one row. Empty when no list is in play, or when
     * the element belongs to somebody else's list source.
     */
    <T> Optional<T> entry(Class<T> type);

    /**
     * The domain subject the menu was opened for, when it is an instance of {@code type}. Menus opened by
     * uxmEssentials itself carry internal objects here, so a value of a type you do not own is not part of the
     * compatibility promise; empty when the menu carries no subject or one of another type.
     */
    <T> Optional<T> subject(Class<T> type);
}
