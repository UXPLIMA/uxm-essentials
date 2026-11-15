package com.uxplima.uxmessentials.persistence.staff;

import java.util.List;
import java.util.UUID;

/**
 * What one adoption of unscoped staff loadouts did.
 *
 * @param moved the number of rows moved onto this backend's server id
 * @param leftBehind the players whose unscoped row stayed, because they already hold a row on this backend
 */
public record StaffLoadoutAdoption(int moved, List<UUID> leftBehind) {

    public StaffLoadoutAdoption {
        leftBehind = List.copyOf(leftBehind);
    }
}
