package simpletomcat.util.net;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * 1단계의 블로킹 소켓 서버. 호출 스레드에서 연결당 요청 한 건을 순서대로 처리한다.
 * HTTP 해석은 다음 단계에서 Coyote Processor로 분리한다.
 */
public final class Endpoint implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(Endpoint.class.getName());
    private static final byte[] HEADER_END = {'\r', '\n', '\r', '\n'};
    private static final byte[] BODY = "Hello, simple-tomcat!\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HEADERS = ("HTTP/1.1 200 OK\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n"
            + "Content-Length: " + BODY.length + "\r\n"
            + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);

    private final ServerSocket serverSocket;
    // 등록과 close를 같은 잠금으로 묶어 종료 직후 새 연결을 읽기 시작하지 않게 한다.
    private Socket currentSocket;

    /** 포트 0을 지정하면 운영체제가 사용 가능한 포트를 배정한다. */
    public Endpoint(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** 한 스레드에서 호출한다. close()가 리스닝 소켓을 닫으면 accept()도 깨어난다. */
    public void serve() throws IOException {
        while (!serverSocket.isClosed()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                    return;
                }
                throw e;
            }

            try (socket) {
                synchronized (this) {
                    if (serverSocket.isClosed()) {
                        return;
                    }
                    currentSocket = socket;
                }

                if (readHeaders(new BufferedInputStream(socket.getInputStream()))) {
                    writeResponse(socket.getOutputStream());
                }
            } catch (IOException e) {
                // 클라이언트 한 건의 연결 오류 때문에 다음 연결 수락까지 중단하지 않는다.
                if (!serverSocket.isClosed()) {
                    LOGGER.log(System.Logger.Level.WARNING, "Connection failed", e);
                }
            } finally {
                synchronized (this) {
                    currentSocket = null;
                }
            }
        }
    }

    private boolean readHeaders(InputStream input) throws IOException {
        int matched = 0;
        int value;
        while ((value = input.read()) != -1) {
            if (value == HEADER_END[matched]) {
                matched++;
                if (matched == HEADER_END.length) {
                    return true;
                }
            } else {
                matched = value == '\r' ? 1 : 0;
            }
        }
        // 헤더 끝에 도달하기 전에 클라이언트가 EOF를 보낸 경우 응답하지 않는다.
        return false;
    }

    private void writeResponse(OutputStream output) throws IOException {
        output.write(HEADERS);
        output.write(BODY);
        output.flush();
    }

    /** accept 또는 헤더 읽기 중에도 종료할 수 있고 여러 번 호출해도 안전하다. */
    @Override
    public synchronized void close() throws IOException {
        try {
            serverSocket.close();
        } finally {
            if (currentSocket != null) {
                currentSocket.close();
            }
        }
    }

    /** java -cp target/classes simpletomcat.util.net.Endpoint [port] */
    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        try (Endpoint endpoint = new Endpoint(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    endpoint.close();
                } catch (IOException e) {
                    LOGGER.log(System.Logger.Level.WARNING, "Endpoint shutdown failed", e);
                }
            }, "endpoint-shutdown"));
            System.out.println("Endpoint listening on http://localhost:" + endpoint.getPort());
            endpoint.serve();
        }
    }
}
