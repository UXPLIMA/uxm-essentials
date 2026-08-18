"""The warp editor's item lore, collapsed from fragments into one block per item.

Each editor button used to draw its lore from two or three catalog keys the spec glued together with
a blank line. The canon gives an item one block, so each button now has a single key and the
fragments are gone. This writes the blocks; the spec and the key enum are edited alongside it.
"""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, row

CRUMB = "warp editor"

BLOCKS = {
    "warp.editor.teleport.lore": lore(
        CRUMB,
        ["stand at the warp to see what a player arrives to."],
        [row("at", "{warp_edit_world} {warp_edit_x}, {warp_edit_y}, {warp_edit_z}")],
        [action("click", "to teleport there")],
    ),
    "warp.editor.icon.lore": lore(
        CRUMB,
        ["the item shown for this warp in every menu it appears in."],
        [row("current", "{warp_edit_icon}")],
        [
            action("left-click", "with an item in hand to set it"),
            action("right-click", "to clear the icon"),
        ],
    ),
    "warp.editor.category.lore": lore(
        CRUMB,
        ["the folder this warp is filed under in the browse menu."],
        [row("current", "{warp_edit_category}")],
        [action("click", "to assign a category")],
    ),
    "warp.editor.lock.lore": lore(
        CRUMB,
        ["a locked warp refuses everyone who is not staff, whatever else it allows."],
        [row("current", "{warp_edit_lock}")],
        [action("click", "to toggle the lock")],
    ),
    "warp.editor.password.lore": lore(
        CRUMB,
        ["a word players have to type before they are let through."],
        [row("current", "{warp_edit_password}")],
        [
            action("left-click", "to set a password"),
            action("right-click", "to clear it"),
        ],
    ),
    "warp.editor.welcome.lore": lore(
        CRUMB,
        ["what a player is shown the moment they arrive."],
        [
            row("current", "{warp_edit_welcome}"),
            row("type", "{warp_edit_welcome_type}", "muted"),
        ],
        [action("click", "to manage the messages")],
    ),
    "warp.editor.sounds.lore": lore(
        CRUMB,
        ["what the player hears leaving, and what they hear arriving."],
        [
            row("departure", "{warp_edit_sound_departure}"),
            row("arrival", "{warp_edit_sound_arrival}"),
        ],
        [action("click", "to open the sound selector")],
    ),
    "warp.editor.particles.lore": lore(
        CRUMB,
        ["the effect drawn where the player leaves, and where they land."],
        [
            row("departure", "{warp_edit_particle_departure}"),
            row("arrival", "{warp_edit_particle_arrival}"),
        ],
        [action("click", "to open the particle selector")],
    ),
    "warp.editor.warmup.lore": lore(
        CRUMB,
        ["how long a player must stand still before this warp fires, whatever the module default is."],
        [row("current", "{warp_edit_warmup}")],
        [
            action("left-click", "to set the seconds"),
            action("right-click", "to clear the override"),
        ],
    ),
    "warp.editor.cooldown.lore": lore(
        CRUMB,
        ["how long a player waits before using this warp again, whatever the module default is."],
        [row("current", "{warp_edit_cooldown}")],
        [
            action("left-click", "to set the seconds"),
            action("right-click", "to clear the override"),
        ],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
