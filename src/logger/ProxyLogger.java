package logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Very small console logger. Every line starts with a timestamp.
 * System.out.println() is synchronized, so lines from different threads
 * never get mixed together in the middle of a line.
 */
public final class ProxyLogger {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ProxyLogger() {
        // utility class, nothing to create
    }

    /** General information (server started, tunnel closed, ...). */
    public static void info(String message) {
        print("INFO ", message);
    }

    /** Something went wrong (bad request, connection failure, ...). */
    public static void error(String message) {
        print("ERROR", message);
    }

    /** One line describing a proxied request: who, what, where, HTTP or HTTPS, and the status. */
    public static void connection(String clientIp, String method, String host, int port,
                                  String protocol, String status) {
        info(String.format("%-15s | %-7s | %s:%d | %-5s | %s",
                clientIp, method, host, port, protocol, status));
    }

    private static void print(String level, String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + "] [" + level + "] " + message);
    }
}
