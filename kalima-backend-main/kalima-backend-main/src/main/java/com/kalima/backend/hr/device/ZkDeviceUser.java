package com.kalima.backend.hr.device;

import java.time.LocalDateTime;

/**
 * A user (punch ID) enrolled on the device. Used to reconcile the office's
 * employee list against what the terminal actually knows about.
 */
public record ZkDeviceUser(
        int uid,
        String deviceUserId,
        String name,
        int role,
        long cardNumber
) {
}
