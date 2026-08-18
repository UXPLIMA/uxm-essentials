"""Collapse a spec item's multi-key lore into the single block the canon asks for.

A menu item used to draw its lore from two or three catalog keys the spec glued together with blank
lines. One item is one block now, so this finds every such item in a spec, merges its keys into one
(named after their common prefix), rewrites the spec to read that key, drops the fragment keys from
the catalog, and edits the owning key enum to match. The merged key is left with an empty value: the
words are authored afterwards by the module script, which is where the prose belongs.

Usage: python3 tools/style/merge_lore.py <spec.conf> <MessageKey.java>
"""

import glob
import re
import sys

CATALOG = "bukkit-adapter/src/messages/resources/messages/messages_en.conf"

LORE_LIST = re.compile(
    r'lore(\s*)=\s*\[\n(?:[ \t]*(?:"@[a-z0-9.\-]+"|""),?\n)+[ \t]*\]'
    r'|lore(\s*)=\s*\[(?:"@[a-z0-9.\-]+"|"")(?:, ?(?:"@[a-z0-9.\-]+"|""))+\]')
CONSTANT = re.compile(r'^    ([A-Z0-9_]+)\("([^"]+)"\)([,;])$')


def merged_key(keys):
    """The key the fragments collapse into: their shared prefix, ending in `lore`."""
    head = keys[0].split(".")
    for key in keys[1:]:
        parts = key.split(".")
        head = [a for a, b in zip(head, parts) if a == b]
    if not head:
        raise SystemExit(f"no shared prefix in {keys}")
    if head[-1] != "lore":
        head.append("lore")
    return ".".join(head)


def constant_name(key):
    return re.sub(r"[.\-]", "_", key).upper()


def rewrite_spec(path):
    """Rewrite every multi-key lore list in the spec, and report the merges it made."""
    text = open(path, encoding="utf-8").read()
    merges = []

    def replace(match):
        keys = re.findall(r'"@([a-z0-9.\-]+)"', match.group(0))
        if len(keys) < 2:
            return match.group(0)
        target = merged_key(keys)
        merges.append((target, keys))
        spacing = match.group(1) or match.group(2)
        return f'lore{spacing}= ["@{target}"]'

    rewritten = LORE_LIST.sub(replace, text)
    if merges:
        open(path, "w", encoding="utf-8").write(rewritten)
    return merges


def collides(merges):
    """Merged names that would land on a key another item already owns.

    Two menus in different specs can share a short prefix (both bank panels start at `bank.`), and a
    derived name that short would put two different blocks on one key. Those are named by hand.
    """
    catalog = open(CATALOG, encoding="utf-8").read()
    taken = set()
    clashes = set()
    for target, keys in merges:
        if target in taken or (f'"{target}"' in catalog and target != keys[0]):
            clashes.add(target)
        taken.add(target)
    return clashes


def rewrite_catalog(merges):
    """Replace each merge's first fragment line with the merged key, and drop the rest."""
    lines = open(CATALOG, encoding="utf-8").readlines()
    first = {keys[0]: target for target, keys in merges}
    dropped = {key for _, keys in merges for key in keys[1:]}
    out = []
    for line in lines:
        match = re.match(r'^"([^"]+)"(\s*=\s*)".*"\s*$', line)
        key = match.group(1) if match else None
        if key in first:
            out.append(f'"{first[key]}"{match.group(2)}""\n')
        elif key not in dropped:
            out.append(line)
    open(CATALOG, "w", encoding="utf-8").writelines(out)


def rewrite_enum(path, merges):
    """Rename each merge's first constant and delete the rest, keeping the enum's closing semicolon."""
    lines = open(path, encoding="utf-8").read().split("\n")
    first = {keys[0]: target for target, keys in merges}
    dropped = {key for _, keys in merges for key in keys[1:]}
    out = []
    closed = True
    for line in lines:
        match = CONSTANT.match(line)
        if match is None:
            out.append(line)
            continue
        name, key, terminator = match.groups()
        if key in first:
            target = first[key]
            out.append(f'    {constant_name(target)}("{target}"){terminator}')
            closed = terminator == ";"
        elif key in dropped:
            closed = closed or terminator != ";"
        else:
            out.append(line)
            closed = terminator == ";"
    if not closed:
        for i in range(len(out) - 1, -1, -1):
            if CONSTANT.match(out[i]):
                out[i] = out[i][:-1] + ";"
                break
    open(path, "w", encoding="utf-8").write("\n".join(out))


def still_referenced(keys, spec, enum):
    """Keys any other spec or any Java source still reads. Dropping one of those would break a menu.

    The enum being rewritten is skipped: it declares every key by definition, and this rewrite is what
    removes the fragments from it.
    """
    haystack = []
    for path in glob.glob("bukkit-adapter/src/main/resources/modules/*/gui/*.conf"):
        if path != spec:
            haystack.append(open(path, encoding="utf-8").read())
    for root in ("core/src/main/java", "bukkit-adapter/src/main/java", "bukkit-adapter/src/test/java"):
        for path in glob.glob(root + "/**/*.java", recursive=True):
            if path != enum:
                haystack.append(open(path, encoding="utf-8").read())
    blob = "\n".join(haystack)
    return {key for key in keys if f'"{key}"' in blob or constant_name(key) in blob}


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    spec, enum = sys.argv[1], sys.argv[2]
    merges = rewrite_spec(spec)
    if not merges:
        print("no multi-key lore in " + spec)
        return
    clashes = collides(merges)
    if clashes:
        raise SystemExit("derived name is not unique, name it by hand: " + ", ".join(sorted(clashes)))
    held = still_referenced({key for _, keys in merges for key in keys[1:]}, spec, enum)
    if held:
        raise SystemExit("still read elsewhere, merge by hand: " + ", ".join(sorted(held)))
    rewrite_catalog(merges)
    rewrite_enum(enum, merges)
    for target, keys in merges:
        print(f"{target} <- {', '.join(keys)}")


if __name__ == "__main__":
    main()
