"""The homes module's menu lore, written in the canonical skeleton."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, note, row

BLOCKS = {
    "home.menu.filled-lore": lore(
        "Home",
        "one of your saved homes",
        ["a place you saved, one click away."],
        [
            row("world", "{world}"),
            row("at", "{x}, {y}, {z}"),
            row("set", "{created}", "muted"),
        ],
        [action("click", "to manage this home")],
    ),
    "home.menu.empty-lore": lore(
        "Empty Slot",
        "no home saved here yet",
        ["stand where you want to return to, then claim this slot."],
        [
            row("homes", "{used}<dim>/</dim>{limit}"),
            row("slot", "free", "good"),
        ],
        [action("click", "to set a home here")],
    ),
    "home.action.info.lore": lore(
        "Home Details",
        "where this home sits",
        ["the exact spot this home returns you to."],
        [
            row("world", "{home_world}"),
            row("at", "{home_x}, {home_y}, {home_z}"),
            row("set", "{home_created}", "muted"),
        ],
    ),
    "home.action.teleport.lore": lore(
        "Travel",
        "home action",
        ["leave where you are and arrive at this home."],
        [row("cost", "free", "good")],
        [action("click", "to travel to this home")],
    ),
    "home.action.delete.lore": lore(
        "Delete",
        "home action",
        ["frees the slot for a new home.", "the saved position is not kept."],
        [row("undo", "not possible", "bad")],
        [action("click", "to remove this home")],
    ),
    "home.action.relocate.lore": lore(
        "Re-anchor",
        "home action",
        ["moves this home to where you are standing.", "the name and the icon stay as they are."],
        [row("new spot", "your position")],
        [action("click", "to re-anchor to your position")],
    ),
    "home.action.rename.lore": lore(
        "Rename",
        "home action",
        ["gives this home a label you pick.", "the slot number does not change."],
        [row("current", "{home_name}")],
        [action("click", "to set a display label")],
    ),
    "home.action.icon.lore": lore(
        "Icon",
        "home action",
        ["chooses the block this home shows in the grid."],
        [row("current", "{home_icon_name}")],
        [action("click", "to pick a new icon")],
    ),
    "home.action.visibility.public.lore": lore(
        "Public",
        "home visibility",
        ["anyone on the server may travel here."],
        [row("state", "public", "good")],
        [action("click", "to make it private")],
    ),
    "home.action.visibility.private.lore": lore(
        "Private",
        "home visibility",
        ["only you and the players you invite may travel here."],
        [row("state", "private", "bad")],
        [action("click", "to make it public")],
    ),
    "home.action.invites.lore": lore(
        "Invites",
        "home action",
        ["decides who else may travel to this home.", "an invited player travels here as you do."],
        [row("scope", "invited players only")],
        [action("click", "to manage who may visit")],
    ),
    "home.invites.entry.lore": lore(
        "Invited Player",
        "home invite",
        ["this player may travel to the home while the invite stands."],
        [row("access", "granted", "good")],
        [action("click", "to revoke this invite")],
    ),
    "home.invites.add.lore": lore(
        "Invite Someone",
        "home invite",
        ["type a name in chat and that player may travel here."],
        [row("input", "chat")],
        [action("click", "to type a player name")],
    ),
    "home.icon.reset.lore": lore(
        "Default Icon",
        "icon picker",
        ["puts the plain bed back on this home."],
        [row("icon", "bed")],
        [action("click", "to use the default bed")],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
