package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The per-open data a menu binding sees: who is viewing, the optional domain subject the menu was opened for
 * (a warp, a home owner, ...), the current page, and — while a list is rendered or clicked — the live list
 * element. The engine creates it; it is public only so feature binding lambdas can read it.
 *
 * <p>Immutable. {@link #withEntry} and {@link #withPage} return copies rather than mutating, because the same
 * base context is reused across every slot of a list page and must not leak one element's identity into the next.
 */
public final class MenuContext {

    private final PlayerRef viewer;

    @Nullable private final Object subject;

    private final int page;

    private final int pageCount;

    @Nullable private final Object entry;

    private MenuContext(PlayerRef viewer, @Nullable Object subject, int page, int pageCount, @Nullable Object entry) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.subject = subject;
        this.page = page;
        this.pageCount = pageCount;
        this.entry = entry;
    }

    /** Opens a fresh context with no list element bound yet and a single-page count until the renderer knows better. */
    public static MenuContext of(PlayerRef viewer, @Nullable Object subject, int page) {
        return new MenuContext(viewer, subject, page, 1, null);
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public int page() {
        return page;
    }

    /** How many pages the menu's list spans, one-based; the renderer stamps it before drawing static items. */
    public int pageCount() {
        return pageCount;
    }

    public Optional<Object> subjectRaw() {
        return Optional.ofNullable(subject);
    }

    public Optional<Object> entry() {
        return Optional.ofNullable(entry);
    }

    /**
     * The domain subject cast to {@code type}. A mismatch means a binding asked for the wrong context, so we
     * fail loudly rather than return null.
     */
    public <T> T subject(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = subjectRaw().orElseThrow(() -> new IllegalStateException("menu has no subject"));
        if (!type.isInstance(value)) {
            throw new IllegalStateException("menu subject is not a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /** The live list element cast to {@code type}; same fail-loud contract as {@link #subject(Class)}. */
    public <T> T entry(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = entry().orElseThrow(() -> new IllegalStateException("menu has no entry"));
        if (!type.isInstance(value)) {
            throw new IllegalStateException("menu entry is not a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /** A copy bound to one list element, leaving viewer, subject, page and page count untouched. */
    public MenuContext withEntry(Object entry) {
        Objects.requireNonNull(entry, "entry");
        return new MenuContext(viewer, subject, page, pageCount, entry);
    }

    /** A copy on a new page, used when the renderer or listener advances pagination; resets nothing else. */
    public MenuContext withPage(int page) {
        return new MenuContext(viewer, subject, page, pageCount, entry);
    }

    /** A copy carrying the page count the renderer computed, so a static item's {@code %max_page%} can read it. */
    public MenuContext withPageCount(int pageCount) {
        return new MenuContext(viewer, subject, page, pageCount, entry);
    }
}
