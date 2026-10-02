package com.kalima.backend.hr.device;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * A single punch read out of a fingerprint / face terminal.
 *
 * @param deviceUserId punch ID as stored on the device (the only identity it gives us)
 * @param punchedAt    punch timestamp decoded from the device's packed time field
 * @param stateCode    raw in/out state byte (0=in, 1=out, 2=break-out, ...)
 * @param verifyCode   raw verification method byte (0=password, 1=fingerprint, ...)
 * @param workCode     raw work code, usually 0
 */
public record ZkAttendanceLog(
        String deviceUserId,
        LocalDateTime punchedAt,
        int stateCode,
        int verifyCode,
        int workCode,
        String raw
) {

    /** Hex of the 40-byte record, kept on the row for troubleshooting. */
    public static String toHex(byte[] record) {
        StringBuilder sb = new StringBuilder(record.length * 2);
        for (byte b : record) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Fixed-width ASCII field, NUL-trimmed — the device pads every string field. */
    public static String readAscii(byte[] buffer, int offset, int length) {
        if (offset < 0 || offset + length > buffer.length) {
            return "";
        }
        String raw = new String(buffer, offset, length, StandardCharsets.US_ASCII);
        int nul = raw.indexOf('\0');
        return (nul >= 0 ? raw.substring(0, nul) : raw).trim();
    }
}
