package proxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The first part of an HTTP request sent by the client: request line + headers.
 *
 * A browser that uses a proxy sends one of these two forms:
 *
 *   GET http://example.com/index.html HTTP/1.1     (plain HTTP)
 *   CONNECT example.com:443 HTTP/1.1               (HTTPS tunnel)
 */
public class HttpRequest {

    private static final int MAX_HEADERS = 100;

    private final String method;
    private final String host;
    private final int port;
    private final String path;          // e.g. "/index.html?x=1" (null for CONNECT)
    private final List<String> headers; // raw header lines, e.g. "Host: example.com"

    private HttpRequest(String method, String host, int port, String path, List<String> headers) {
        this.method = method;
        this.host = host;
        this.port = port;
        this.path = path;
        this.headers = headers;
    }

    /**
     * Reads and parses a request from the stream.
     *
     * @return the request, or null if the client connected and closed without sending anything
     *         (browsers do this sometimes to "pre-connect")
     * @throws IllegalArgumentException if the request is invalid (the caller answers 400 Bad Request)
     * @throws IOException              if reading from the socket fails
     */
    public static HttpRequest parse(InputStream in) throws IOException {
        String requestLine = HttpUtils.readLine(in);
        if (requestLine == null) {
            return null;
        }

        // Request line looks like: METHOD SP TARGET SP VERSION
        String[] parts = requestLine.split(" ");
        if (parts.length != 3 || !parts[2].startsWith("HTTP/")) {
            throw new IllegalArgumentException("Malformed request line");
        }
        String method = parts[0].toUpperCase();
        String target = parts[1];

        // Header lines follow, until an empty line
        List<String> headers = new ArrayList<>();
        while (true) {
            String line = HttpUtils.readLine(in);
            if (line == null) {
                throw new IllegalArgumentException("Connection closed before the headers were complete");
            }
            if (line.isEmpty()) {
                break;
            }
            if (headers.size() >= MAX_HEADERS) {
                throw new IllegalArgumentException("Too many headers");
            }
            headers.add(line);
        }

        // HTTPS: CONNECT example.com:443 HTTP/1.1
        if (method.equals("CONNECT")) {
            HostPort hostPort = parseHostPort(target, 443);
            return new HttpRequest(method, hostPort.host(), hostPort.port(), null, headers);
        }

        // HTTP: GET http://example.com:8080/path?query HTTP/1.1
        if (!target.regionMatches(true, 0, "http://", 0, 7)) {
            throw new IllegalArgumentException(
                    "This is a proxy server. Expected a full URL starting with http:// but got: " + target);
        }
        String rest = target.substring(7); // everything after "http://"

        int pathStart = -1;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?') {
                pathStart = i;
                break;
            }
        }

        String authority;   // "host" or "host:port"
        String path;
        if (pathStart == -1) {
            authority = rest;
            path = "/";
        } else {
            authority = rest.substring(0, pathStart);
            path = rest.charAt(pathStart) == '?' ? "/" + rest.substring(pathStart) : rest.substring(pathStart);
        }
        authority = authority.substring(authority.lastIndexOf('@') + 1); // drop optional "user:pass@"

        HostPort hostPort = parseHostPort(authority, 80);
        return new HttpRequest(method, hostPort.host(), hostPort.port(), path, headers);
    }

    private record HostPort(String host, int port) {
    }

    /** Splits "example.com:8080" into host and port. Uses defaultPort when no port is given. */
    private static HostPort parseHostPort(String authority, int defaultPort) {
        String host = authority;
        int port = defaultPort;

        int colon = authority.lastIndexOf(':');
        if (colon != -1) {
            host = authority.substring(0, colon);
            try {
                port = Integer.parseInt(authority.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port in: " + authority);
            }
        }
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Missing host name");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        return new HostPort(host, port);
    }

    /** Returns the value of a header (case-insensitive name), or null if the header is not present. */
    public String getHeader(String name) {
        for (String header : headers) {
            int colon = header.indexOf(':');
            if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase(name)) {
                return header.substring(colon + 1).trim();
            }
        }
        return null;
    }

    public boolean isConnect() {
        return method.equals("CONNECT");
    }

    public String getMethod() {
        return method;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public List<String> getHeaders() {
        return headers;
    }
}
