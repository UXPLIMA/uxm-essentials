package com.uxplima.uxmessentials.tablist.domain;

import java.util.Objects;

/** One materialized cell in an exact virtual tab-list grid. */
public record VirtualTabCell<T>(int slot, Content<T> content) {

    public VirtualTabCell {
        if (slot <= 0) {
            throw new IllegalArgumentException("a virtual tab cell slot must be positive, got " + slot);
        }
        Objects.requireNonNull(content, "content");
    }

    /** The mutually-exclusive owner of a cell. */
    public sealed interface Content<T> permits Empty, Fixed, Player {}

    /** A deliberately materialized empty cell; it still becomes a synthetic client entry. */
    public record Empty<T>() implements Content<T> {}

    /** Operator-authored fixed content. */
    public record Fixed<T>(TablistFiller filler) implements Content<T> {
        public Fixed {
            Objects.requireNonNull(filler, "filler");
        }
    }

    /** One roster occupant assigned through the named player group. */
    public record Player<T>(String groupId, T occupant) implements Content<T> {
        public Player {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(occupant, "occupant");
        }
    }
}
