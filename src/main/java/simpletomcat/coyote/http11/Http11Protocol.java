package simpletomcat.coyote.http11;

import simpletomcat.coyote.Adapter;
import simpletomcat.coyote.ProtocolHandler;
import simpletomcat.util.net.Endpoint;

import java.io.IOException;
import java.util.Objects;

/**
 * Endpoint의 연결 처리와 HTTP Processor를 조립하는 블로킹 프로토콜 구현이다.
 * <p>생성 시 수신 포트를 바인딩한다. Endpoint가 연결을 순차 처리하며 각 연결의
 * 스트림을 Processor에 전달한다. HTTP 판단은 Processor, 소켓 소유권은 Endpoint에 둔다.
 */
public final class Http11Protocol implements ProtocolHandler {
    /** HTTP 요청 검증 이후 호출할 상위 처리 대상이다. */
    private final Adapter adapter;
    /** 수신 소켓과 현재 연결의 생명주기를 소유한다. */
    private final Endpoint endpoint;

    /**
     * Adapter·Processor·Endpoint를 연결하고 수신 포트를 연다.
     * @param port 수신 포트, 0이면 운영체제가 사용 가능한 포트를 배정한다
     * @param adapter 상위 요청 처리 대상
     * @throws NullPointerException Adapter가 null인 경우
     * @throws IOException 수신 소켓 생성 또는 바인딩 실패
     */
    public Http11Protocol(int port, Adapter adapter) throws IOException {
        this.adapter = Objects.requireNonNull(adapter);
        Http11Processor processor = new Http11Processor(adapter);
        endpoint = new Endpoint(port, socket -> processor.process(socket.getInputStream(), socket.getOutputStream()));
    }
    /**
     * 생성 시 연결한 상위 처리 대상을 반환한다.
     * @return 요청을 처리할 Adapter
     */
    @Override public Adapter getAdapter() { return adapter; }
    /**
     * Endpoint가 실제 바인딩한 포트를 반환한다.
     * @return 실제 수신 포트
     */
    @Override public int getPort() { return endpoint.getPort(); }
    /**
     * 현재 스레드에서 연결 수락과 HTTP 처리를 순차 실행한다.
     * @throws IOException Endpoint가 복구하지 못한 연결 수락 오류
     */
    @Override public void serve() throws IOException { endpoint.serve(); }
    /**
     * Endpoint에 종료를 위임해 수신 소켓과 처리 중인 연결을 닫는다.
     * @throws IOException 소켓 종료 실패
     */
    @Override public void close() throws IOException { endpoint.close(); }

    /**
     * 고정 텍스트를 반환하는 예제 서버를 실행한다.
     * <p>실행 예: {@code java -cp target/classes simpletomcat.coyote.http11.Http11Protocol 8080}.
     * JVM 종료 훅에서도 프로토콜을 닫아 accept 또는 요청 읽기의 대기를 해제한다.
     *
     * @param args 첫 인자는 수신 포트이며 생략하면 8080
     * @throws NumberFormatException 첫 인자를 정수로 해석할 수 없는 경우
     * @throws IOException 서버 생성·실행·종료 중 발생한 입출력 오류
     */
    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        try (Http11Protocol protocol = new Http11Protocol(port, (request, response) -> {
            response.setHeader("Content-Type", "text/plain; charset=UTF-8");
            response.setBody("Hello, simple-tomcat!\n");
        })) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { protocol.close(); }
                catch (IOException e) { System.getLogger(Http11Protocol.class.getName()).log(System.Logger.Level.WARNING,"Shutdown failed",e); }
            }, "http11-shutdown"));
            System.out.println("Http11Protocol listening on http://localhost:" + protocol.getPort());
            protocol.serve();
        }
    }
}
