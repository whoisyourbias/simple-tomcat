package simpletomcat.coyote;

import java.io.IOException;

/**
 * 프로토콜 실행과 종료를 제공하는 최소 계약이다.
 * <p>현재 구현은 호출 스레드에서 블로킹 실행하며 전체 Tomcat 생명주기 계약은 후속 단계다.
 */
public interface ProtocolHandler extends AutoCloseable {
    /**
     * 상위 요청 처리를 담당하는 Adapter를 반환한다.
     * @return 프로토콜에 연결된 Adapter
     */
    Adapter getAdapter();
    /**
     * 실제 바인딩된 수신 포트를 반환한다.
     * @return 포트 0으로 생성했다면 운영체제가 배정한 포트
     */
    int getPort();
    /**
     * 연결을 수락하고 처리하며 종료될 때까지 호출 스레드를 점유한다.
     * @throws IOException 연결 수락 등 서버 실행 중 복구하지 못한 입출력 오류
     */
    void serve() throws IOException;
    /**
     * 수신 소켓과 처리 중인 연결을 닫아 블로킹 실행을 종료시킨다.
     * @throws IOException 자원을 닫는 중 발생한 오류
     */
    @Override void close() throws IOException;
}
