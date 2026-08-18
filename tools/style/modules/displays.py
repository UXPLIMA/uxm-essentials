"""The hologram and npc list lore: the two entity lists that share a shape."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, row

BLOCKS = {
    "hologram.gui.list.entry-lore": lore(
        "a floating display",
        ["text hanging in the world, with nothing solid behind it."],
        [
            row("lines", "{hologram_lines}", "level"),
            row("world", "{hologram_world}"),
            row("at", "{hologram_x}, {hologram_y}, {hologram_z}"),
        ],
        [action("click", "to edit this hologram")],
    ),
    "npc.gui.list.entry-lore": lore(
        "a standing character",
        ["a character players can look at, click, and be sent somewhere by."],
        [
            row("type", "{npc_type}"),
            row("world", "{npc_world}"),
            row("at", "{npc_x}, {npc_y}, {npc_z}"),
        ],
        [action("click", "to edit this npc")],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
