# A pose left its poser invisible for good

Reported 2026-10-25 by a server owner: a player who ran `/spin` while vanished and in spectator mode
stayed invisible after they went back to survival. Another spectator could still see them.

## Why

`/lay`, `/bellyflop` and `/spin` hide the real player behind a copy with `setInvisible(true)`, and
put back what they read at the start with `setInvisible(wasInvisible)`.

`setInvisible` is not the flag the server keeps for spectator mode or a potion. Paper writes it as
`persistentInvisibility`. While that is set, the server ignores every later call that would make the
player seen. It is also saved with the player as `Bukkit.invisible`, so it survives a relog and a
restart.

So three paths left a player unseen for good:

- **Spectator mode or an invisibility potion at the start.** The server held the flag for its own
  reason, the pose read `wasInvisible = true`, and at the end it wrote `true` back as persistent.
  Leaving spectator mode or the potion running out no longer changed anything.
- **A reload or a restart in the middle of a pose.** The module stop dropped the copies without
  giving anyone their body back. A restart disables the plugins before it saves the players, so the
  poser was saved unseen.
- **A crash in the middle of a pose.** The same, with no stop at all.

## The fix

The pose takes the flag only from a player who is visible. A spectator, a player under a potion, or
a player another plugin made invisible keeps what they have, and the pose never writes it. When the
pose takes the flag, it saves a `pose_invisible` mark on the player. It gives the flag back when the
pose ends, when the module stops, and on the next join of a player who still carries the mark after
a crash. `PosesAdapterTest` holds one test for each path.

## What it does not undo

A player made invisible by an older build carries the flag and no mark, so the fix cannot tell them
from a player another plugin made invisible on purpose. Until something clears it, such a player
stays unseen. With the player offline, removing `Bukkit.invisible` from
`<world>/playerdata/<uuid>.dat` with an NBT editor frees them without touching anything else they
own.
