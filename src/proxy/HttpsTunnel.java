package proxy;

import logger.ProxyLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles an HTTPS request (the HTTP CONNECT method).
 *
 * The proxy never looks inside the traffic. It only:
 *   1. connects to the destination server,
 *   2. tells the client "200 Connection Established",
 *   3. copies bytes in both directions until one side closes.
 *
 * The bytes are encrypted (TLS) end-to-end between the browser and the website,
 * so the proxy cannot read them.
 */
public class HttpsTunnel {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int IDLE_TIMEOUT_MS = 120_000;
    private static final int BUFFER_SIZE = 8192;

    private final Socket clientSocket;
    private final InputStream clientIn;
    private final HttpRequest request;

    public HttpsTunnel(Socket clientSocket, InputStream clientIn, HttpRequest request) {
        this.clientSocket = clientSocket;
        this.clientIn = clientIn;
        this.request = request;
    }

    public void handle() throws IOException {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        String host = request.getHost();
        int port = request.getPort();
        OutputStream clientOut = clientSocket.getOutputStream();

        ProxyLogger.connection(clientIp, "CONNECT", host, port, "HTTPS", "Connecting to server...");

        Socket server = new Socket();
        try {
            // 1. Connect to the destination server
            try {
                server.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            } catch (IOException e) {
                ProxyLogger.connection(clientIp, "CONNECT", host, port, "HTTPS", "FAILED to connect: " + e.getMessage());
                HttpUtils.sendError(clientOut, 502, "Bad Gateway", "Proxy could not connect to " + host + ":" + port);
                return;
            }
            server.setSoTimeout(IDLE_TIMEOUT_MS);
            clientSocket.setSoTimeout(IDLE_TIMEOUT_MS);

            // 2. Tell the client that the tunnel is ready
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            clientOut.flush();
            ProxyLogger.connection(clientIp, "CONNECT", host, port, "HTTPS", "Tunnel established");

            // 3. Copy bytes in both directions at the same time.
            //    A helper thread copies client -> server, this thread copies server -> client.
            InputStream serverIn = server.getInputStream();
            OutputStream serverOut = server.getOutputStream();
            AtomicLong bytesUp = new AtomicLong();
            AtomicLong bytesDown = new AtomicLong();

            Thread upstream = new Thread(() -> {
                pipe(clientIn, serverOut, bytesUp);
                closeBoth(server); // client is done, so stop the other direction as well
            }, "tunnel-up-" + host);
            upstream.start();

            pipe(serverIn, clientOut, bytesDown);
            closeBoth(server);     // server is done, so stop the other direction as well

            try {
                upstream.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            ProxyLogger.connection(clientIp, "CONNECT", host, port, "HTTPS",
                    "Tunnel closed (sent " + bytesUp.get() + " bytes, received " + bytesDown.get() + " bytes)");
        } finally {
            HttpUtils.closeQuietly(server);
        }
    }

    /**
     * Copies bytes from in to out until the stream ends or fails.
     * A failure here is normal: it just means the connection was closed, so it is not reported.
     */
    private void pipe(InputStream in, OutputStream out, AtomicLong counter) {
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
                counter.addAndGet(n);
            }
        } catch (IOException e) {
            // closed, reset or timed out: the tunnel is finished
        }
    }

    /** Closing the sockets makes the other thread's blocked read() fail, which ends its loop. */
    private void closeBoth(Socket server) {
        HttpUtils.closeQuietly(server);
        HttpUtils.closeQuietly(clientSocket);
    }
}
