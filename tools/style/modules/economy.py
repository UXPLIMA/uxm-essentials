"""The economy menu lore: the wallet, the currency exchange, and the admin panel."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, row


def admin(title, what, verb, currency=True):
    """One button of the economy admin panel: what it does to a balance, against the active currency."""
    return lore(
        "economy admin",
        [what],
        [row("currency", "{eco_currency}")] if currency else (),
        [action("click", verb)],
    )


BLOCKS = {
    "wallet.gui-banknotes-lore": lore(
        "cash in hand",
        ["money you are carrying as items: it is not in your balance until you deposit it."],
        [row("total", "{wallet_banknotes}", "money")],
        [action("right-click", "a banknote to deposit it")],
    ),
    "eco.exchange.gui-source-lore": lore(
        "currency exchange",
        ["the currency the exchange takes from you."],
        [row("balance", "{exchange_source_balance}", "money")],
        [action("click", "to change the source")],
    ),
    "eco.exchange.gui-target-lore": lore(
        "currency exchange",
        ["the currency the exchange pays you in."],
        [row("balance", "{exchange_target_balance}", "money")],
        [action("click", "to change the target")],
    ),
    "eco.exchange.gui-info-lore": lore(
        "currency exchange",
        ["what one unit buys, and what the exchange keeps for itself."],
        [
            row("pair", "{exchange_source_currency} <dim>to</dim> {exchange_target_currency}"),
            row("rate", "1.00 <dim>to</dim> {exchange_rate}"),
            row("fee", "{exchange_fee}%", "muted"),
        ],
        [action("click", "to convert a custom amount")],
    ),
    "eco.admin-gui.manage-lore": lore(
        "economy admin",
        ["give, take, set or reset a single player's balance."],
        actions=[action("click", "to pick a player")],
    ),
    "eco.admin-gui.bulk-lore": lore(
        "economy admin",
        ["give to every online player at once, or reset all of them."],
        actions=[action("click", "to open the bulk tools")],
    ),
    "eco.admin-gui.history-lore": lore(
        "economy admin",
        ["every transaction the economy recorded, newest first."],
        actions=[action("click", "to view recent transactions")],
    ),
    "eco.admin-gui.target-head-lore": lore(
        "the player you picked",
        ["what this player holds in each currency right now."],
        [row("{eco_currency}", "{eco_amount}", "money")],
    ),
    "eco.admin-gui.give-lore": admin("Give", "credit this player, leaving the rest of the balance alone.",
                                     "to enter an amount"),
    "eco.admin-gui.take-lore": admin("Take", "debit this player, down to zero at the lowest.",
                                     "to enter an amount"),
    "eco.admin-gui.set-lore": admin("Set", "write an exact balance, whatever it was before.",
                                    "to enter an amount"),
    "eco.admin-gui.reset-lore": admin("Reset", "put this balance back to the starting amount.",
                                      "to reset, then confirm"),
    "eco.admin-gui.giveall-lore": admin("Give All", "credit every player who is online right now.",
                                        "to enter an amount"),
    "eco.admin-gui.resetall-lore": admin("Reset All", "put every online player's balance back to the start.",
                                         "to reset all, then confirm"),
    "eco.admin-gui.select-currency-lore": lore(
        "economy admin",
        ["the currency every button on this panel works against."],
        [row("active", "{eco_currency}")],
        [action("click", "to choose a currency")],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)
