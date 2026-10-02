package proxy;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Small helper methods shared by the proxy classes.
 */
public final class HttpUtils {

    private static final int MAX_LINE_LENGTH = 8192;
    private static final int BUFFER_SIZE = 8192;

    private HttpUtils() {
        // utility class, nothing to create
    }

    /**
     * Reads one line (up to \n) from the stream and removes the trailing \r\n.
     * Returns null if the stream ended before any byte was read.
     * HTTP headers are plain ASCII, so every byte is simply treated as one character.
     */
    public static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                int length = line.length();
                if (length > 0 && line.charAt(length - 1) == '\r') {
                    line.setLength(length - 1);
                }
                return line.toString();
            }
            if (line.length() >= MAX_LINE_LENGTH) {
                throw new IllegalArgumentException("Line is too long");
            }
            line.append((char) b);
        }
        return line.length() == 0 ? null : line.toString();
    }

    /** Copies everything from in to out until in reaches end-of-stream. Returns the number of bytes copied. */
    public static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            out.flush();
            total += n;
        }
        return total;
    }

    /** Copies exactly 'length' bytes (used for request bodies with a Content-Length header). */
    public static void copyExactly(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = length;
        while (remaining > 0) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (n == -1) {
                throw new EOFException("Connection closed before the full request body was received");
            }
            out.write(buffer, 0, n);
            remaining -= n;
        }
        out.flush();
    }

    /** Sends a small plain-text HTTP error response, for example 400 Bad Request or 502 Bad Gateway. */
    public static void sendError(OutputStream out, int code, String reason, String message) {
        try {
            byte[] body = (message + "\n").getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
            out.flush();
        } catch (IOException e) {
            // The client has probably already disconnected. Nothing more we can do.
        }
    }

    /** Closes something (socket, stream) and ignores any error. */
    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
