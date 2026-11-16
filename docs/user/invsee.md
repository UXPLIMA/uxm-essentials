# Invsee and endersee

`/invsee <player>` and `/endersee <player>` open a window on a player's inventory or ender chest. The window is a
copy: what staff move in it reaches the player when the window closes. This page explains how that save works, so
an operator can tell what happened when a message or a console line says an edit was not saved.

## Online and offline

When the player is online on this server, the window shows their live inventory and the close applies to it.
When they are not, the window reads their saved data from `players/data/<uuid>.dat` in the world folder, and the
close writes the file. A player who joins while a staff member has their offline window open gets the edit on
their live inventory instead.

The inventory window is editable with `uxmessentials.invsee.modify`; without it the window is view only. The
ender chest window is always editable. Either way the window opens view only when:

- someone else is editing the same container, online or offline. The message names them. Only one person edits
  a container at a time, so two staff cannot take the same items.
- the saved data was written by an older server version, or holds an item this server cannot read. The server
  converts old data when the player next joins; the window is editable after that.

An offline window does not open at all while the player is joining, when they have no saved data on this server,
or when the file cannot be read. The staff member is told which.

## What a close saves

Only the slots the staff member changed are written, and only where the container still holds what the window
opened with. Everything else in the player's data, and every slot the window did not change, stays exactly as it
is, including anything the player or another edit changed in the meantime.

A slot that changed in the meantime is a conflict and is never written over. What happens then:

- The staff member's change to that slot is undone: what they took out of it is taken back from their inventory,
  and what they put into it is returned to them.
- Their other slots are still saved when they hold everything that has to be taken back. When a slot's items were
  moved to another slot of the window, writing only the second slot would copy them, so nothing is saved.
- The message says whether the edit was saved in part or undone.

The same happens, for every changed slot, when nothing can be saved: the player is joining or left the server
while the window was open, or the file could not be written. Nothing is ever kept open waiting for the staff
member to put things back.

While an editable window is open, and until its save comes back, the staff member cannot drop items, by key or by
clicking outside the window. That keeps what may have to be taken back in their inventory.

## When the window closes by itself

When a staff member disconnects with a window open, the edit is saved, and anything not saved taken back, before
the server saves the staff member's own inventory. When the module stops or reloads, or the server shuts down,
every open window is saved and closed the same way.

## Logins

A save of an offline player's file takes a moment. A player who logs in during one waits for it, up to three
seconds, so the login always reads the saved file. If the save takes longer, the login is refused with a message
to join again.

## The file and its backup

A save writes the whole new file next to the old one first, keeps the old one as `<uuid>.dat_old`, and then puts
the new file in place in one step. The file is never missing or half written; a save that fails leaves the old
file as it was. `<uuid>.dat_old` is the file as it was before the last save, by the server or by `/invsee`.

## Console lines

- `event=mirror_edit_not_reclaimed`: an edit was not saved and some of what had to be taken back was no longer in
  the staff member's inventory. The line lists each item with its full data, so it can be restored by hand.
- `event=offline_edit_not_saved`: the staff member left before the outcome of their save came back, so nothing
  could be taken back. `viewer_holds_extra` lists what they still hold that the player was not given, and
  `viewer_is_owed` what they put in that the player did not get.
- `event=player_data_busy`: a login was refused because a save of the player's file did not finish in time.
- `event=offline_inventories_unavailable`: this server version's internals could not be reached, so offline
  windows are off. Online windows still work.
