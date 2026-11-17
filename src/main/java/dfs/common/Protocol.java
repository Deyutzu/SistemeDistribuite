package dfs.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Constante și metode comune pentru protocolul de comunicație TCP și UDP multicast.
 */
public final class Protocol {
    public static final long MAX_FILE_SIZE = 500L * 1024L * 1024L;
    public static final int BUFFER_SIZE = 64 * 1024;
    public static final int MULTICAST_PORT = 4446;
    public static final String MULTICAST_GROUP_PREFIX = "239.255.0.";
    public static final String HELLO_PREFIX = "HELLO";

    private Protocol() {
    }

    public static String multicastGroup(int team) {
        return MULTICAST_GROUP_PREFIX + team;
    }

    public static void writeHeader(DataOutputStream out, String header) throws IOException {
        out.writeUTF(header);
        out.flush();
    }

    public static String[] readHeader(DataInputStream in) throws IOException {
        String header = in.readUTF();
        if (header == null) {
            return new String[0];
        }
        String trimmed = header.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    public static boolean isValidFileName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return !name.contains("/")
                && !name.contains("\\")
                && !name.contains("..")
                && !".".equals(name);
    }
}
