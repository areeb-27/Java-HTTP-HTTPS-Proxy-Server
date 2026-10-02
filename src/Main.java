import logger.ProxyLogger;
import server.ProxyServer;

import java.io.IOException;

/**
 * Entry point of the Java HTTP/HTTPS Proxy Server.
 *
 * Usage:  java Main          (uses the default port 8888)
 *         java Main 9090     (uses port 9090)
 */
public class Main {

    // ---- Simple configuration: change these two values if you need to ----
    private static final int DEFAULT_PORT = 8888;

    // 127.0.0.1 = only programs on THIS computer can use the proxy (safe default).
    // Use "0.0.0.0" to let other devices on your network use it too.
    private static final String BIND_ADDRESS = "127.0.0.1";
    // ------------------------------------------------------------------------

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                System.out.println("Usage: java Main [port]   (port must be a number from 1 to 65535)");
                System.exit(1);
            }
        }

        try {
            new ProxyServer(BIND_ADDRESS, port).start();
        } catch (IOException e) {
            ProxyLogger.error("Could not start the proxy on port " + port + ": " + e.getMessage());
            ProxyLogger.error("Is another program (or another copy of this proxy) already using that port?");
            System.exit(1);
        }
    }
}
