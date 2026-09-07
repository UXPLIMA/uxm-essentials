# The tile title changes colour when the menu engine moves to uxmLib

2026-09-07. Written while moving uxmEssentials onto `uxmlib-menu`. Player visible, so it is
written down rather than folded in.

## What a player sees

Every menu tile with a title draws that title on the first lore line, opened by a diamond, under
a blank display name. That much is unchanged: same line, same place, same diamond, same bold.

What changes is the colour the title is painted in.

- **Before.** `Tiles.head` asked `Gradients.title(name)`, which chose a ramp from the colour the
  title already carried: the danger ramp for a title in the `bad` colour, the money ramp for
  `good` or `emerald`, the attention ramp for `gold`, and the brand sky ramp for everything else.
  A red title read red, a money title read green, and neither the menu file nor the theme said so.
- **After.** uxmLib's `Tiles.head` paints across a gradient the caller names, and the engine names
  one, `header`, for every tile. The colours come from the `Theme` the host hands over.

So a title that used to pick its own ramp out of its own colour now reads in the one header
gradient, unless the server writes a `header` gradient into `theme.conf`. A red "Delete" title and
a green "Balance" title used to be two colours and are now one.

## Why it is not simply a defect

The old behaviour read a global palette from inside a static helper. Nothing in the call site said
a colour had been decided, and nothing could decide it differently: `ItemRenderer` passed a name
and a lore and got a painted line back. That is the shape a public library may not ship, and it is
the reason the engine was worth moving at all. uxmLib asks for the `Theme` because only the caller
knows which tiles should look alike.

## What this plugin does about it

`ThemeFile.theme(dataFolder)` reads the same `theme.conf` the palette already comes from and hands
the engine a `Theme` carrying **this plugin's shipped role colours** and **the diamond**, rather
than `Theme.defaults()`, which answers in vanilla colours and names no glyph at all. A server that
wrote no file keeps the colours and the furniture it has always seen. `EngineTheme` holds the
answer and re-reads it on the theme reload step, so a live `/uxmess reload` still reaches the
menus, which a theme captured at construction would not.

That closes the glyph and the role colours. It does not restore the per-title ramp, because the
library's seam cannot express it: `Tiles.head(theme, title, gradient)` takes one gradient name for
the whole render and cannot vary it by the colour of the title it is given.

## The open question for the owner

Three ways out, none of them taken here:

1. Accept one header gradient for every tile, and let a server that wants variety write `gradients`
   into `theme.conf`.
2. Ask uxmLib for an overload that takes the stops rather than the gradient name, so a consumer can
   still choose per tile. That is a public API addition and goes through the contribution policy.
3. Keep a per-tile decision on this side by painting the title before it reaches the engine.

This note exists so the choice is made by somebody who owns the look, rather than by whoever was
holding the compiler.
