package server;

import logger.ProxyLogger;
import proxy.HttpProxy;
import proxy.HttpRequest;
import proxy.HttpUtils;
import proxy.HttpsTunnel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Runs in its own thread and serves exactly one client connection:
 * read the request, then hand it to HttpProxy (plain HTTP) or HttpsTunnel (CONNECT).
 */
public class ClientHandler implements Runnable {

    private static final int REQUEST_TIMEOUT_MS = 30_000;

    private final Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        String clientIp = clientSocket.getInetAddress().getHostAddress();

        // try-with-resources closes the client socket when we are done, whatever happens
        try (Socket socket = clientSocket) {
            socket.setSoTimeout(REQUEST_TIMEOUT_MS);
            InputStream in = new BufferedInputStream(socket.getInputStream());

            HttpRequest request;
            try {
                request = HttpRequest.parse(in);
            } catch (IllegalArgumentException e) {
                ProxyLogger.error(clientIp + " - Invalid request: " + e.getMessage());
                HttpUtils.sendError(socket.getOutputStream(), 400, "Bad Request", e.getMessage());
                return;
            }
            if (request == null) {
                return; // client connected and closed without sending anything
            }

            if (request.isConnect()) {
                new HttpsTunnel(socket, in, request).handle();
            } else {
                new HttpProxy(socket, in, request).handle();
            }

        } catch (SocketTimeoutException e) {
            ProxyLogger.error(clientIp + " - Timed out waiting for data");
        } catch (IOException e) {
            ProxyLogger.error(clientIp + " - Connection problem: " + e.getMessage());
        } catch (RuntimeException e) {
            // Safety net: a bug while serving one client must never affect the other clients
            ProxyLogger.error(clientIp + " - Unexpected error: " + e);
        }
    }
}
