package simpletomcat.coyote.http11;

import simpletomcat.coyote.Adapter;
import simpletomcat.coyote.Request;
import simpletomcat.coyote.Response;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 입력 스트림의 HTTP 요청 한 건을 응답 바이트로 변환하는 블로킹 처리기다.
 * <p>문법 파싱 → HTTP 버전 확인 → 요청 헤더 검증 → Adapter 호출 → 응답 검증·전송 순서다.
 * 연결당 요청 한 건만 처리하며 요청 본문, keep-alive, 읽기 타임아웃과 헤더 크기 제한은
 * 구현하지 않는다. 스트림과 소켓의 종료는 이 객체가 아니라 Endpoint가 소유한다.
 */
public final class Http11Processor {
    /** 요청 헤더 영역의 끝을 표시하는 CRLF 두 쌍이다. */
    private static final byte[] HEADER_END = {'\r','\n','\r','\n'};
    /** 파싱·검증 이후 애플리케이션 처리를 맡길 대상이다. */
    private final Adapter adapter;

    /**
     * 상위 요청 처리 대상을 연결한다.
     * @param adapter 요청을 처리하고 응답 버퍼를 채울 대상
     * @throws NullPointerException Adapter가 null인 경우
     */
    public Http11Processor(Adapter adapter) { this.adapter = Objects.requireNonNull(adapter); }

    /**
     * 요청 한 건을 읽어 검증하고 Adapter 결과 또는 오류 응답을 전송한다.
     * <p>문법 오류는 400, 지원하지 않는 버전은 505, 본문 미지원은 501로 응답한다.
     * 헤더 완성 전 EOF이면 응답 없이 반환한다. 스트림은 닫지 않는다.
     *
     * @param input 요청 바이트를 읽을 스트림
     * @param output 응답 바이트를 쓸 스트림
     * @throws IOException 읽기·쓰기 오류 또는 Adapter의 중단 관련 입출력 예외
     */
    public void process(InputStream input, OutputStream output) throws IOException {
        Request request;
        try {
            request = readRequest(input);
        } catch (BadRequestException e) {
            writeResponse(null, errorResponse(400), output);
            return;
        }
        if (request == null) return;
        int error = prepareRequestProtocol(request);
        if (error == 0) error = prepareRequest(request);
        Response response = error == 0 ? service(request) : errorResponse(error);
        writeResponse(request, prepareResponse(response), output);
    }

