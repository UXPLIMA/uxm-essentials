package com.uxplima.uxmessentials.bootstrap;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/** The box the plugin prints to the console when it enables. */
@NullMarked
final class StartupBanner {

    /** The width of every line, frame included. */
    static final int WIDTH = 60;

    private StartupBanner() {}

    static List<String> lines(String version) {
        Objects.requireNonNull(version, "version");
        return List.of(
                "╔" + "═".repeat(WIDTH - 2) + "╗",
                boxed(""),
                boxed("   ██╗   ██╗██╗  ██╗███╗   ███╗"),
                boxed("   ██║   ██║╚██╗██╔╝████╗ ████║"),
                boxed("   ██║   ██║ ╚███╔╝ ██╔████╔██║"),
                boxed("   ██║   ██║ ██╔██╗ ██║╚██╔╝██║"),
                boxed("   ╚██████╔╝██╔╝ ██╗██║ ╚═╝ ██║"),
                boxed("    ╚═════╝ ╚═╝  ╚═╝╚═╝     ╚═╝"),
                boxed(""),
                boxed("   Essentials v" + version),
                boxed("   Author: uxplima"),
                boxed(""),
                "╚" + "═".repeat(WIDTH - 2) + "╝");
    }

    /** One line inside the frame, padded to the frame's width. A text too long to fit widens its own line. */
    private static String boxed(String text) {
        int room = WIDTH - 2;
        return "║" + text + " ".repeat(Math.max(1, room - text.length())) + "║";
    }
}
