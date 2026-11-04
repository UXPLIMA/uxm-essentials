package com.uxplima.uxmessentials.guard.domain.engine;

/** The cells of one chunk, bottom section first. Blocks above or below the world use the nearest section. */
final class Column {

    private final Cell[] cells;

    Column(Cell[] cells) {
        this.cells = cells;
    }

    RegionSet at(int x, int y, int z, int minSection, int maxSection, WorldIndex index) {
        int section = Math.max(minSection, Math.min(maxSection, y >> 4));
        return cells[section - minSection].at(x, y, z, index);
    }
}
