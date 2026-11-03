package simpletomcat.util.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(10)
class EndpointTest {
    private static final String REQUEST = "GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n";

    @Test
    void respondsToTwoConnectionsOnTheSameServer() throws Exception {
        try (RunningEndpoint server = new RunningEndpoint()) {
            assertResponse(request(server.port()));
            assertResponse(request(server.port()));
        }
    }

    @Test
    void waitsForTheEndOfHeadersAcrossSocketWrites() throws Exception {
        try (RunningEndpoint server = new RunningEndpoint(); Socket socket = connect(server.port())) {
            write(socket, "GET /hello HTTP/1.1\r\nHost: localhost\r\n\r");
            socket.setSoTimeout(200);
            assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());

            write(socket, "\n");
            socket.setSoTimeout(2000);
            assertResponse(readResponse(socket));
        }
    }

    @Test
    void closesIncompleteRequestsWithoutStoppingTheServer() throws Exception {
        try (RunningEndpoint server = new RunningEndpoint()) {
            try (Socket socket = connect(server.port())) {
                write(socket, "GET /hello HTTP/1.1\r\nHost: localhost\r\n");
                socket.shutdownOutput();
                assertEquals("", readResponse(socket));
            }
            assertResponse(request(server.port()));
        }
    }

    @Test
    void closeUnblocksAcceptAndReleasesThePort() throws Exception {
        try (RunningEndpoint server = new RunningEndpoint()) {
            int port = server.port();
            server.stop();
            server.stop();
            try (ServerSocket rebound = new ServerSocket(port)) {
                assertEquals(port, rebound.getLocalPort());
            }
        }
    }

    @Test
    void closeUnblocksAnIncompleteRequest() throws Exception {
        try (RunningEndpoint server = new RunningEndpoint(); Socket socket = connect(server.port())) {
            write(socket, "GET /hello HTTP/1.1\r\n");
            socket.setSoTimeout(200);
            assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());
            server.stop();
        }
    }

    private static String request(int port) throws IOException {
        try (Socket socket = connect(port)) {
            write(socket, REQUEST);
            return readResponse(socket);
        }
    }

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        socket.setSoTimeout(2000);
        return socket;
    }

    private static void write(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static String readResponse(Socket socket) throws IOException {
        // readAllBytes가 반환하려면 서버가 응답 후 연결까지 닫아야 한다.
        return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void assertResponse(String response) {
        String[] parts = response.split("\r\n\r\n", 2);
        assertEquals(2, parts.length, "HTTP headers and body must be separated by CRLF CRLF");
        assertEquals("HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: 22\r\n"
                + "Connection: close", parts[0]);
        assertEquals("Hello, simple-tomcat!\n", parts[1]);
    }

    private static final class RunningEndpoint implements AutoCloseable {
        private final Endpoint endpoint = new Endpoint(0);
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<?> serving;

        private RunningEndpoint() throws IOException {
            serving = executor.submit(() -> {
                endpoint.serve();
                return null;
            });
        }

        private int port() {
            return endpoint.getPort();
        }

        private void stop() throws Exception {
            endpoint.close();
            serving.get(2, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            try {
                stop();
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
