"""Give every pagination and back button the one-line shape the style canon fixes.

A navigation button is an arrow item with a single-line name and no lore: the arrow sits on the left,
the label follows in the brand sky, and nothing is bolded. This rewrites the catalog's nav keys into
that shape, keeping whatever the button already said so a "back to manage" stays specific.
"""

import re
import sys

sys.path.insert(0, "tools/style")

from apply_lore import CATALOG

PREVIOUS = "← ᴘʀᴇᴠɪᴏᴜꜱ ᴘᴀɢᴇ"
NEXT = "→ ɴᴇxᴛ ᴘᴀɢᴇ"
PAGE = "<muted>ᴘᴀɢᴇ</muted> <value>{page}</value><dim>/</dim><value>{max_page}</value>"

KEY = re.compile(r'^(\s*)"([^"]+)"(\s*=\s*)"(.*)"(\s*)$')
NAV = re.compile(r"[.-](prev|previous|next|page-info|back|back-button)(\.name)?$")
LABEL = re.compile(r"^\s*<[a-z]+>(.*?)</[a-z]+>\s*$")


def value_for(key, current):
    match = NAV.search(key)
    tail = match.group(1) if match else ""
    if tail in ("prev", "previous"):
        return f"<accent>{PREVIOUS}</accent>"
    if tail == "next":
        return f"<accent>{NEXT}</accent>"
    if tail == "page-info":
        return PAGE
    label = LABEL.match(current)
    text = label.group(1) if label else current
    text = text.lstrip("←→◀▶ ")
    arrow = "" if text.startswith("ᴄʟᴏꜱᴇ") else "← "
    return f"<accent>{arrow}{text}</accent>"


def main():
    with open(CATALOG, encoding="utf-8") as handle:
        lines = handle.readlines()
    changed = 0
    for i, line in enumerate(lines):
        match = KEY.match(line)
        if match is None:
            continue
        indent, key, equals, current, tail = match.groups()
        if not NAV.search(key):
            continue
        replacement = value_for(key, current)
        if replacement == current:
            continue
        lines[i] = f'{indent}"{key}"{equals}"{replacement}"{tail}'
        changed += 1
    with open(CATALOG, "w", encoding="utf-8") as handle:
        handle.writelines(lines)
    print(f"{changed} navigation labels rewritten")


if __name__ == "__main__":
    main()
