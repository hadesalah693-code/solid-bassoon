package com.kalima.backend.hr.device;

/** Raised when a fingerprint device is unreachable or answers unexpectedly. */
public class ZkDeviceException extends RuntimeException {

    public ZkDeviceException(String message) {
        super(message);
    }

    public ZkDeviceException(String message, Throwable cause) {
        super(message, cause);
    }
}
