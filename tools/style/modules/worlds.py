"""The worlds editor lore: the world list, the creation wizard, and the world summary."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, row

BLOCKS = {
    "world.editor.list.entry-lore": lore(
        "World",
        "a world on this server",
        ["one of the worlds this server runs, loaded or waiting."],
        [
            row("environment", "{world_environment}"),
            row("loaded", "{world_loaded}"),
        ],
        [
            action("left-click", "to edit this world"),
            action("right-click", "to teleport there"),
        ],
    ),
    "world.editor.create.button-lore": lore(
        "New World",
        "world editor",
        ["set a name, a seed and a generator, then build the world."],
        actions=[action("click", "to start")],
    ),
    "world.editor.create.name-lore": lore(
        "Name",
        "required",
        ["the folder name the world is saved under, and the name commands use."],
        actions=[action("click", "to type a name")],
    ),
    "world.editor.create.seed-lore": lore(
        "Seed",
        "optional",
        ["the number the generator starts from: leave it empty for a random world."],
        actions=[action("click", "to type a seed")],
    ),
    "world.editor.create.confirm-lore": lore(
        "Create",
        "world editor",
        ["build the world with the settings above: generation runs in the background."],
        actions=[action("click", "to create it")],
    ),
    "world.editor.main.summary-lore": lore(
        "World",
        "what this world is",
        ["the shape of this world and who is in it."],
        [
            row("environment", "{world_main_environment}"),
            row("type", "{world_main_type}"),
            row("loaded", "{world_main_loaded}"),
            row("players", "{world_main_players}", "level"),
            row("alias", "{world_main_alias}", "muted"),
        ],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
