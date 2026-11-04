package simpletomcat.coyote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {
    /**
     * 기본 응답이 200·Content-Type 미설정·빈 본문인지 검증한다.
     * 한글 본문의 UTF-8 바이트 길이, 본문 배열의 방어적 복사와 null 본문 거부도 확인한다.
     */
    @Test void responseDefaultsAndUtf8Bytes() {
        Response response = new Response();
        assertEquals(200, response.getStatus());
        assertNull(response.getHeaders().get("content-type"));
        assertEquals(0, response.getBody().length);
        response.setBody("안녕하세요\n");
        assertEquals(16, response.getBody().length);
        response.getBody()[0] = 0;
        assertNotEquals(0, response.getBody()[0]);
        assertThrows(NullPointerException.class, () -> response.setBody(null));
    }
    /** Response는 최종 전송 가능 여부를 판단하지 않고 애플리케이션이 설정한 상태 값을 그대로 보관하는지 검증한다. */
    @Test void responsePreservesApplicationStatus() {
        Response response = new Response();
        for (int status : new int[]{404, 409, 500, 101, 999}) {
            response.setStatus(status); assertEquals(status, response.getStatus());
        }
    }
    /** 대소문자가 다른 같은 이름의 헤더는 새 값으로 덮어쓰고, 조회한 헤더 맵은 수정할 수 없는지 검증한다. */
    @Test void responseValidatesHeaders() {
        Response response = new Response();
        response.setHeader("Content-Type", "text/plain");
        response.setHeader("CONTENT-TYPE", "text/html");
        assertEquals("text/html", response.getHeaders().get("content-type"));
        assertThrows(UnsupportedOperationException.class, () -> response.getHeaders().put("x", "y"));
    }
    /** 각 프레이밍·연결 제어 헤더를 애플리케이션이 설정하면 대소문자와 관계없이 예외로 거부하는지 검증한다. */
    @ParameterizedTest @ValueSource(strings={"Content-Length", "cOnNeCtIoN", "Transfer-Encoding"})
    void rejectsFramingHeaderOverrides(String name) {
        assertThrows(IllegalArgumentException.class, () -> new Response().setHeader(name, "x"));
    }
    /** CRLF·비ASCII 문자·제어 문자가 든 헤더 값과 공백이 든 헤더 이름을 예외로 거부하는지 검증한다. */
    @ParameterizedTest @ValueSource(strings={"ok\r\nInjected: yes", "한글", "x\u0000y", "x\t y"})
    void rejectsInvalidResponseHeaders(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Response().setHeader("X-Test", value));
        assertThrows(IllegalArgumentException.class, () -> new Response().setHeader("Bad Name", "x"));
    }
}
