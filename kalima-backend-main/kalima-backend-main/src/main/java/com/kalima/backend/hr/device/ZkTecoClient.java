package com.kalima.backend.hr.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal client for the ZKTeco standalone SDK over TCP/IP (default port 4370).
 *
 * <p>Implements only what the HR module needs — connect, read the punch log,
 * read the enrolled user list, and close — rather than the full command set.
 *
 * <h2>Wire format</h2>
 * Every TCP frame is a 16-byte prefix followed by a payload:
 * <pre>
 * prefix  : 50 50 82 7d | len (uint16 LE) | 00 00
 * payload : cmd (uint16 LE) | checksum (uint16 LE) | sessionId | replyId | data...
 * </pre>
 * The {@code len} field counts the payload only. {@code checksum} is
 * {@code 0xFFFF - (sum of every uint16 in the payload, mod 0xFFFF) - 1}.
 *
 * <h2>Bulk reads</h2>
 * Attendance and user tables are read in three steps: send the request
 * ({@link #CMD_DATA_WRRQ}), receive {@link #CMD_PREPARE_DATA} carrying the total
 * byte size, then pull the payload in {@link #MAX_CHUNK}-byte slices via
 * {@link #CMD_DATA_RDY}. One slice in flight at a time — issuing them all up
 * front overflows the device's send buffer on slow links and silently truncates
 * the download.
 *
 * <p>All timestamps are decoded as device-local time with the documented ZK
 * quirk: the day is {@code day % 31 + 1} and the month field is 0-based, so the
 * naive decoding is off by one in both.
 */
@Component
public class ZkTecoClient {

    private static final Logger log = LoggerFactory.getLogger(ZkTecoClient.class);

    // Commands used by this client. Full table lives in the ZK PUSH SDK doc.
    private static final int CMD_CONNECT = 1000;
    private static final int CMD_EXIT = 1001;
    private static final int CMD_ENABLE_DEVICE = 1002;
    private static final int CMD_DISABLE_DEVICE = 1003;
    private static final int CMD_PREPARE_DATA = 1500;
    private static final int CMD_DATA = 1501;
    private static final int CMD_FREE_DATA = 1502;
    private static final int CMD_DATA_WRRQ = 1503;
    private static final int CMD_DATA_RDY = 1504;
    private static final int CMD_GET_FREE_SIZES = 50;
    private static final int CMD_OPTIONS_RRQ = 11;
    private static final int CMD_GET_TIME = 201;

    private static final int USHRT_MAX = 65535;
    private static final int HEADER_SIZE = 8;
    private static final int PREFIX_SIZE = 8;
    private static final int MAX_CHUNK = 65472;
    private static final int MAX_PACKET_SIZE = 1024;
    private static final int MAX_BULK_PACKET_SIZE = 65480;
    private static final int USERS_REQUEST_FLAG = 0x05;

    private static final byte[] DISABLE_DEVICE_PAYLOAD = {0, 0, 0, 0};
    private static final byte[] GET_ATTENDANCE_PAYLOAD = {0x01, 0x0D, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] GET_USERS_PAYLOAD = {0x01, 0x09, 0, USERS_REQUEST_FLAG, 0, 0, 0, 0, 0, 0, 0};

    private static final int RECORD_SIZE = 40;
    private static final int USER_SIZE = 72;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public ZkTecoClient(
            @Value("${hr.device.connect-timeout-ms:8000}") int connectTimeoutMs,
            @Value("${hr.device.read-timeout-ms:20000}") int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Opens a session, runs {@code action}, then always closes the session and
     * the socket — a leaked CMD_CONNECT session locks the device out for other
     * clients until it times out.
     */
    public <T> T withSession(String host, int port, SessionAction<T> action) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            socket.setTcpNoDelay(true);

            ZkSession session = new ZkSession(socket);
            session.connect();
            try {
                return action.run(session);
            } finally {
                session.closeQuietly();
            }
        } catch (IOException e) {
            throw new ZkDeviceException("تعذر الاتصال بجهاز البصمة " + host + ":" + port, e);
        }
    }

    /** Pings a device and returns its current clock — used by the "test" button. */
    public ZkDeviceInfo probe(String host, int port) {
        return withSession(host, port, session -> {
            byte[] freeSizes = execute(session, CMD_GET_FREE_SIZES, new byte[0]);
            String serialNumber = readOption(session, "~SerialNumber");
            String firmware = readOption(session, "~ZKFPVersion");
            int freeSpace = freeSizes.length >= 44 ? readInt(freeSizes, 40) : 0;
            session.deviceTime = readDeviceTime(session);
            return new ZkDeviceInfo(serialNumber, firmware, session.deviceTime, freeSpace);
        });
    }

    /**
     * Downloads every punch the device holds. Devices do not support filtering
     * by date, so callers discard anything at or before their high-water mark.
     */
    public List<ZkAttendanceLog> fetchAttendance(String host, int port) {
        return withSession(host, port, session -> decodeAttendance(readBulk(session, GET_ATTENDANCE_PAYLOAD)));
    }

    /** Downloads the enrolled user list (punch IDs) from the device. */
    public List<ZkDeviceUser> fetchUsers(String host, int port) {
        return withSession(host, port, session -> decodeUsers(readBulk(session, GET_USERS_PAYLOAD)));
    }

    // ------------------------------------------------------------------ session

    /** Business action run against an open device session. */
    @FunctionalInterface
    public interface SessionAction<T> {
        T run(ZkSession session) throws IOException;
    }

    /** A connected device session. Package-private: callers go through the facade above. */
    public static final class ZkSession {

        private final Socket socket;
        private final DataInputStream in;
        private final OutputStream out;
        private int sessionId;
        private int replyId;
        private LocalDateTime deviceTime;

        ZkSession(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = socket.getOutputStream();
        }

        /** Last clock value read from the device, for the connection-test screen. */
        public LocalDateTime deviceTime() {
            return deviceTime;
        }

        void connect() throws IOException {
            byte[] reply = execute(this, CMD_CONNECT, new byte[0]);
            if (reply.length < HEADER_SIZE) {
                throw new ZkDeviceException("الجهاز لم يرد على طلب الاتصال");
            }
            // The session id is negotiated by the device and must be echoed on
            // every later command in this connection.
            this.sessionId = readUint16(reply, 4);
        }

        void closeQuietly() {
            try {
                if (socket.isClosed() || !socket.isConnected()) {
                    return;
                }
                execute(this, CMD_EXIT, new byte[0]);
            } catch (IOException | ZkDeviceException e) {
                log.debug("CMD_EXIT failed during cleanup for device session {}: {}", sessionId, e.toString());
            }
        }
    }

    /** Result of a device connection test. */
    public record ZkDeviceInfo(
            String serialNumber,
            String firmware,
            LocalDateTime deviceTime,
            int freeSizes
    ) {
    }

    // ------------------------------------------------------------- command flow

    private static byte[] execute(ZkSession session, int command, byte[] data) throws IOException {
        // CMD_CONNECT must start from a clean slate; every other command bumps
        // the reply id so the device can correlate the reply.
        if (command == CMD_CONNECT) {
            session.sessionId = 0;
            session.replyId = 0;
        } else {
            session.replyId = (session.replyId + 1) % USHRT_MAX;
        }

        byte[] frame = buildFrame(command, session.sessionId, session.replyId, data);
        session.out.write(frame);
        session.out.flush();

        int maxPacket = command == CMD_GET_FREE_SIZES || command == CMD_OPTIONS_RRQ
                ? MAX_PACKET_SIZE
                : MAX_BULK_PACKET_SIZE;
        return readFrame(session, maxPacket);
    }

    /**
     * Reads one 16-byte-prefixed frame, buffering any payload that spilled into
     * the following TCP segment.
     */
    private static byte[] readFrame(ZkSession session, int maxPacketSize) throws IOException {
        byte[] prefix = readFully(session.in, PREFIX_SIZE);
        int payloadSize = readUint16(prefix, 4);
        if (payloadSize <= 0) {
            return new byte[0];
        }
        if (payloadSize > maxPacketSize) {
            throw new ZkDeviceException("حجم رد الجهاز غير متوقع: " + payloadSize + " بايت");
        }
        // readFully consumes exactly the frame, so anything still buffered in
        // the stream belongs to the next frame and must be left alone.
        return readFully(session.in, payloadSize);
    }

    /**
     * Requests a table, waits for the device to stage it, then pulls the bytes
     * in {@link #MAX_CHUNK} slices, and releases the device buffer afterwards.
     */
    private static byte[] readBulk(ZkSession session, byte[] request) throws IOException {
        try {
            return requestTable(session, request);
        } finally {
            freeData(session);
        }
    }

    private static byte[] requestTable(ZkSession session, byte[] request) throws IOException {
        session.replyId = (session.replyId + 1) % USHRT_MAX;
        byte[] frame = buildFrame(CMD_DATA_WRRQ, session.sessionId, session.replyId, request);
        session.out.write(frame);
        session.out.flush();

        byte[] reply = readFrame(session, MAX_BULK_PACKET_SIZE);
        int commandId = readUint16(reply, 0);

        if (commandId == CMD_DATA) {
            // Small table: it came back in full inside the reply frame.
            return stripHeader(reply);
        }
        if (commandId != CMD_PREPARE_DATA && commandId != 2000) {
            throw new ZkDeviceException("رفض الجهاز طلب البيانات (الرمز " + commandId + ")");
        }
        if (reply.length < HEADER_SIZE + 4) {
            throw new ZkDeviceException("رد تحضيري ناقص من الجهاز");
        }

        int totalSize = readInt(reply, HEADER_SIZE);
        if (totalSize <= 0) {
            return new byte[0];
        }
        return readChunks(session, totalSize);
    }

    private static byte[] readChunks(ZkSession session, int totalSize) throws IOException {
        java.io.ByteArrayOutputStream collected = new java.io.ByteArrayOutputStream(Math.min(totalSize, 1 << 20));
        int offset = 0;

        while (offset < totalSize) {
            int size = Math.min(MAX_CHUNK, totalSize - offset);
            byte[] request = ByteBuffer.allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(offset)
                    .putInt(size)
                    .array();

            session.replyId = (session.replyId + 1) % USHRT_MAX;
            session.out.write(buildFrame(CMD_DATA_RDY, session.sessionId, session.replyId, request));
            session.out.flush();

            byte[] packet = readFrame(session, MAX_BULK_PACKET_SIZE);
            if (readUint16(packet, 0) != CMD_DATA) {
                throw new ZkDeviceException("رد غير متوقع أثناء سحب البيانات (الرمز " + readUint16(packet, 0) + ")");
            }
            byte[] chunk = stripHeader(packet);
            collected.write(chunk, 0, chunk.length);
            offset += chunk.length;

            // A short chunk means the device has nothing more to give.
            if (chunk.length < size) {
                break;
            }
        }
        return collected.toByteArray();
    }

    private static byte[] stripHeader(byte[] payload) {
        if (payload.length <= HEADER_SIZE) {
            return new byte[0];
        }
        byte[] body = new byte[payload.length - HEADER_SIZE];
        System.arraycopy(payload, HEADER_SIZE, body, 0, body.length);
        return body;
    }

    private static void freeData(ZkSession session) throws IOException {
        try {
            execute(session, CMD_FREE_DATA, new byte[0]);
        } catch (ZkDeviceException e) {
            // A device with nothing staged answers with an error; harmless here.
            log.debug("CMD_FREE_DATA ignored for device {}: {}", session.sessionId, e.getMessage());
        }
    }

    /** Reads a {@code ~Keyword=Value} option string (serial number, firmware, ...). */
    private static String readOption(ZkSession session, String keyword) {
        try {
            byte[] reply = execute(session, CMD_OPTIONS_RRQ, keyword.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            if (reply.length <= HEADER_SIZE) {
                return null;
            }
            String value = ZkAttendanceLog.readAscii(reply, HEADER_SIZE, reply.length - HEADER_SIZE);
            int eq = value.indexOf('=');
            String resolved = eq >= 0 ? value.substring(eq + 1) : value;
            return resolved.isBlank() ? null : resolved;
        } catch (IOException | ZkDeviceException e) {
            log.debug("Could not read option {} from device {}: {}", keyword, session.sessionId, e.toString());
            return null;
        }
    }

    /**
     * Reads the device clock. A wrong clock silently corrupts every punch time,
     * so the devices screen shows it so staff can spot a device that drifted.
     */
    private static LocalDateTime readDeviceTime(ZkSession session) {
        try {
            byte[] reply = execute(session, CMD_GET_TIME, new byte[0]);
            if (reply.length < HEADER_SIZE + 4) {
                return null;
            }
            return decodePackedTime(readInt(reply, HEADER_SIZE));
        } catch (IOException | ZkDeviceException e) {
            log.debug("Could not read the clock of device {}: {}", session.sessionId, e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ decoding

    private static List<ZkAttendanceLog> decodeAttendance(byte[] data) {
        // The first 4 bytes of the table repeat the record count.
        byte[] body = skipHeaderCount(data);
        List<ZkAttendanceLog> logs = new ArrayList<>();
        int offset = 0;
        while (body.length - offset >= RECORD_SIZE) {
            byte[] record = new byte[RECORD_SIZE];
            System.arraycopy(body, offset, record, 0, RECORD_SIZE);
            offset += RECORD_SIZE;

            String deviceUserId = ZkAttendanceLog.readAscii(record, 2, 9);
            if (deviceUserId.isEmpty()) {
                continue;
            }
            int packedTime = readInt(record, 27);
            logs.add(new ZkAttendanceLog(
                    deviceUserId,
                    decodePackedTime(packedTime),
                    record[31] & 0xFF,
                    record[26] & 0xFF,
                    readUint16(record, 32),
                    ZkAttendanceLog.toHex(record)));
        }
        return logs;
    }

    private static List<ZkDeviceUser> decodeUsers(byte[] data) {
        byte[] body = skipHeaderCount(data);
        List<ZkDeviceUser> users = new ArrayList<>();
        int offset = 0;
        while (body.length - offset >= USER_SIZE) {
            byte[] record = new byte[USER_SIZE];
            System.arraycopy(body, offset, record, 0, USER_SIZE);
            offset += USER_SIZE;

            String deviceUserId = ZkAttendanceLog.readAscii(record, 48, 9);
            if (deviceUserId.isEmpty()) {
                continue;
            }
            users.add(new ZkDeviceUser(
                    readUint16(record, 0),
                    deviceUserId,
                    ZkAttendanceLog.readAscii(record, 11, 24),
                    record[2] & 0xFF,
                    readInt(record, 35)));
        }
        return users;
    }

    private static byte[] skipHeaderCount(byte[] data) {
        if (data.length <= 4) {
            return new byte[0];
        }
        byte[] body = new byte[data.length - 4];
        System.arraycopy(data, 4, body, 0, body.length);
        return body;
    }

    /**
     * Decodes the packed 32-bit timestamp.
     *
     * <p>The ZK layout is {@code (year*12*31 + month*31 + day-1) * 86400 +
     * h*3600 + m*60 + s} with a 2000 epoch. Month is 0-based and the day term
     * wraps at 31, so a direct split reads one day late and one month early.
     */
    static LocalDateTime decodePackedTime(int packed) {
        if (packed <= 0) {
            return null;
        }
        int second = packed % 60;
        int rest = (packed - second) / 60;
        int minute = rest % 60;
        rest = (rest - minute) / 60;
        int hour = rest % 24;
        rest = (rest - hour) / 24;
        int day = rest % 31 + 1;
        rest = (rest - (day - 1)) / 31;
        int month = rest % 12;
        int year = rest / 12 + 2000;

        try {
            return LocalDateTime.of(year, month + 1, day, hour, minute, second);
        } catch (RuntimeException e) {
            log.debug("Discarding out-of-range device timestamp {}", packed);
            return null;
        }
    }

    // ------------------------------------------------------------- header codec

    private static byte[] buildFrame(int command, int sessionId, int replyId, byte[] data) {
        byte[] payload = new byte[HEADER_SIZE + data.length];
        writeUint16(payload, 0, command);
        writeUint16(payload, 2, 0); // checksum placeholder
        writeUint16(payload, 4, sessionId);
        writeUint16(payload, 6, replyId);
        System.arraycopy(data, 0, payload, HEADER_SIZE, data.length);

        writeUint16(payload, 2, computeChecksum(payload));

        byte[] frame = new byte[PREFIX_SIZE + payload.length];
        frame[0] = 0x50;
        frame[1] = 0x50;
        frame[2] = (byte) 0x82;
        frame[3] = 0x7d;
        writeUint16(frame, 4, payload.length);
        System.arraycopy(payload, 0, frame, PREFIX_SIZE, payload.length);
        return frame;
    }

    private static int computeChecksum(byte[] buffer) {
        int sum = 0;
        for (int i = 0; i + 1 < buffer.length; i += 2) {
            sum += readUint16(buffer, i);
            sum %= USHRT_MAX;
        }
        if (buffer.length % 2 == 1) {
            sum += buffer[buffer.length - 1] & 0xFF;
        }
        return USHRT_MAX - sum - 1;
    }

    private static byte[] readFully(DataInputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        in.readFully(buffer);
        return buffer;
    }

    private static int readUint16(byte[] buffer, int offset) {
        return (buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] buffer, int offset) {
        return (buffer[offset] & 0xFF)
                | ((buffer[offset + 1] & 0xFF) << 8)
                | ((buffer[offset + 2] & 0xFF) << 16)
                | ((buffer[offset + 3] & 0xFF) << 24);
    }

    private static void writeUint16(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
