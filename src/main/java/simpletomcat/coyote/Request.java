package simpletomcat.coyote;

import java.util.*;

/**
 * 한 HTTP 요청의 프로토콜 데이터를 보관하는 불변 객체다.
 * <p>URI 경로와 쿼리는 디코딩하지 않는다. 헤더 이름은 소문자로 정규화하고
 * 동일 이름의 여러 값을 보존한다. 서블릿 요청이나 요청 본문을 제공하는 타입은 아니다.
 */
public final class Request {
    /** 요청 메서드, 원시 URI 경로, 원시 쿼리와 HTTP 버전이다. 쿼리는 없으면 null이다. */
    private final String method, requestURI, queryString, protocol;
    /** 소문자 이름별 헤더 값의 불변 복사본이다. */
    private final Map<String, List<String>> headers;

    /**
     * 요청 데이터를 저장하고 외부 변경이 전파되지 않도록 헤더와 값 목록을 복사한다.
     * <p>대소문자만 다른 헤더 이름은 같은 목록으로 병합한다. HTTP 문법 검증은
     * 이 생성자가 아니라 Processor가 담당한다.
     *
     * @param method 요청 메서드
     * @param requestURI 쿼리를 제외한 디코딩 전 경로
     * @param queryString 물음표 뒤의 디코딩 전 쿼리, 없으면 null
     * @param protocol HTTP 버전 문자열
     * @param headers 헤더 이름별 값 목록
     * @throws NullPointerException 필수 인자 또는 헤더 이름·목록·값이 null인 경우
     */
    public Request(String method, String requestURI, String queryString, String protocol,
                   Map<String, List<String>> headers) {
        this.method = Objects.requireNonNull(method);
        this.requestURI = Objects.requireNonNull(requestURI);
        this.queryString = queryString;
        this.protocol = Objects.requireNonNull(protocol);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((name, values) -> copy.computeIfAbsent(name.toLowerCase(Locale.ROOT),
                ignored -> new ArrayList<>()).addAll(values));
        copy.replaceAll((name, values) -> List.copyOf(values));
        this.headers = Collections.unmodifiableMap(copy);
    }
    /**
     * 요청 메서드를 반환한다.
     * @return 대소문자를 보존한 메서드
     */
    public String getMethod() { return method; }
    /**
     * 쿼리를 제외한 원시 요청 경로를 반환한다.
     * @return 퍼센트 디코딩하지 않은 URI 경로
     */
    public String getRequestURI() { return requestURI; }
    /**
     * 원시 쿼리를 반환한다.
     * @return 쿼리가 없으면 null, 물음표만 있으면 빈 문자열
     */
    public String getQueryString() { return queryString; }
    /**
     * 요청의 HTTP 버전을 반환한다.
     * @return HTTP/1.0 또는 HTTP/1.1 등의 버전 문자열
     */
    public String getProtocol() { return protocol; }
    /**
     * 이름의 대소문자와 무관하게 첫 번째 헤더 값을 조회한다.
     * @param name 조회할 헤더 이름
     * @return 첫 번째 값, 헤더가 없으면 null
     * @throws NullPointerException 이름이 null인 경우
     */
    public String getHeader(String name) {
        List<String> values = getHeaders(name);
        return values.isEmpty() ? null : values.getFirst();
    }
    /**
     * 이름의 대소문자와 무관하게 해당 헤더의 모든 값을 조회한다.
     * @param name 조회할 헤더 이름
     * @return 저장된 순서의 수정 불가 목록, 헤더가 없으면 빈 목록
     * @throws NullPointerException 이름이 null인 경우
     */
    public List<String> getHeaders(String name) {
        return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }
}
