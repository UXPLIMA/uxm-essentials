"""Write generated lore blocks back into the message catalog, one key at a time.

The blocks themselves come from a module script that describes each item through lore(); this only
finds the key's line and replaces its value, so a rewrite never disturbs the ordering, the comments
or the keys around it. A key that is not in the catalog is reported rather than silently added,
because a typo in a key name would otherwise ship as a missing message.
"""

import re
import sys

CATALOG = "bukkit-adapter/src/messages/resources/messages/messages_en.conf"


def apply(blocks, path=CATALOG):
    with open(path, encoding="utf-8") as handle:
        lines = handle.readlines()
    seen = set()
    for i, line in enumerate(lines):
        match = re.match(r'^(\s*)"([^"]+)"(\s*=\s*)"(.*)"(\s*)$', line)
        if match is None:
            continue
        indent, key, equals, _, tail = match.groups()
        if key not in blocks:
            continue
        seen.add(key)
        lines[i] = f'{indent}"{key}"{equals}"{blocks[key]}"{tail}'
    missing = sorted(set(blocks) - seen)
    if missing:
        print("no such key: " + ", ".join(missing), file=sys.stderr)
    with open(path, "w", encoding="utf-8") as handle:
        handle.writelines(lines)
    print(f"{len(seen)} keys rewritten")
    return not missing
