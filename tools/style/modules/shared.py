"""The lore shared across modules: the hub, the pickers, the online list, and the property rows.

A property row is the same item in a dozen editors: a setting with its current value and a click that
changes it, so every one of them is written from the same two helpers rather than key by key.
"""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, row


def setting(verb="to toggle it"):
    """One row of a settings panel: what it is set to now, and the click that changes it."""
    return lore(
        "Setting",
        "your preference",
        ["one of your own settings, as it stands now."],
        [row("current", "{value}")],
        [action("click", verb)],
    )


BLOCKS = {
    "gui.hub.entry.lore": lore(
        "Module",
        "admin hub",
        ["one of the plugin's modules, with its own panel behind it."],
        [row("module", "{module}")],
        [action("click", "to open this module")],
    ),
    "gui.player-picker.custom-lore": lore(
        "Someone Else",
        "player picker",
        ["pick a player who is offline, hidden, or simply not on this page."],
        actions=[action("click", "to type a name")],
    ),
    "gui.duration-picker.custom-lore": lore(
        "Custom Span",
        "duration picker",
        ["type your own length instead of taking one of the offered spans."],
        [row("format", "30m, 2h, 7d", "muted")],
        [action("click", "to type a duration")],
    ),
    "list.gui.entry-lore": lore(
        "Player",
        "online now",
        ["someone playing on the server right now."],
        [
            row("world", "{world}"),
            row("status", "{status}"),
        ],
    ),
    "menu.editor.entry.lore": lore(
        "Menu",
        "menu editor",
        ["one of the menus on this server, open for editing."],
        [
            row("title", "{title}"),
            row("rows", "{rows}"),
            row("items", "{items}"),
        ],
        [action("click", "to manage this menu")],
    ),
    "teleport.gui.settings.value-lore": setting(),
    "messaging.gui.settings.value-lore": setting(),
    "presence.gui.settings.value-lore": setting(),
    "scoreboard.gui.value-lore": setting(),
    "poses.gui.value-lore": setting("to switch it"),
    "survival.gui.value-lore": setting(),
    "hologram.gui.editor.value-lore": lore(
        "Setting",
        "hologram editor",
        ["one of this hologram's settings, as it stands now."],
        [row("current", "{value}")],
        [action("click", "to change it")],
    ),
    "npc.gui.editor.value-lore": lore(
        "Setting",
        "npc editor",
        ["one of this npc's settings, as it stands now."],
        [row("current", "{value}")],
        [action("click", "to change it")],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
