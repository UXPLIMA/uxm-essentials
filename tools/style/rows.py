"""Turn the leftover pipe rows into the canon's bullet rows.

Chat read-outs and the tiles the plugin composes line by line still wrote a fact as `| label: value`.
The canon writes it as a dim bullet, a white label with no colon, and the value in its own tone, so
one fact reads the same whether it lands in a lore block or in chat. The tones already on the line are
kept: only the bullet, the colon and the leading space change.
"""

import re
import sys

CATALOG = "bukkit-adapter/src/messages/resources/messages/messages_en.conf"

ROW = re.compile(r'<muted>\|</muted> <body>([^<]*?):</body>')
BARE = re.compile(r'<muted>\|</muted> ')


def convert(value):
    value = ROW.sub(lambda m: f"<icon>•</icon> <body>{m.group(1)}</body>", value)
    return BARE.sub("<icon>•</icon> ", value)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else CATALOG
    lines = open(path, encoding="utf-8").readlines()
    changed = 0
    for i, line in enumerate(lines):
        match = re.match(r'^("[^"]+"\s*=\s*")(.*)("\s*)$', line)
        if match is None:
            continue
        rewritten = convert(match.group(2))
        if rewritten != match.group(2):
            lines[i] = match.group(1) + rewritten + match.group(3)
            changed += 1
    open(path, "w", encoding="utf-8").writelines(lines)
    print(f"{changed} lines converted")


if __name__ == "__main__":
    main()
