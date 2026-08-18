"""The single-line button lore left over after the merges: one block each, same skeleton.

These items were a bare click hint under a name. A hint alone tells a player what the button does but
never what it is or what it costs them, so each one is written out: what the item is, the section that
explains it, the facts worth reading, then the click.
"""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, row

BLOCKS = {
    "baltop.gui-entry-lore": lore(
        "richest players",
        ["a player on the balance ladder, and what they are holding."],
        [row("balance", "{baltop_amount}", "money")],
    ),
    "bank.actions-gui-deposit-lore": lore(
        "bank action",
        ["move money out of your wallet and into the shared account."],
        actions=[action("click", "to enter an amount")],
    ),
    "bank.actions-gui-withdraw-lore": lore(
        "bank action",
        ["take money out of the shared account and into your wallet."],
        actions=[action("click", "to enter an amount")],
    ),
    "bank.actions-gui-members-lore": lore(
        "bank action",
        ["who may act on this bank, and what each of them is allowed to do."],
        actions=[action("click", "to manage the members")],
    ),
    "bank.actions-gui-logs-lore": lore(
        "bank action",
        ["every deposit and withdrawal this bank has seen."],
        actions=[action("click", "to view the history")],
    ),
    "bank.list-gui-create-lore": lore(
        "shared accounts",
        ["open an account you can share with other players."],
        actions=[action("click", "to create a bank")],
    ),
    "bank.members-gui-add-lore": lore(
        "bank access",
        ["let another player act on this bank."],
        actions=[action("click", "to pick a player")],
    ),
    "communication.gui.panel.action-hint": lore(
        "server panel",
        ["one of the actions this panel offers."],
        actions=[action("click", "to run it")],
    ),
    "eco.admin-gui.target-history-lore": lore(
        "economy admin",
        ["every transaction that touched this player's balance."],
        actions=[action("click", "to view them")],
    ),
    "eco.exchange.gui-no-rate-lore": lore(
        "currency exchange",
        ["these two currencies have no rate set, so nothing can be converted between them."],
        [row("pair", "{exchange_source_currency} <dim>to</dim> {exchange_target_currency}", "muted")],
    ),
    "invrollback.gui.export": lore(
        "snapshot action",
        ["take the snapshot's items as shulker boxes instead of putting them back."],
        actions=[action("click", "to receive the boxes")],
    ),
    "invrollback.gui.restore": lore(
        "snapshot action",
        ["put this snapshot back on the player, replacing what they carry now."],
        actions=[action("click", "to restore it")],
    ),
    "invrollback.gui.teleport": lore(
        "snapshot action",
        ["go to the spot where this snapshot was taken."],
        actions=[action("click", "to teleport there")],
    ),
    "itemworld.gui.hub.value-lore": lore(
        "item tools",
        ["one of the item tools, ready to run on what you are holding."],
        actions=[action("click", "to use it")],
    ),
    "itemworld.gui.itemedit.rename.hint": lore(
        "item editor",
        ["give the held item a name of your own."],
        actions=[
            action("left-click", "to rename it"),
            action("right-click", "to reset the name"),
        ],
    ),
    "itemworld.gui.itemedit.lore.hint": lore(
        "item editor",
        ["the lines written under the item's name."],
        actions=[
            action("left-click", "to add a line"),
            action("right-click", "to remove the last line"),
            action("shift-click", "to clear them all"),
        ],
    ),
    "itemworld.gui.itemedit.enchant.hint": lore(
        "item editor",
        ["what the item is enchanted with, whatever an anvil would allow."],
        actions=[
            action("left-click", "to add one"),
            action("right-click", "to remove one"),
        ],
    ),
    "itemworld.gui.itemedit.flags.hint": lore(
        "item editor",
        ["which details the item keeps hidden from its tooltip."],
        actions=[action("click", "to toggle them")],
    ),
    "itemworld.gui.itemedit.unbreakable.hint": lore(
        "item editor",
        ["an unbreakable item never loses durability."],
        actions=[action("click", "to toggle it")],
    ),
    "itemworld.gui.itemedit.durability.hint": lore(
        "item editor",
        ["how worn the item is."],
        actions=[
            action("left-click", "to set the damage"),
            action("right-click", "to repair it"),
        ],
    ),
    "itemworld.gui.itemedit.model.hint": lore(
        "item editor",
        ["the custom model a resource pack draws this item with."],
        actions=[
            action("left-click", "to set the model"),
            action("right-click", "to clear it"),
        ],
    ),
    "kit.editor.create-button.lore": lore(
        "kit manager",
        ["make an empty kit, then fill it with items and settings."],
        actions=[action("click", "to create a kit")],
    ),
    "kit.editor.manage-categories.lore": lore(
        "kit manager",
        ["the folders kits are filed under in the kit menu."],
        actions=[action("click", "to manage them")],
    ),
    "kit.editor.category.create-button.lore": lore(
        "kit categories",
        ["add a folder to file kits under."],
        actions=[action("click", "to create a category")],
    ),
    "kit.editor.category.selector.none.lore": lore(
        "kit categories",
        ["leave this kit outside every folder."],
        actions=[action("click", "to remove the category")],
    ),
    "kit.editor.settings.edit-items.lore": lore(
        "kit editor",
        ["the stacks a player receives when they claim this kit."],
        actions=[action("click", "to open the item grid")],
    ),
    "loan.gui-request-lore": lore(
        "credit",
        ["take a loan against your credit score; the interest follows the score."],
        actions=[action("click", "to request a loan")],
    ),
    "pay.confirm-gui-cancel-lore": lore(
        "payment",
        ["walk away: nothing leaves your balance."],
        actions=[action("click", "to cancel")],
    ),
    "pay.confirm-gui-value-lore": lore(
        "payment",
        ["payments above the configured threshold ask before they are sent."],
    ),
    "pwarp.gui.categories.close-lore": lore(
        "player warps",
        ["shut the directory and go back to the game."],
        actions=[action("click", "to close the menu")],
    ),
    "pwarp.gui.icon.reset-lore": lore(
        "warp icon",
        ["drop the custom icon and go back to the default one."],
        actions=[action("click", "to clear the icon")],
    ),
    "ranks.gui-rankup-lore": lore(
        "your progress",
        ["climb to the next rank, if you meet what it asks for."],
        actions=[action("click", "to advance")],
    ),
    "teleport.rtp.gui.biome-hint-lore": lore(
        "random teleport",
        ["a random teleport can be aimed at one kind of terrain."],
        actions=[action("run", "/rtp biome to land in a chosen biome")],
    ),
    "teleport.rtp.gui.world-lore": lore(
        "random teleport",
        ["drop somewhere unexplored in this world."],
        actions=[action("click", "to random-teleport here")],
    ),
    "wallet.gui-balance-lore": lore(
        "your wallet",
        ["money the server holds for you; it survives deaths and rollbacks."],
        [row("balance", "{wallet_balance}", "money")],
    ),
    "wallet.gui-history-lore": lore(
        "your wallet",
        ["what came in and what went out, newest first."],
        actions=[action("click", "to view your transactions")],
    ),
    "warp.manager.create-button.lore": lore(
        "warp manager",
        ["open a warp where you are standing."],
        actions=[action("click", "to create a warp here")],
    ),
    "warp.manager.manage-categories.lore": lore(
        "warp manager",
        ["the folders warps are filed under in the browse menu."],
        actions=[action("click", "to manage them")],
    ),
    "warp.editor.category.create-button.lore": lore(
        "warp categories",
        ["add a folder to file warps under."],
        actions=[action("click", "to create a category")],
    ),
    "warp.editor.category.selector.none.lore": lore(
        "warp categories",
        ["leave this warp outside every folder."],
        actions=[action("click", "to remove the category")],
    ),
    "warp.editor.sound-selector.entry.lore": lore(
        "teleport sounds",
        ["what a player hears when this warp fires."],
        [row("sound", "{sound}")],
        [action("click", "to use this sound")],
    ),
    "warp.editor.sound-selector.custom.lore": lore(
        "teleport sounds",
        ["any sound key the server knows, including one from a resource pack."],
        actions=[action("click", "to type a sound name")],
    ),
    "warp.editor.sound-selector.remove.lore": lore(
        "teleport sounds",
        ["leave this warp silent."],
        actions=[action("click", "to remove the sound")],
    ),
    "warp.editor.welcome-manager.add.lore": lore(
        "welcome messages",
        ["another line for arriving players to read."],
        actions=[action("click", "to write one in chat")],
    ),
    "warp.editor.welcome-manager.clear.lore": lore(
        "welcome messages",
        ["remove every welcome message from this warp."],
        actions=[action("click", "to remove them all")],
    ),
    "world.editor.create.cycle-hint": lore(
        "world editor",
        ["one of the settings the new world is built with."],
        actions=[
            action("left-click", "for the next value"),
            action("right-click", "for the previous one"),
        ],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
