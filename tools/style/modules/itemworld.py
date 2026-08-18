"""The item-world lore: the recipe viewer and the nearby entity tally."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import lore, row

BLOCKS = {
    "itemworld.recipe.gui.result-lore": lore(
        "Result",
        "what this recipe makes",
        ["the item that comes out when the ingredients are laid out as shown."],
    ),
    "itemworld.recipe.gui.ingredient-lore": lore(
        "Ingredient",
        "part of this recipe",
        ["one of the items the recipe consumes."],
    ),
    "itemworld.entitycount.gui.entry-lore": lore(
        "Entity",
        "counted near you",
        ["how many of this kind are loaded around you right now."],
        [
            row("count", "{entity_count}", "level"),
            row("radius", "{entity_radius}", "muted"),
        ],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
