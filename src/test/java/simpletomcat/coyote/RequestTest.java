package simpletomcat.coyote;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestTest {
    /** 요청의 메서드·프로토콜과 디코딩하지 않은 경로·쿼리 문자열을 그대로 보존하는지 검증한다. */
    @Test void requestPreservesRawPathAndQuery() {
        Request request = new Request("GET", "/hello%20world", "name=a%20b", "HTTP/1.1", Map.of());
        assertEquals("GET", request.getMethod());
        assertEquals("/hello%20world", request.getRequestURI());
        assertEquals("name=a%20b", request.getQueryString());
        assertEquals("HTTP/1.1", request.getProtocol());
    }
    /** 헤더 이름의 대소문자를 무시하고 복수 값을 보존하며, 없는 헤더는 null 또는 빈 목록을 반환하는지 검증한다. */
    @Test void headerLookupIgnoresCaseAndPreservesValues() {
        Request request = new Request("GET", "/", null, "HTTP/1.1", Map.of("X-Tag", List.of("one", "two")));
        assertEquals("one", request.getHeader("X-TAG"));
        assertEquals(List.of("one", "two"), request.getHeaders("x-tag"));
        assertNull(request.getHeader("missing"));
        assertEquals(List.of(), request.getHeaders("missing"));
    }
    /** 원본 헤더 맵·목록의 변경이 요청에 영향을 주지 않고, 조회한 목록도 수정할 수 없는지 검증한다. */
    @Test void requestDefensivelyCopiesHeaderLists() {
        var values = new ArrayList<>(List.of("one"));
        var headers = new HashMap<String, List<String>>();
        headers.put("X-Tag", values);
        Request request = new Request("GET", "/", null, "HTTP/1.1", headers);
        values.add("two"); headers.clear();
        assertEquals(List.of("one"), request.getHeaders("X-Tag"));
        assertThrows(UnsupportedOperationException.class, () -> request.getHeaders("X-Tag").add("three"));
    }
}
