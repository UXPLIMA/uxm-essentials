package com.uxplima.uxmessentials.scoreboard.domain;

import java.util.Objects;

/** Operator-authored right-edge rendering for one modern sidebar line. */
public sealed interface SidebarNumberFormat {

    record Default() implements SidebarNumberFormat {}

    record Blank() implements SidebarNumberFormat {}

    record Fixed(String source) implements SidebarNumberFormat {
        public Fixed {
            Objects.requireNonNull(source, "source");
        }
    }

    static SidebarNumberFormat defaultFormat() {
        return new Default();
    }

    static SidebarNumberFormat blank() {
        return new Blank();
    }

    static SidebarNumberFormat fixed(String source) {
        return new Fixed(source);
    }
}
