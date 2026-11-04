package simpletomcat.util.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;

/**
 * 블로킹 연결 수락과 소켓 종료를 소유하는 네트워크 진입점이다.
 * <p>한 스레드에서 연결을 순차 처리하며 HTTP 책임은 주입한 Handler에 둔다.
 * 처리 완료·실패 후 연결을 닫고 다음 연결을 받는다. 다른 스레드의 종료 요청은
 * 수신 소켓과 현재 소켓을 닫아 블로킹 입출력 대기를 해제한다.
 */
public final class Endpoint implements AutoCloseable {
    /** 서버가 계속 실행될 때 개별 연결의 입출력 실패를 기록한다. */
    private static final System.Logger LOGGER = System.getLogger(Endpoint.class.getName());
    /**
     * 수락한 연결 한 건의 처리를 위임받는 계약이다.
     * <p>처리가 끝나면 반환해야 하며 소켓의 최종 종료는 Endpoint가 담당한다.
     */
    @FunctionalInterface
    public interface Handler {
        /**
         * 현재 연결에서 필요한 입출력을 수행한다.
         * @param socket Endpoint가 수락하고 처리 중 연결로 등록한 소켓
         * @throws IOException 연결 처리 중 입출력 실패; Endpoint는 기록 후 다음 연결을 받는다
         */
        void handle(Socket socket) throws IOException;
    }
    /** 수락한 소켓에 대해 같은 실행 스레드에서 호출할 처리기다. */
    private final Handler handler;

    /** 생성 시 바인딩하고 close 시 닫는 수신 소켓이다. */
    private final ServerSocket serverSocket;
    /**
     * 현재 처리 중인 연결이며 없으면 null이다.
     * 등록·해제·종료를 이 객체의 모니터로 보호해 close와 등록의 경합을 제어한다.
     */
    private Socket currentSocket;

    /**
     * 처리기를 저장하고 지정한 포트에 수신 소켓을 바인딩한다.
     * @param port 수신 포트, 0이면 운영체제가 사용 가능한 포트를 배정한다
     * @param handler 연결 한 건을 처리할 대상
     * @throws NullPointerException 처리기가 null인 경우
     * @throws IOException 소켓 생성 또는 포트 바인딩 실패
     */
    public Endpoint(int port, Handler handler) throws IOException {
        this.handler = Objects.requireNonNull(handler);
        serverSocket = new ServerSocket(port);
    }

    /**
     * 실제 바인딩된 포트를 반환한다.
     * @return 포트 0으로 생성한 경우를 포함한 실제 수신 포트
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * 한 스레드에서 연결 수락 → 현재 연결 등록 → Handler 호출 → 소켓 종료를 반복한다.
     * <p>close에 따른 accept 실패는 정상 종료로 처리한다. 개별 연결의 IOException은
     * 서버가 열려 있으면 기록하고 다음 연결을 받는다. 처리 중 연결은 성공·실패와
     * 무관하게 닫고 등록을 해제한다. 여러 스레드에서 동시에 호출하지 않는다.
     *
     * @throws IOException 서버 종료에 의한 경우 외의 연결 수락 실패
     */
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

                handler.handle(socket);
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

    /**
     * 수신 소켓을 닫고 이어서 현재 처리 중인 소켓도 닫는다.
     * <p>accept 또는 Handler의 소켓 읽기 대기를 해제하며 여러 번 호출할 수 있다.
     * 수신 소켓 종료가 실패해도 finally에서 현재 연결의 종료를 시도한다.
     * Handler가 소켓과 무관한 작업을 하는 경우 그 작업의 종료까지 기다리지는 않는다.
     *
     * @throws IOException 수신 소켓 또는 현재 연결을 닫는 중 발생한 오류
     */
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

}
