package proxy;

import logger.ProxyLogger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Handles a plain HTTP request:
 *   1. connect to the destination web server
 *   2. forward the client's request
 *   3. send the server's response back to the client
 */
public class HttpProxy {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    // Headers that only make sense between the client and the proxy.
    // They must not be passed on to the web server.
    private static final Set<String> SKIPPED_HEADERS = Set.of(
            "proxy-connection", "connection", "keep-alive", "proxy-authorization", "te", "upgrade", "expect");

    private final Socket clientSocket;
    private final InputStream clientIn;
    private final HttpRequest request;

    public HttpProxy(Socket clientSocket, InputStream clientIn, HttpRequest request) {
        this.clientSocket = clientSocket;
        this.clientIn = clientIn;
        this.request = request;
    }

    public void handle() throws IOException {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        String method = request.getMethod();
        String host = request.getHost();
        int port = request.getPort();
        OutputStream clientOut = clientSocket.getOutputStream();

        // Request bodies with "Transfer-Encoding: chunked" are not supported (see README limitations)
        if (request.getHeader("Transfer-Encoding") != null) {
            ProxyLogger.connection(clientIp, method, host, port, "HTTP", "Rejected (chunked request body)");
            HttpUtils.sendError(clientOut, 501, "Not Implemented", "Chunked request bodies are not supported");
            return;
        }

        // Size of the request body (POST/PUT), 0 if there is none
        long bodyLength = 0;
        String contentLength = request.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                bodyLength = Long.parseLong(contentLength);
                if (bodyLength < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                ProxyLogger.connection(clientIp, method, host, port, "HTTP", "Rejected (bad Content-Length)");
                HttpUtils.sendError(clientOut, 400, "Bad Request", "Invalid Content-Length header");
                return;
            }
        }

        ProxyLogger.connection(clientIp, method, host, port, "HTTP", "Connecting to server...");

        try (Socket server = new Socket()) {
            // 1. Connect to the destination web server
            try {
                server.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            } catch (IOException e) {
                ProxyLogger.connection(clientIp, method, host, port, "HTTP", "FAILED to connect: " + e.getMessage());
                HttpUtils.sendError(clientOut, 502, "Bad Gateway", "Proxy could not connect to " + host + ":" + port);
                return;
            }
            server.setSoTimeout(READ_TIMEOUT_MS);
            OutputStream serverOut = server.getOutputStream();
            InputStream serverIn = new BufferedInputStream(server.getInputStream());

            // 2. Forward the request (headers, then the body if there is one)
            serverOut.write(buildRequestHead().getBytes(StandardCharsets.ISO_8859_1));
            if (bodyLength > 0) {
                HttpUtils.copyExactly(clientIn, serverOut, bodyLength);
            }
            serverOut.flush();

            // 3. Send the response back. We read the first line (e.g. "HTTP/1.1 200 OK") separately
            //    so that we can log the status, then copy every remaining byte unchanged.
            String statusLine = HttpUtils.readLine(serverIn);
            if (statusLine == null) {
                ProxyLogger.connection(clientIp, method, host, port, "HTTP", "FAILED (server sent no response)");
                HttpUtils.sendError(clientOut, 502, "Bad Gateway", "The server sent an empty response");
                return;
            }
            clientOut.write((statusLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            long bytes = statusLine.length() + 2 + HttpUtils.copy(serverIn, clientOut);

            int space = statusLine.indexOf(' ');
            String status = space == -1 ? statusLine : statusLine.substring(space + 1);
            ProxyLogger.connection(clientIp, method, host, port, "HTTP", "Response " + status + " (" + bytes + " bytes)");
        }
    }

    /**
     * Builds the request that is sent to the web server.
     * The client sent an absolute URL ("GET http://example.com/a HTTP/1.1");
     * the web server expects only the path ("GET /a HTTP/1.1").
     */
    private String buildRequestHead() {
        StringBuilder head = new StringBuilder();
        head.append(request.getMethod()).append(' ').append(request.getPath()).append(" HTTP/1.1\r\n");

        boolean hasHost = false;
        for (String header : request.getHeaders()) {
            int colon = header.indexOf(':');
            if (colon <= 0) {
                continue; // ignore malformed header lines
            }
            String name = header.substring(0, colon).trim().toLowerCase();
            if (SKIPPED_HEADERS.contains(name)) {
                continue;
            }
            if (name.equals("host")) {
                hasHost = true;
            }
            head.append(header).append("\r\n");
        }
        if (!hasHost) {
            head.append("Host: ").append(request.getHost());
            if (request.getPort() != 80) {
                head.append(':').append(request.getPort());
            }
            head.append("\r\n");
        }

        // Ask the server to close the connection after this one response.
        // That way "end of response" is simply "the server closed the connection".
        head.append("Connection: close\r\n\r\n");
        return head.toString();
    }
}
