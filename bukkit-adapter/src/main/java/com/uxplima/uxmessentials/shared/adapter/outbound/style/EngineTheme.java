package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.NullMarked;

/**
 * The colours the menu engine is handed, held so a render never reads a file.
 *
 * <p>uxmLib's renderers take a {@link Supplier} rather than a value, and the reason is a live one: an operator
 * reloads {@code theme.conf} while the server runs, and a renderer is built once and then called per item. A
 * theme captured at construction would be the stale value that reload exists to replace.
 *
 * <p>Reading the file per render would answer that correctly and pay disk for it on the hottest path the engine
 * has, so the file is read on a reload and the answer is held here. {@link #reload()} is what the reload step
 * calls, beside the one that re-reads the palette this plugin draws its own text with.
 */
@NullMarked
public final class EngineTheme implements Supplier<Theme> {

    private final Path themeFolder;

    private final AtomicReference<Theme> current;

    public EngineTheme(Path themeFolder) {
        this.themeFolder = Objects.requireNonNull(themeFolder, "themeFolder");
        this.current = new AtomicReference<>(ThemeFile.theme(themeFolder));
    }

    /** Re-read the theme files; the next render draws in the new colours. */
    public void reload() {
        current.set(ThemeFile.theme(themeFolder));
    }

    @Override
    public Theme get() {
        return Objects.requireNonNull(current.get(), "theme");
    }
}
