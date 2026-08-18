"""The remaining single-menu lore blocks: vaults, announcements, ranks and inventory rollback."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, row

BLOCKS = {
    "vaults.selector.entry.lore": lore(
        "your item storage",
        ["storage that lives in the database, so it survives deaths and rollbacks."],
        actions=[action("click", "to open this vault")],
    ),
    "vaults.selector.locked.lore": lore(
        "not unlocked yet",
        ["a slot you have not claimed: unlock it and it is yours for good."],
        actions=[action("run", "/vault {index} to unlock it")],
    ),
    "communication.gui.announcer.entry-lore": lore(
        "an automatic message",
        ["a message the server posts by itself, on its own schedule."],
        [
            row("lines", "{announcement_lines}", "level"),
            row("channels", "{announcement_channels}"),
        ],
    ),
    "communication.announce.editor.entry-lore": lore(
        "announcement editor",
        ["a scheduled message, as it is configured now."],
        [
            row("state", "{state}"),
            row("lines", "{lines}", "level"),
            row("channels", "{channels}"),
        ],
        [action("click", "to edit it")],
    ),
    "ranks.gui-current-lore": lore(
        "where you stand",
        ["the rank you hold now, and how many times you have started over."],
        [
            row("rank", "{ranks_current}", "rank"),
            row("prestige", "{ranks_prestige}", "level"),
        ],
    ),
    "ranks.gui-next-lore": lore(
        "what is ahead",
        ["the rank you climb to next, and what it takes to get there."],
        [
            row("next", "{ranks_next}", "rank"),
            row("cost", "{ranks_next_cost}", "money"),
            row("requires", "{ranks_next_requirements}", "subtext"),
        ],
    ),
    "invrollback.gui.shulker-lore": lore(
        "a saved inventory",
        ["what this player was carrying at the moment it was taken."],
        [
            row("player", "{player}"),
            row("cause", "{cause}"),
            row("when", "{time}", "muted"),
            row("part", "{part}<dim>/</dim>{parts}", "muted"),
        ],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
