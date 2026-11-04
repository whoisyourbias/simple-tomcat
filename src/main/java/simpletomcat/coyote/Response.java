package simpletomcat.coyote;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Adapter가 작성할 상태·헤더·본문을 메모리에 모으는 학습용 응답이다.
 * <p>본문은 UTF-8 바이트로 저장한다. Content-Length와 연결 종료 등 전송 경계는
 * Processor가 결정하므로 Adapter는 해당 헤더를 직접 설정할 수 없다.
 */
public final class Response {
    /** 기본 응답 상태다. 값의 허용 범위는 전송 전 Processor가 검사한다. */
    private int status = 200;
    /** 소문자 헤더 이름별 단일 값이며 설정 순서를 보존한다. */
    private final Map<String, String> headers = new LinkedHashMap<>();
    /** 전송할 UTF-8 본문이며 기본값은 빈 본문이다. */
    private byte[] body = new byte[0];

    /** 상태 200·헤더 없음·빈 본문으로 응답을 생성한다. */
    public Response() { }

    /**
     * 현재 응답 상태를 반환한다.
     * @return 설정된 상태 코드, 기본값은 200
     */
    public int getStatus() { return status; }
    /**
     * 응답 상태를 설정한다. 200~599 범위 밖의 값은 Processor가 전송 전 500으로 바꾼다.
     * @param status 설정할 상태 코드
     */
    public void setStatus(int status) { this.status = status; }
    /**
     * 헤더 이름과 값을 검사한 뒤 저장하며 같은 이름의 기존 값을 덮어쓴다.
     * <p>프로젝트 정책상 값은 출력 가능한 ASCII만 허용한다. Content-Length,
     * Connection, Transfer-Encoding은 Processor 소유이므로 설정을 거부한다.
     *
     * @param name HTTP token 문법의 헤더 이름
     * @param value 제어문자 없는 ASCII 값
     * @throws NullPointerException 이름 또는 값이 null인 경우
     * @throws IllegalArgumentException 문법·문자 제한 위반 또는 전송 제어 헤더인 경우
     */
    public void setHeader(String name, String value) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        if (name.isEmpty() || !name.chars().allMatch(Response::isToken)) {
            throw new IllegalArgumentException("Invalid header name");
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (Set.of("content-length", "connection", "transfer-encoding").contains(key)) {
            throw new IllegalArgumentException("Framing header belongs to Processor");
        }
        if (!value.chars().allMatch(c -> c >= 32 && c < 127)) {
            throw new IllegalArgumentException("Header values must be printable ASCII");
        }
        headers.put(key, value);
    }
    /**
     * 헤더의 수정 불가 뷰를 반환한다. 이후 설정한 값은 이 뷰에도 반영된다.
     * @return 소문자 이름을 키로 하는 헤더 뷰
     */
    public Map<String, String> getHeaders() { return Collections.unmodifiableMap(headers); }
    /**
     * 문자열을 UTF-8로 인코딩해 기존 본문을 교체한다.
     * @param body 응답 본문 문자열
     * @throws NullPointerException 본문이 null인 경우
     */
    public void setBody(String body) { this.body = Objects.requireNonNull(body).getBytes(StandardCharsets.UTF_8); }
    /**
     * 내부 본문이 외부에서 바뀌지 않도록 바이트 배열을 복사해 반환한다.
     * @return UTF-8 본문 바이트의 복사본
     */
    public byte[] getBody() { return body.clone(); }

    /**
     * 헤더 이름에 쓸 수 있는 영문자·숫자·기호인지 검사한다.
     * @param c 검사할 문자 코드
     * @return HTTP token 구성 문자이면 true
     */
    private static boolean isToken(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }
}
