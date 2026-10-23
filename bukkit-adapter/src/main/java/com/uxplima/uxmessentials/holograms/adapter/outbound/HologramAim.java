package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;

import com.uxplima.uxmlib.text.GlyphWidthTable;
import org.jspecify.annotations.NullMarked;

/**
 * Where a look meets a hologram's text, worked out on the server.
 *
 * <p>A client sends a click on an entity only within its reach, three blocks for a player in survival or adventure,
 * so a hologram's click box answers only a reader who walks up to it. A board is read from further away than that.
 * The server knows where every hologram stands and what each reader sees on it, so it works the click out itself:
 * the text is a panel turned towards the reader, as wide as the widest line that reader is shown and as tall as its
 * lines, and a look that crosses it within the reach is a click on it.
 */
@NullMarked
final class HologramAim {

    /** A text display draws forty pixels of its font to a block. */
    static final double BLOCKS_PER_PIXEL = 1.0 / 40.0;

    /** A head drawn inside a line is eight pixels wide, with the gap after it. */
    static final int HEAD_PIXELS = 9;

    /** How far past the drawn text a look still counts, so the edge of a letter is not a miss. */
    static final double MARGIN = 0.15;

    private static final double PARALLEL = 1.0e-9;

    private HologramAim() {}

    /**
     * The text of a hologram as the reader sees it, standing upright.
     *
     * @param x where its middle stands, east to west
     * @param z where its middle stands, north to south
     * @param bottom the height of its lowest line
     * @param height how tall its lines are together
     * @param halfWidth half its widest line
     */
    record Panel(double x, double z, double bottom, double height, double halfWidth) {}

    /**
     * How far along the look, in lengths of {@code (dx, dy, dz)}, it meets {@code panel} turned towards the eye, or
     * empty when it passes beside, above or below the text, looks away from it, or meets it beyond {@code reach}.
     */
    static OptionalDouble hit(
            double ex, double ey, double ez, double dx, double dy, double dz, Panel panel, double reach) {
        Objects.requireNonNull(panel, "panel");
        double nx = ex - panel.x();
        double nz = ez - panel.z();
        double flat = Math.hypot(nx, nz);
        if (flat < PARALLEL) {
            return OptionalDouble.empty();
        }
        return hitFacing(ex, ey, ez, dx, dy, dz, panel, nx / flat, nz / flat, reach);
    }

    /**
     * The same, for a panel that keeps its own facing: {@code (nx, nz)} is a level unit vector square to its face.
     * Either side of the face is met, since a click on the back of a board is a click on the board.
     */
    static OptionalDouble hitFacing(
            double ex,
            double ey,
            double ez,
            double dx,
            double dy,
            double dz,
            Panel panel,
            double nx,
            double nz,
            double reach) {
        Objects.requireNonNull(panel, "panel");
        double towards = dx * nx + dz * nz;
        if (Math.abs(towards) < PARALLEL) {
            return OptionalDouble.empty();
        }
        double along = ((panel.x() - ex) * nx + (panel.z() - ez) * nz) / towards;
        if (along <= 0 || along > reach) {
            return OptionalDouble.empty();
        }
        double lateral = (ex + dx * along - panel.x()) * -nz + (ez + dz * along - panel.z()) * nx;
        double py = ey + dy * along;
        boolean across = Math.abs(lateral) <= panel.halfWidth() + MARGIN;
        boolean up = py >= panel.bottom() - MARGIN && py <= panel.bottom() + panel.height() + MARGIN;
        return across && up ? OptionalDouble.of(along) : OptionalDouble.empty();
    }

    /** The pixel width of the widest line of {@code text}, a head drawn inside it counting as a glyph of its own. */
    static int widestLinePixels(Component text) {
        Objects.requireNonNull(text, "text");
        List<StringBuilder> lines = new ArrayList<>();
        List<int[]> heads = new ArrayList<>();
        lines.add(new StringBuilder());
        heads.add(new int[1]);
        walk(text, lines, heads);
        int widest = 0;
        for (int i = 0; i < lines.size(); i++) {
            widest = Math.max(widest, GlyphWidthTable.widthOf(lines.get(i), false) + heads.get(i)[0] * HEAD_PIXELS);
        }
        return widest;
    }

    /** How many lines {@code text} is drawn as: one, and one more for each line break in it. */
    static int lineCount(Component text) {
        Objects.requireNonNull(text, "text");
        List<StringBuilder> lines = new ArrayList<>();
        lines.add(new StringBuilder());
        walk(text, lines, new ArrayList<>(List.of(new int[1])));
        return lines.size();
    }

    private static void walk(Component node, List<StringBuilder> lines, List<int[]> heads) {
        if (node instanceof TextComponent text) {
            String content = text.content();
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '\n') {
                    lines.add(new StringBuilder());
                    heads.add(new int[1]);
                } else {
                    lines.get(lines.size() - 1).append(c);
                }
            }
        } else if (node instanceof ObjectComponent) {
            heads.get(heads.size() - 1)[0]++;
        }
        for (Component child : node.children()) {
            walk(child, lines, heads);
        }
    }
}
