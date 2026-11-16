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
- the staff member's previous edit is still being saved. Opening it again a moment later makes it editable.

An offline window does not open at all while the player is joining, when they have no saved data on this server,
or when the file cannot be read. The staff member is told which.

## What a close saves

Only the slots the staff member changed are written, and only where the container still holds what the window
opened with. Everything else in the player's data, and every slot the window did not change, stays exactly as it
is, including anything the player or another edit changed in the meantime.

A slot that changed in the meantime is a conflict and is never written over. On an online player this is common
for tools and armour they are using: a pickaxe that lost durability while the window was open is a changed slot.
What happens then:

- The staff member's change to that slot is undone: what they took out of it is taken back from their inventory,
  and what they put into it is returned to them.
- Their other slots are still saved when they hold everything that has to be taken back. When a slot's items were
  moved to another slot of the window, writing only the second slot would copy them, so nothing is saved.
- The message says whether the edit was saved in part or undone.

The same happens, for every changed slot, when nothing can be saved: the player is joining or left the server
while the window was open, or the file could not be written. Nothing is ever kept open waiting for the staff
member to put things back.

While an editable window is open, and until its save comes back, the staff member's items stay where they are:
they cannot drop items, move items into or out of a bundle, open another inventory, place, use or eat items, use
an armour stand or their crafting grid, and if they die they keep their inventory. That keeps what may have to be taken back in their inventory. The one exception is a
drop of an item that would not fit back into a full inventory, such as what the window hands back on close: it is
let through so the item is not lost, and logged.

If another plugin stops the window from opening, nothing is edited and the container is free for the next editor.

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
- `event=mirror_drop_let_through`: a staff member with an edit open dropped an item that had no room to go back
  into their inventory. The line has the item's full data.
- `event=mirror_edit_save_failed`: a save threw an error. Any slot it already wrote on an online player is put
  back, and the edit is undone like any other failed save; the line has the stack trace.
- `event=mirror_edit_rollback_failed`: putting back a slot after such an error failed too. The line names the slot,
  which keeps what the edit wrote; the edit's other slots are undone.
- `event=player_data_busy`: a login was refused because a save of the player's file did not finish in time.
- `event=offline_inventories_unavailable`: this server version's internals could not be reached, so offline
  windows are off. Online windows still work.
