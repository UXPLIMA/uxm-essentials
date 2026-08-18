"""The player-warps menu lore, written in the canonical skeleton."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, note, row

BLOCKS = {
    "pwarp.gui.list.entry-lore": lore(
        "a warp another player opened",
        ["a public spot someone put on the map."],
        [
            row("owner", "{pwarp_owner}"),
            row("world", "{pwarp_world}"),
            row("at", "{pwarp_x}, {pwarp_y}, {pwarp_z}"),
            row("visibility", "{pwarp_visibility}", "muted"),
        ],
        [action("click", "to edit this warp")],
    ),
    "pwarp.gui.browse.sort-lore": lore(
        "browse control",
        ["reorder the grid by rating, newest, visits, favourites, name or distance."],
        actions=[action("click", "to cycle the sort")],
    ),
    "pwarp.gui.browse.search-lore": lore(
        "browse control",
        ["narrow the grid to the warps whose name matches what you type."],
        actions=[action("click", "to type a name")],
    ),
    "pwarp.gui.browse.mine-lore": lore(
        "browse filter",
        ["show only the warps you opened yourself."],
        actions=[action("click", "to filter to yours")],
    ),
    "pwarp.gui.browse.favourites-lore": lore(
        "browse filter",
        ["show only the warps you have starred."],
        actions=[action("click", "to filter to favourites")],
    ),
    "pwarp.gui.browse.all-lore": lore(
        "browse filter",
        ["drop the filter and show every public warp on the server."],
        actions=[action("click", "to clear the filter")],
    ),
    "pwarp.gui.browse.create-lore": lore(
        "browse control",
        ["open a warp of your own where you are standing."],
        actions=[action("click", "to create a warp here")],
    ),
    "pwarp.gui.browse.categories-lore": lore(
        "browse control",
        ["browse by category, or jump straight to a shortcut."],
        actions=[action("click", "to open the categories hub")],
    ),
    "pwarp.gui.browse.empty-lore": lore(
        "empty directory",
        ["nobody has opened a warp yet.", "stand where you want yours and make the first one."],
        actions=[action("run", "/pwarp set to create one")],
    ),
    "pwarp.gui.view.teleport-lore": lore(
        "warp action",
        ["leave where you are and arrive at this warp."],
        actions=[action("click", "to warp here")],
    ),
    "pwarp.gui.view.teleport-locked-lore": lore(
        "warp action",
        ["this warp is password protected: the owner set a word you have to know."],
        actions=[action("click", "to enter the password")],
    ),
    "pwarp.gui.view.favourite-lore": lore(
        "warp action",
        ["star this warp so it is one click away from the favourites filter."],
        actions=[action("click", "to add to favourites")],
    ),
    "pwarp.gui.view.unfavourite-lore": lore(
        "warp action",
        ["take this warp off your favourites list."],
        actions=[action("click", "to remove from favourites")],
    ),
    "pwarp.gui.view.rate-lore": lore(
        "warp action",
        ["award this warp one to five stars: the average decides where it sits in the top list."],
        actions=[action("click", "to rate")],
    ),
    "pwarp.gui.view.manage-lore": lore(
        "owner tools",
        ["edit this warp's settings, its members and who may enter."],
        actions=[action("click", "to manage")],
    ),
    "pwarp.gui.rate.star-1-lore": lore(
        "rating",
        ["the lowest rating: this warp was not worth the trip."],
        actions=[action("click", "to rate 1 star")],
    ),
    "pwarp.gui.rate.star-2-lore": lore(
        "rating",
        ["below average: something here, but not much."],
        actions=[action("click", "to rate 2 stars")],
    ),
    "pwarp.gui.rate.star-3-lore": lore(
        "rating",
        ["a fair warp: worth a visit."],
        actions=[action("click", "to rate 3 stars")],
    ),
    "pwarp.gui.rate.star-4-lore": lore(
        "rating",
        ["a good warp: you would go back."],
        actions=[action("click", "to rate 4 stars")],
    ),
    "pwarp.gui.rate.star-5-lore": lore(
        "rating",
        ["the highest rating: one of the best on the server."],
        actions=[action("click", "to rate 5 stars")],
    ),
    "pwarp.gui.categories.header-lore": lore(
        "player warps",
        [
            "every warp here was opened by a player, not by staff.",
            "visit what others built, or put your own on the map.",
        ],
        actions=[action("pick", "a shortcut or a category below")],
    ),
    "pwarp.gui.categories.browse-all-lore": lore(
        "shortcut",
        ["the full grid: every public warp on the server."],
        actions=[action("click", "to browse them all")],
    ),
    "pwarp.gui.categories.mine-lore": lore(
        "shortcut",
        ["the warps you opened, with the tools to manage them."],
        actions=[action("click", "to see your warps")],
    ),
    "pwarp.gui.categories.favourites-lore": lore(
        "shortcut",
        ["the warps you starred, in one grid."],
        actions=[action("click", "to see your favourites")],
    ),
    "pwarp.gui.categories.top-lore": lore(
        "shortcut",
        ["the warps other players rated highest."],
        actions=[action("click", "to see the best")],
    ),
    "pwarp.gui.categories.entry-lore": lore(
        "warp directory",
        ["one theme of the directory: the warps their owners filed here."],
        actions=[action("click", "to browse {pwarp_category_name} warps")],
    ),
    "pwarp.gui.categories.sponsor-lore": lore(
        "featured warp",
        ["its owner paid to keep this warp on the front page."],
        [row("owner", "{pwarp_sponsor_owner}")],
        [action("click", "to visit")],
    ),
    "pwarp.gui.members.entry-lore": lore(
        "warp access",
        ["a player who may use this warp beyond what the public may."],
        [row("role", "{pwarp_member_role}")],
        [action("click", "to remove this member")],
    ),
    "pwarp.gui.whitelist.entry-lore": lore(
        "warp access",
        ["a player allowed in while the warp is closed to everyone else."],
        actions=[action("click", "to remove from the whitelist")],
    ),
    "pwarp.gui.bans.entry-lore": lore(
        "warp access",
        ["a player the owner shut out of this warp."],
        [
            row("reason", "{pwarp_ban_reason}"),
            row("expires", "{pwarp_ban_until}", "muted"),
        ],
        [action("click", "to lift the ban")],
    ),
    "pwarp.gui.editor.value-lore": lore(
        "warp editor",
        ["one of this warp's settings, as it stands now."],
        [row("current", "{value}")],
        [action("click", "to change it")],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