    /**
     * CRLF 두 쌍까지 읽고 요청 행과 헤더의 문법을 검사해 요청 객체를 만든다.
     * <p>요청 대상은 슬래시로 시작하는 형식만 허용하며 첫 물음표에서 경로와 쿼리를
     * 나눈다. 헤더 값 양끝의 SP·HTAB만 제거하고 중복 헤더는 값 목록으로 보존한다.
     * 버전 지원 여부와 Host·본문 관련 의미 검증은 다음 준비 단계에 둔다.
     *
     * @param input 요청 헤더를 읽을 스트림
     * @return 파싱한 요청, 헤더 완성 전 EOF이면 null
     * @throws BadRequestException 요청 행·대상·헤더 문법이 잘못된 경우
     * @throws IOException 입력 스트림 읽기 실패
     */
    private Request readRequest(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < HEADER_END.length) {
            int value = input.read();
            if (value == -1) return null;
            bytes.write(value);
            matched = value == HEADER_END[matched] ? matched + 1 : value == '\r' ? 1 : 0;
        }
        String text = bytes.toString(StandardCharsets.ISO_8859_1);
        String[] lines = text.substring(0, text.length()-4).split("\r\n", -1);
        String[] parts = lines[0].split(" ", -1);
        if (parts.length != 3 || parts[0].isEmpty() || !parts[0].chars().allMatch(Http11Processor::isToken)
                || !parts[2].matches("HTTP/[0-9]\\.[0-9]")) throw new BadRequestException();
        String target = parts[1];
        if (!target.startsWith("/") || !validTarget(target)) throw new BadRequestException();
        int query = target.indexOf('?');
        String path = query < 0 ? target : target.substring(0,query);
        String queryString = query < 0 ? null : target.substring(query+1);
        Map<String,List<String>> headers = new LinkedHashMap<>();
        for (int i=1; i<lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0 || !line.substring(0,colon).chars().allMatch(Http11Processor::isToken)) throw new BadRequestException();
            String value = line.substring(colon+1);
            if (!value.chars().allMatch(c -> c == '\t' || c >= 32 && c != 127)) throw new BadRequestException();
            // HTTP OWS는 SP와 HTAB뿐이다. String.trim()의 넓은 제어문자 처리를 사용하지 않는다.
            value = value.replaceAll("^[ \\t]+|[ \\t]+$", "");
            headers.computeIfAbsent(line.substring(0,colon).toLowerCase(Locale.ROOT), ignored->new ArrayList<>()).add(value);
        }
        return new Request(parts[0],path,queryString,parts[2],headers);
    }

    /**
     * 문법상 올바른 HTTP 버전이 현재 지원 범위인지 확인한다.
     * @param request 요청 행을 파싱한 요청
     * @return HTTP/1.0·1.1이면 0, 지원하지 않는 버전이면 505
     */
    private int prepareRequestProtocol(Request request) {
        return Set.of("HTTP/1.0","HTTP/1.1").contains(request.getProtocol()) ? 0 : 505;
    }

    /**
     * Host와 요청 본문의 전송 경계를 검증해 Adapter 호출 가능 여부를 결정한다.
     * <p>HTTP/1.1의 Host 누락, Host·Content-Length 중복, 전송 길이 헤더 충돌,
     * 숫자가 아니거나 long 범위를 넘는 길이는 400이다. 길이 0은 허용하지만
     * 양수 길이 또는 Transfer-Encoding은 프로젝트의 본문 미지원 정책으로 501이다.
     *
     * @param request 헤더 의미를 검사할 요청
     * @return 통과하면 0, 잘못된 요청이면 400, 본문 미지원이면 501
     */
    private int prepareRequest(Request request) {
        List<String> hosts = request.getHeaders("host");
        if (hosts.size() > 1 || hosts.isEmpty() && request.getProtocol().equals("HTTP/1.1")) return 400;
        if (!hosts.isEmpty() && !validHost(hosts.getFirst())) return 400;
        List<String> lengths = request.getHeaders("content-length");
        boolean transfer = !request.getHeaders("transfer-encoding").isEmpty();
        if (lengths.size() > 1 || transfer && !lengths.isEmpty()) return 400;
        long length = 0;
        if (!lengths.isEmpty()) {
            String value = lengths.getFirst();
            if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) return 400;
            try { length = Long.parseLong(value); } catch (NumberFormatException e) { return 400; }
        }
        return transfer || length > 0 ? 501 : 0;
    }

    /**
     * 새 응답 버퍼를 만들어 Adapter를 호출한다.
     * <p>일반 예외가 발생하면 부분 작성된 응답을 버리고 새 500 응답을 만든다.
     * 중단을 뜻하는 입출력 예외는 오류 응답으로 바꾸지 않고 상위로 전달한다.
     *
     * @param request 파싱과 검증을 마친 요청
     * @return Adapter가 작성한 응답 또는 새 500 응답
     * @throws InterruptedIOException Adapter가 중단 관련 입출력 예외를 던진 경우
     */
    private Response service(Request request) throws InterruptedIOException {
        Response response = new Response();
        try {
            adapter.service(request,response);
            return response;
        } catch (InterruptedIOException e) {
            throw e;
        } catch (Exception e) {
            return errorResponse(500);
        }
    }

    /**
     * 현재 구현이 최종 응답으로 전송할 수 있는 상태 코드인지 검사한다.
     * @param response Adapter가 작성한 응답
     * @return 200~599이면 기존 응답, 그 밖에는 새 500 응답
     */
    private Response prepareResponse(Response response) {
        return response.getStatus() >= 200 && response.getStatus() <= 599 ? response : errorResponse(500);
    }

    /**
     * 상태 행·헤더·본문을 HTTP/1.1 바이트로 쓰고 출력 버퍼를 비운다.
     * <p>항상 Connection: close를 전송한다. 204·304는 길이와 본문을 생략하고,
     * 205는 길이 0과 빈 본문을 보낸다. HEAD는 본문 전송만 생략하며 일반 상태에서는
     * 준비된 본문의 바이트 길이를 유지한다. 상태 행의 reason phrase는 비워 둔다.
     * 실제 연결 종료는 호출자 측에서 수행한다.
     *
     * @param request HEAD 여부를 판단할 요청, 파싱 실패 응답이면 null
     * @param response 전송할 응답 데이터
     * @param output 응답 목적지
     * @throws IOException 응답 쓰기 또는 flush 실패
     */
    private void writeResponse(Request request, Response response, OutputStream output) throws IOException {
        int status = response.getStatus();
        byte[] body = response.getBody();
        boolean noLength = status == 204 || status == 304;
        boolean noBody = noLength || status == 205 || request != null && request.getMethod().equals("HEAD");
        var headers = new StringBuilder("HTTP/1.1 ").append(status).append(" \r\n");
        response.getHeaders().forEach((name,value)->headers.append(name).append(": ").append(value).append("\r\n"));
        if (!noLength) headers.append("Content-Length: ").append(status == 205 ? 0 : body.length).append("\r\n");
        headers.append("Connection: close\r\n\r\n");
        output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
        if (!noBody) output.write(body);
        output.flush();
    }

    /**
     * 상태에 맞는 고정 텍스트 본문을 가진 오류 응답을 생성한다.
     * @param status 응답 상태 코드; 현재 호출 경로는 400·500·501·505를 사용한다
     * @return UTF-8 일반 텍스트 응답; 본문은 400·501·505 외에는 서버 오류 문구다
     */
    private static Response errorResponse(int status) {
        Response response = new Response();
        response.setStatus(status);
        response.setHeader("Content-Type","text/plain; charset=UTF-8");
        response.setBody(switch(status) {
            case 400 -> "Bad Request\n";
            case 501 -> "Not Implemented\n";
            case 505 -> "HTTP Version Not Supported\n";
            default -> "Internal Server Error\n";
        });
        return response;
    }

    /**
     * Host 값을 URI의 authority로 해석할 수 있는지 검사한다.
     * <p>빈 값은 허용한다. 비어 있지 않으면 호스트가 있어야 하며 사용자 정보,
     * 경로·쿼리·프래그먼트가 섞이거나 원래 authority와 달라지면 거부한다.
     *
     * @param host 양끝 공백을 제거한 Host 헤더 값
     * @return 현재 검사 규칙을 만족하면 true
     */
    private static boolean validHost(String host) {
        if (host.isEmpty()) return true;
        try {
            URI uri = URI.create("http://" + host);
            return uri.getHost() != null && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && uri.getRawPath().isEmpty() && host.equals(uri.getRawAuthority());
        } catch (IllegalArgumentException e) { return false; }
    }

    /**
     * 요청 대상이 현재 파서가 허용하는 ASCII 문자로만 구성되는지 검사한다.
     * <p>물음표와 퍼센트 문자 자체는 허용하며 퍼센트 인코딩의 유효성이나 디코딩은
     * 검사하지 않는다. 슬래시로 시작하는지 여부는 호출자가 별도로 확인한다.
     *
     * @param target 요청 행에서 얻은 경로와 쿼리
     * @return 모든 문자가 허용 집합에 속하면 true
     */
    private static boolean validTarget(String target) {
        for (int c : target.chars().toArray()) {
            if (c == '?') continue;
            if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || "-._~!$&'()*+,;=:@/%".indexOf(c) >= 0)) return false;
        }
        return true;
    }

    /**
     * 메서드와 헤더 이름에 허용하는 HTTP token 구성 문자인지 검사한다.
     * @param c 검사할 문자 코드
     * @return 영문자·숫자 또는 허용 기호이면 true
     */
    private static boolean isToken(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }
    /** 요청 문법 실패를 일반 입출력 실패와 구분해 400 응답으로 변환하기 위한 내부 예외다. */
    private static final class BadRequestException extends IOException {
        /** 요청 문법 오류를 알리는 메시지·원인 없는 예외를 생성한다. */
        private BadRequestException() { }
    }
}
