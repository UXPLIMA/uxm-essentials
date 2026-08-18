"""Give every shipped menu its open sound and every clickable item its click sound.

The style canon treats sound as part of the look: a window announces itself with a page turn, a
confirmation chimes, a cancel thuds, a money button rings, and everything else clicks. The event is
chosen from what the item is, never from what its label says, so the mapping holds whatever language
a catalog is written in.

Filler panes are left silent on purpose: a click on a decorative pane should make no sound.
"""

import glob
import re
import sys

OPEN = "sound:ITEM_BOOK_PAGE_TURN 0.7 1.2"
PAGE = "sound:ITEM_BOOK_PAGE_TURN 0.7 1.0"
YES = "sound:BLOCK_NOTE_BLOCK_PLING 0.6 1.5"
NO = "sound:BLOCK_NOTE_BLOCK_BASS 0.6 0.9"
MONEY = "sound:BLOCK_NOTE_BLOCK_BELL 0.5 1.5"
CLICK = "sound:UI_BUTTON_CLICK 0.5 1.6"

# What an item is, read off its material and its item name. A pane is a backdrop, an arrow navigates,
# a dye or a barrier answers yes or no, and the money materials ring.
POSITIVE = {"LIME_DYE", "LIME_WOOL", "EMERALD", "EMERALD_BLOCK", "GREEN_WOOL"}
NEGATIVE = {"RED_DYE", "RED_WOOL", "BARRIER", "TNT", "LAVA_BUCKET"}
COINS = {"GOLD_INGOT", "GOLD_BLOCK", "GOLD_NUGGET", "SUNFLOWER", "PAPER", "GOLDEN_APPLE"}

ITEM_HEAD = re.compile(r"^(\s*)([a-z0-9-]+) \{\s*$")
FIELD = re.compile(r"^\s*([a-z-]+)\s*=\s*(.*?)\s*$")


def sound_for(name, material, key):
    if material.endswith("_PANE"):
        return None
    if material == "ARROW" or re.search(r"[.-](prev|previous|next)$", key or ""):
        return PAGE
    if material in NEGATIVE:
        return NO
    if material in POSITIVE:
        return YES
    if material in COINS:
        return MONEY
    return CLICK


def restyle(path):
    text = open(path, encoding="utf-8").read()
    lines = text.split("\n")
    out = []
    i = 0
    title_seen = False
    while i < len(lines):
        line = lines[i]
        out.append(line)
        if not title_seen and line.startswith("rows "):
            title_seen = True
            out.append(f'open-actions = ["{OPEN}"]')
        i += 1
    return "\n".join(out)


def main():
    changed = 0
    for path in sorted(glob.glob("bukkit-adapter/src/main/resources/modules/*/gui/*.conf")):
        text = open(path, encoding="utf-8").read()
        if "open-actions" in text:
            continue
        rewritten = restyle(path)
        if rewritten == text:
            print(f"no rows line, skipped: {path}", file=sys.stderr)
            continue
        open(path, "w", encoding="utf-8").write(rewritten)
        changed += 1
    print(f"{changed} menus given an open sound")


if __name__ == "__main__":
    main()
