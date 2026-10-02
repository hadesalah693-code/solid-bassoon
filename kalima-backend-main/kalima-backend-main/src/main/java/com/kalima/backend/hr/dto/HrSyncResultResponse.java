package com.kalima.backend.hr.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outcome of a device sync, shown as a toast right after the button is pressed.
 *
 * @param deviceId     device that was synced
 * @param deviceName   its display name
 * @param fetched      punches present on the device
 * @param inserted     punches newly stored
 * @param skipped      punches already stored from an earlier sync
 * @param unmatched    newly stored punches whose punch ID matches no employee
 * @param maxPunchedAt newest punch seen on the device
 * @param message      human-readable Arabic summary
 */
public record HrSyncResultResponse(
        UUID deviceId,
        String deviceName,
        int fetched,
        int inserted,
        int skipped,
        int unmatched,
        LocalDateTime maxPunchedAt,
        String message
) {
}
