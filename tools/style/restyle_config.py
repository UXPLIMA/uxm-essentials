"""Apply the small-capital rewrite to the player-visible strings in a shipped operator config.

A module config mixes two kinds of string: settings an operator types (a material, a permission
node, a sound key) and text a player reads (a join line, a scoreboard row, an info page). Only the
second kind is rewritten, and it is recognised by the style token it carries: a string holding
<value>, <body>, <h:'…'> or any other palette tag is text somebody sees, and nothing else in these
files is written that way.

Usage: python3 tools/style/restyle_config.py <file>...
"""

import re
import sys

sys.path.insert(0, "tools/style")

from smallcaps import convert, de_dash, strip_decorations

TOKEN = re.compile(
    r"<(?:h|tag|etag|helpop|staffchat):|"
    r"</?(?:accent|value|body|subtext|muted|dim|icon|crumb|good|bad|money|level|cta|info|rank|event)>"
)
QUOTED = re.compile(r'"((?:[^"\\]|\\.)*)"')


def restyle(text):
    def one(match):
        value = match.group(1)
        if not TOKEN.search(value):
            return match.group(0)
        return '"' + convert(de_dash(strip_decorations(value))) + '"'

    return QUOTED.sub(one, text)


def main():
    for path in sys.argv[1:]:
        with open(path, encoding="utf-8") as handle:
            before = handle.read()
        after = restyle(before)
        if after == before:
            print(f"unchanged: {path}")
            continue
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(after)
        print(f"restyled: {path}")


if __name__ == "__main__":
    main()
