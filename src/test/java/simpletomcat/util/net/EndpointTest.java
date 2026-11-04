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
    /** Handler가 다음 바이트를 읽으며 대기할 때 close가 활성 소켓을 닫아 서버 실행을 끝내는지 검증한다. */
    @Test void closeUnblocksAnIncompleteRequest() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        try (Endpoint endpoint = new Endpoint(0, socket -> {
            socket.getInputStream().read(); entered.countDown(); socket.getInputStream().read();
        }); ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> serving = executor.submit(() -> { endpoint.serve(); return null; });
            try (Socket socket = connect(endpoint.getPort())) {
                socket.getOutputStream().write(1); socket.getOutputStream().flush();
                org.junit.jupiter.api.Assertions.assertTrue(entered.await(2, TimeUnit.SECONDS));
                endpoint.close(); serving.get(2, TimeUnit.SECONDS);
            }
        }
    }
    /** 첫 연결의 Handler에서 IOException이 나면 해당 연결을 닫고 다음 연결은 정상 처리하는지 검증한다. */
    @Test void continuesAfterHandlerIOException() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        try (Endpoint endpoint = new Endpoint(0, socket -> {
            if (calls.incrementAndGet() == 1) throw new IOException("test failure");
            socket.getOutputStream().write("handled\n".getBytes(StandardCharsets.US_ASCII));
        }); ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> serving = executor.submit(() -> { endpoint.serve(); return null; });
            try {
                try (Socket first = connect(endpoint.getPort())) { assertEquals(-1, first.getInputStream().read()); }
                try (Socket second = connect(endpoint.getPort())) { assertResponse(readResponse(second)); }
            } finally { endpoint.close(); serving.get(2, TimeUnit.SECONDS); }
        }
    }
    private static final String REQUEST = "GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n";

    /** 같은 Endpoint가 두 연결을 차례로 Handler에 위임하고, 각 응답 후 연결을 닫는지 검증한다. */
    @Test
    void invokesHandlerForTwoConnectionsOnTheSameServer() throws Exception {
        try (RunningEndpoint server = new RunningEndpoint()) {
            assertResponse(request(server.port()));
            assertResponse(request(server.port()));
        }
    }

    /** 연결 수락 대기 중 반복해서 종료해도 서버가 끝나고, 사용하던 포트를 다시 바인딩할 수 있는지 검증한다. */
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

    private static void assertResponse(String response) { assertEquals("handled\n", response); }

    private static final class RunningEndpoint implements AutoCloseable {
        private final Endpoint endpoint = new Endpoint(0, socket -> { socket.getOutputStream().write("handled\n".getBytes(StandardCharsets.US_ASCII)); });
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
