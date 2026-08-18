"""Read the default font's glyph advances off the client's own atlas.

`FontWidths` (bukkit-adapter, style package) needs to know how wide a string is drawn so a menu title
can be centred with spaces. The server has no font to ask, so the numbers come from here: this
downloads the client of a given release, reads `assets/minecraft/font/include/default.json`, and
measures every glyph in the bitmap providers the way the client does, trimming the transparent columns
on the right and adding the one-pixel gap it leaves after each glyph.

    python3 tools/style/fontwidths.py            # the latest release
    python3 tools/style/fontwidths.py 1.21.4     # a specific one

It prints the Java table body for the characters whose advance is not the usual six: paste it into
`FontWidths.NARROW`. The measurement of the printable ASCII range doubles as its own check, since
those widths are long known (a six, i two, l three, f five, I four, full stop two).

Needs Pillow. The client jar is downloaded to a temporary directory and deleted again.
"""

import json
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

from PIL import Image

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
COMMON_WIDTH = 6
SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴ"
SMALL_CAPS += "ᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢ"


def client_url(version):
    """The download URL of `version`'s client jar, or of the latest release when version is None."""
    with urllib.request.urlopen(MANIFEST) as response:
        manifest = json.load(response)
    wanted = version or manifest["latest"]["release"]
    entry = next(v for v in manifest["versions"] if v["id"] == wanted)
    with urllib.request.urlopen(entry["url"]) as response:
        return wanted, json.load(response)["downloads"]["client"]["url"]


def advances(assets):
    """Every glyph advance the default font defines, keyed by character."""
    font = json.loads((assets / "minecraft/font/include/default.json").read_text(encoding="utf-8"))
    measured = {" ": 4}
    for provider in font["providers"]:
        if provider["type"] != "bitmap":
            continue
        atlas = Image.open(assets / "minecraft/textures" / provider["file"].split(":")[1]).convert("RGBA")
        rows = provider["chars"]
        cell_w = atlas.width // len(rows[0])
        cell_h = atlas.height // len(rows)
        scale = provider.get("height", 8) / cell_h
        pixels = atlas.load()
        for row, line in enumerate(rows):
            for column, character in enumerate(line):
                if not character.strip():
                    continue
                measured.setdefault(character, glyph_advance(pixels, column * cell_w, row * cell_h, cell_w, cell_h, scale))
    return measured


def glyph_advance(pixels, left, top, cell_w, cell_h, scale):
    """One glyph's advance: its drawn width, scaled to the provider's height, plus the gap after it."""
    for x in range(cell_w - 1, -1, -1):
        if any(pixels[left + x, top + y][3] != 0 for y in range(cell_h)):
            return int(0.5 + (x + 1) * scale) + 1
    return 4


def table(measured):
    """The Java map entries for every character we write that is not the common six pixels wide."""
    written = "".join(chr(code) for code in range(32, 127)) + SMALL_CAPS
    lines = []
    for character in written:
        width = measured.get(character, COMMON_WIDTH)
        if width == COMMON_WIDTH:
            continue
        literal = "\\u%04x" % ord(character) if ord(character) > 126 else character.replace("\\", "\\\\").replace("'", "\\'")
        lines.append("            Map.entry('%s', %d)," % (literal, width))
    return "\n".join(lines)


def main():
    version, url = client_url(sys.argv[1] if len(sys.argv) > 1 else None)
    work = Path(tempfile.mkdtemp(prefix="fontwidths-"))
    try:
        jar = work / "client.jar"
        urllib.request.urlretrieve(url, jar)
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith("assets/minecraft/font/") or name.startswith("assets/minecraft/textures/font/"):
                    archive.extract(name, work)
        print("# measured against the %s client" % version)
        print(table(advances(work / "assets")))
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
