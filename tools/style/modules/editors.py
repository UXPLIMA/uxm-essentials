"""The editor panels' item lore: kits, kit categories, warp categories, warp welcome, banks and worlds.

These blocks replaced the fragment keys the specs used to glue together; the placeholders are the same
ones the fragments read, so nothing is asked of the renderer that it did not already provide.
"""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, note, row

KIT = "kit editor"
KIT_CATEGORY = "kit category"
WARP_CATEGORY = "warp category"

DELETE_WARNING = "this cannot be undone."

BLOCKS = {
    "kit.editor.kit-lore.lore": lore(
        "Kit",
        "kit manager",
        ["a kit as it is configured now."],
        [
            row("cooldown", "{kit_manager_seconds}ꜱ"),
            row("cost", "{kit_manager_cost}", "money"),
            row("permission", "{kit_manager_permission}", "muted"),
            row("one-time", "{kit_manager_onetime}"),
            row("first join", "{kit_manager_firstjoin}"),
            row("auto-equip", "{kit_manager_autoequip}"),
        ],
        [action("click", "to edit its settings")],
    ),
    "kit.editor.settings.permission.lore": lore(
        "Permission",
        KIT,
        ["when a kit requires a node, only players who hold it may claim it."],
        [row("current", "{kit_set_permission}")],
        [action("click", "to toggle the requirement")],
    ),
    "kit.editor.settings.onetime.lore": lore(
        "One-time",
        KIT,
        ["a one-time kit may be claimed once per player, and never again."],
        [row("current", "{kit_set_onetime}")],
        [action("click", "to toggle it")],
    ),
    "kit.editor.settings.cooldown.lore": lore(
        "Cooldown",
        KIT,
        ["how long a player waits between claims of this kit."],
        [row("current", "{kit_set_cooldown}ꜱ")],
        [action("click", "to enter a new value in chat")],
    ),
    "kit.editor.settings.cost.lore": lore(
        "Cost",
        KIT,
        ["what claiming this kit takes from the player's balance."],
        [row("current", "{kit_set_cost}", "money")],
        [action("click", "to enter a new value in chat")],
    ),
    "kit.editor.settings.display-name.lore": lore(
        "Display Name",
        KIT,
        ["the name players see on this kit's icon."],
        [row("current", "{kit_set_display_name}")],
        [action("click", "to edit it in chat")],
    ),
    "kit.editor.settings.display-material.lore": lore(
        "Display Material",
        KIT,
        ["the item this kit is shown as."],
        [row("current", "{kit_set_display_material}")],
        [action("click", "to set it to the item in your hand")],
    ),
    "kit.editor.settings.display-lore.lore": lore(
        "Display Lore",
        KIT,
        ["the extra lines written under this kit's name."],
        [row("lines", "{kit_set_display_lore_count}", "level")],
        [action("click", "to edit them in chat")],
    ),
    "kit.editor.settings.commands.lore": lore(
        "Commands",
        KIT,
        ["commands the server runs when this kit is claimed, on top of the items."],
        [row("commands", "{kit_set_commands_count}", "level")],
        [action("click", "to edit them in chat")],
    ),
    "kit.editor.settings.firstjoin.lore": lore(
        "First Join",
        KIT,
        ["a first-join kit is handed out the moment a player arrives for the first time."],
        [row("current", "{kit_set_firstjoin}")],
        [action("click", "to toggle it")],
    ),
    "kit.editor.settings.autoequip.lore": lore(
        "Auto-equip",
        KIT,
        ["armour in this kit goes straight onto the player instead of into their bag."],
        [row("current", "{kit_set_autoequip}")],
        [action("click", "to toggle it")],
    ),
    "kit.editor.settings.category.lore": lore(
        "Category",
        KIT,
        ["the folder this kit is filed under in the kit menu."],
        [row("current", "{kit_set_category}")],
        [action("click", "to choose a category")],
    ),
    "kit.editor.settings.delete.lore": lore(
        "Delete",
        KIT,
        ["remove this kit for good.", DELETE_WARNING],
        actions=[action("click", "to delete it")],
    ),
    "kit.editor.category.settings.display-name.lore": lore(
        "Display Name",
        KIT_CATEGORY,
        ["the name players see on this category's icon."],
        [row("current", "{cat_set_name}")],
        [action("click", "to edit it in chat")],
    ),
    "kit.editor.category.settings.display-material.lore": lore(
        "Display Material",
        KIT_CATEGORY,
        ["the item this category is shown as."],
        [row("current", "{cat_set_material}")],
        [action("click", "to set it to the item in your hand")],
    ),
    "kit.editor.category.settings.display-lore.lore": lore(
        "Display Lore",
        KIT_CATEGORY,
        ["the extra lines written under this category's name."],
        [row("lines", "{cat_set_lore_count}", "level")],
        [action("click", "to edit them in chat")],
    ),
    "kit.editor.category.settings.slot.lore": lore(
        "Slot",
        KIT_CATEGORY,
        ["where this category sits in the menu grid."],
        [row("current", "{cat_set_slot}", "level")],
        [action("click", "to set the slot in chat")],
    ),
    "kit.editor.category.settings.parent.lore": lore(
        "Parent",
        KIT_CATEGORY,
        ["the category this one sits inside, if any."],
        [row("current", "{cat_set_parent}")],
        [action("click", "to change the parent")],
    ),
    "kit.editor.category.settings.delete.lore": lore(
        "Delete",
        KIT_CATEGORY,
        ["remove this category; the kits in it are not deleted with it.", DELETE_WARNING],
        actions=[action("click", "to delete it")],
    ),
    "warp.manager.entry.lore": lore(
        "Warp",
        "warp manager",
        ["a warp as it is configured now."],
        [
            row("category", "{warp_manager_category}"),
            row("at", "{warp_manager_world} {warp_manager_x}, {warp_manager_y}, {warp_manager_z}"),
        ],
        [action("click", "to edit this warp")],
    ),
    "warp.editor.category.settings.display-name.lore": lore(
        "Display Name",
        WARP_CATEGORY,
        ["the name players see on this category's icon."],
        [row("current", "{warp_cat_set_name}")],
        [action("click", "to edit it in chat")],
    ),
    "warp.editor.category.settings.display-material.lore": lore(
        "Display Material",
        WARP_CATEGORY,
        ["the item this category is shown as."],
        [row("current", "{warp_cat_set_material}")],
        [action("click", "to set it to the item in your hand")],
    ),
    "warp.editor.category.settings.display-lore.lore": lore(
        "Display Lore",
        WARP_CATEGORY,
        ["the extra lines written under this category's name."],
        [row("lines", "{warp_cat_set_lore_count}", "level")],
        [action("click", "to edit them in chat")],
    ),
    "warp.editor.category.settings.slot.lore": lore(
        "Slot",
        WARP_CATEGORY,
        ["where this category sits in the menu grid."],
        [row("current", "{warp_cat_set_slot}", "level")],
        [action("click", "to set the slot in chat")],
    ),
    "warp.editor.category.settings.parent.lore": lore(
        "Parent",
        WARP_CATEGORY,
        ["the category this one sits inside, if any."],
        [row("current", "{warp_cat_set_parent}")],
        [action("click", "to change the parent")],
    ),
    "warp.editor.category.settings.delete.lore": lore(
        "Delete",
        WARP_CATEGORY,
        ["remove this category; the warps in it are not deleted with it.", DELETE_WARNING],
        actions=[action("click", "to delete it")],
    ),
    "warp.editor.welcome-manager.entry.lore": lore(
        "Message",
        "welcome messages",
        ["one of the lines a player is shown when they arrive at this warp."],
        [
            row("text", "{warp_welcome_text}", "subtext"),
            row("type", "{warp_welcome_type}", "muted"),
        ],
        [
            action("left-click", "to edit the text"),
            action("right-click", "to delete this message"),
            action("shift-click", "to cycle how it is shown"),
        ],
    ),
    "bank.list-gui-icon-lore": lore(
        "Bank",
        "a shared account",
        ["an account several players share, with its own balance and members."],
        [
            row("balance", "{bank_balance}", "money"),
            row("creator", "{bank_creator}"),
            row("members", "{bank_members}", "level"),
        ],
        [action("click", "to open this bank")],
    ),
    "bank.members-gui-member-lore": lore(
        "Member",
        "bank access",
        ["a player who may act on this bank, within what their role allows."],
        [row("role", "{bank_member_role}")],
        [action("right-click", "to remove this member")],
    ),
    "pay.confirm-gui-confirm-lore": lore(
        "Confirm",
        "payment",
        ["send the money: it leaves your balance the moment you click."],
        [
            row("target", "{payconfirm_target}"),
            row("amount", "{payconfirm_amount}", "money"),
        ],
        [action("click", "to approve and send")],
    ),
    "eco.history.gui-lore": lore(
        "Transaction",
        "economy ledger",
        ["one movement of money the economy recorded."],
        [
            row("id", "#{history_id}", "muted"),
            row("date", "{history_date}", "muted"),
            row("from", "{history_from}"),
            row("to", "{history_to}"),
            row("amount", "{history_amount}", "money"),
            row("reason", "{history_reason}", "subtext"),
        ],
    ),
    "world.editor.property.lore": lore(
        "Property",
        "world editor",
        ["one of this world's settings, as it stands now."],
        [row("current", "{world_grid_value}")],
        [
            action("left-click", "for the next value"),
            action("right-click", "for the previous one"),
        ],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
