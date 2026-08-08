/**
 * The vote context's inbound GUI views. {@link VoteSitesMenu} registers and opens the vote-site board:
 * one icon per configured site with its cooldown status, drawn by the menu engine from
 * {@code modules/vote/gui/vote-sites.conf}. A click on a ready site sends the player an Adventure
 * {@code openUrl} component so their client can open the link.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.adapter.inbound.gui;
