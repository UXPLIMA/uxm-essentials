"""Strip the colour out of the menu-title keys in the message catalog.

The style canon writes a window title centred and bare: no colour, no bold, no gradient. The renderer
enforces that on its way to the client, and this keeps the catalog saying the same thing, so a
translator copying an existing title does not carry a colour tag into the new one. Only keys that
name a window are touched: the ones a shipped menu spec opens with, plus the editor and list titles,
which are recognised by their key. A <h:'Text'> header collapses to its own small-capital text.
"""

import glob
import pathlib
import re
import sys

sys.path.insert(0, "tools/style")

from lore import sc

CATALOG = "bukkit-adapter/src/messages/resources/messages/messages_en.conf"
SPECS = "bukkit-adapter/src/main/resources/modules/*/gui/*.conf"
GUI_PATHS = (".gui.", "-gui", ".menu.", ".editor.", "gui-")
TITLE_ENDINGS = ("title",)


def spec_titles():
    """Every catalog key a shipped spec opens its window with."""
    keys = set()
    for path in glob.glob(SPECS):
        for line in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
            match = re.match(r'\s*title\s*=\s*"@([a-z0-9._-]+)"\s*$', line)
            if match:
                keys.add(match.group(1))
    return keys


def names_a_window(key):
    return key.split(".")[-1].endswith(TITLE_ENDINGS) and any(part in key for part in GUI_PATHS)


def bare(value):
    """The value with every style tag dropped, a header collapsed to its own small capitals."""
    value = re.sub(r"<h:'([^']*)'>", lambda m: sc(m.group(1).lower()), value)
    value = re.sub(r"</?[a-z][a-z0-9_-]*(:[^>]*)?>", "", value)
    return re.sub(r"\s+", " ", value).strip()


def main():
    path = pathlib.Path(CATALOG)
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    wanted = spec_titles()
    changed = 0
    for i, line in enumerate(lines):
        match = re.match(r'^(\s*)"([^"]+)"(\s*=\s*)"(.*)"(\s*)$', line)
        if match is None:
            continue
        indent, key, equals, value, tail = match.groups()
        if key not in wanted and not names_a_window(key):
            continue
        stripped = bare(value)
        if stripped == value:
            continue
        lines[i] = f'{indent}"{key}"{equals}"{stripped}"{tail}'
        changed += 1
    path.write_text("".join(lines), encoding="utf-8")
    print(f"{changed} titles stripped")


if __name__ == "__main__":
    main()
