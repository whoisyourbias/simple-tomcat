package simpletomcat.coyote;

/**
 * HTTP 프로토콜 처리와 상위 애플리케이션 처리를 연결하는 경계다.
 * <p>Processor가 파싱·검증한 요청을 전달하면 구현체가 응답을 채운다.
 * 현재는 예제 또는 테스트 구현체를 연결하며, 실제 Catalina 연결은 후속 단계다.
 */
@FunctionalInterface
public interface Adapter {
    /**
     * 준비된 요청을 처리하고 전달받은 응답 객체에 상태·헤더·본문을 설정한다.
     * <p>소켓 읽기·쓰기와 종료는 호출자 측 책임이다. 현재 Processor는 일반 예외를
     * 500 응답으로 바꾸지만 {@link java.io.InterruptedIOException}은 상위로 전달한다.
     *
     * @param request 파싱과 요청 검증을 통과한 프로토콜 데이터
     * @param response 처리 결과를 기록할 응답 버퍼
     * @throws Exception 애플리케이션 처리 실패
     */
    void service(Request request, Response response) throws Exception;
}
