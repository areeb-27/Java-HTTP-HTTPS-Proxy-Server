package server;

import logger.ProxyLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Listens on a port and starts a new thread for every client that connects.
 */
public class ProxyServer {

    private final String bindAddress;
    private final int port;

    public ProxyServer(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    /** Starts listening. This method runs until the program is stopped (Ctrl+C). */
    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName(bindAddress))) {
            ProxyLogger.info("Proxy server listening on " + bindAddress + ":" + port);
            ProxyLogger.info("Press Ctrl+C to stop");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> ProxyLogger.info("Proxy server stopped")));

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();   // waits here for the next client
                    Thread thread = new Thread(new ClientHandler(clientSocket), "client-" + clientSocket.getPort());
                    thread.start();
                } catch (IOException e) {
                    // One failed accept() must not stop the whole server
                    ProxyLogger.error("Could not accept a client connection: " + e.getMessage());
                }
            }
        }
    }
}
