package simpletomcat.coyote.http11;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import simpletomcat.coyote.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class Http11ProcessorTest {
    private static final String GET = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n";
    private static String process(String input, Adapter adapter) throws IOException {
        var output = new ByteArrayOutputStream();
        new Http11Processor(adapter).process(new ByteArrayInputStream(input.getBytes(StandardCharsets.ISO_8859_1)), output);
        return output.toString(StandardCharsets.UTF_8);
    }
    private static void status(String output, int status) { assertTrue(output.startsWith("HTTP/1.1 " + status + " \r\n"), output); }
    private static String body(String output) { return output.split("\r\n\r\n", 2)[1]; }
    /** 요청 행·쿼리·대소문자가 섞인 복수 헤더를 파싱하여 Adapter에 전달하고 정확히 한 번 호출하는지 검증한다. */
    @Test void parsesRequestAndInvokesAdapterOnce() throws Exception {
        var calls = new AtomicInteger();
        process("GET /hello?name=a HTTP/1.1\r\nHost: localhost\r\nX-Tag: one\r\nx-tag: two\r\n\r\n", (req,res) -> {
            calls.incrementAndGet(); assertEquals("GET", req.getMethod()); assertEquals("/hello", req.getRequestURI());
            assertEquals("name=a", req.getQueryString()); assertEquals("HTTP/1.1", req.getProtocol());
            assertEquals("localhost", req.getHeader("HOST")); assertEquals(List.of("one", "two"),req.getHeaders("X-TAG"));
        }); assertEquals(1,calls.get());
    }
    /** 한글 본문을 UTF-8로 전송하고 Content-Length는 문자 수 대신 16바이트로, Connection은 close로 쓰는지 검증한다. */
    @Test void writesUtf8BodyUsingByteLength() throws Exception {
        String output=process(GET,(req,res)-> { res.setHeader("Content-Type","text/plain; charset=UTF-8"); res.setBody("안녕하세요\n"); });
        status(output,200); assertTrue(output.contains("Content-Length: 16\r\n")); assertTrue(output.contains("Connection: close\r\n"));
        assertArrayEquals("안녕하세요\n".getBytes(StandardCharsets.UTF_8),body(output).getBytes(StandardCharsets.UTF_8));
    }
    /** Adapter가 본문을 설정하지 않으면 빈 본문과 Content-Length 0을 전송하는지 검증한다. */
    @Test void writesEmptyBody() throws Exception { String out=process(GET,(q,s)->{}); assertEquals("",body(out)); assertTrue(out.contains("Content-Length: 0\r\n")); }
    /**
     * 요청 행·헤더 문법 오류, Host 누락·중복, 잘못된 Content-Length와 Transfer-Encoding 충돌을 검증한다.
     * 각 입력을 Adapter 호출 없이 400과 Bad Request 본문으로 거부해야 한다.
     */
    @ParameterizedTest @ValueSource(strings={
        "GET / HTTP/1.1\r\n\r\n", "GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n",
        "GET / HTTP/1.1\r\nHost : a\r\n\r\n", "GET / HTTP/1.1\r\nHost a\r\n\r\n",
        "GET / HTTP/1.1\r\nHost: a\r\n folded\r\n\r\n", "GET / HTTP/1.1\nHost: a\r\n\r\n",
        "GET / HTTP/1.1\r\nHost: a\r\nBad(Name: x\r\n\r\n", "GET / HTTP/1.1\r\nHost: a\r\nX: a\u0000b\r\n\r\n",
        "GET / HTTP/1.1\r\nHost: a\r\nContent-Length: -1\r\n\r\n", "GET / HTTP/1.1\r\nHost: a\r\nContent-Length: x\r\n\r\n",
        "GET / HTTP/1.1\r\nHost: a\r\nContent-Length: 99999999999999999999\r\n\r\n",
        "GET / HTTP/1.1\r\nHost: a\r\nContent-Length: 0\r\nContent-Length: 0\r\n\r\n",
        "GET / HTTP/1.1\r\nHost: a\r\nTransfer-Encoding: chunked\r\nContent-Length: 0\r\n\r\n",
        "GET /\r\n\r\n", "GE(T / HTTP/1.1\r\nHost: a\r\n\r\n", "GET  / HTTP/1.1\r\nHost: a\r\n\r\n",
        "GET /#x HTTP/1.1\r\nHost: a\r\n\r\n", "GET http://a/ HTTP/1.1\r\nHost: a\r\n\r\n",
        "GET / HTTP/1.X\r\nHost: a\r\n\r\n", "\r\nHost: a\r\n\r\n"})
    void rejectsMalformedRequestsWithoutCallingAdapter(String input) throws Exception {
        String out=process(input,(q,s)->fail("Adapter must not run")); status(out,400); assertEquals("Bad Request\n",body(out));
    }
    /** 본문 없는 POST를 GET으로 제한하지 않고 메서드를 그대로 Adapter에 전달하여 200을 반환하는지 검증한다. */
    @Test void passesBodylessPostToAdapter() throws Exception { status(process(GET.replace("GET","POST"),(q,s)->assertEquals("POST",q.getMethod())),200); }
    /** Host 헤더 없는 HTTP/1.0 요청을 허용하고 요청 프로토콜을 보존하여 200을 반환하는지 검증한다. */
    @Test void acceptsHttp10WithoutHost() throws Exception { status(process("GET / HTTP/1.0\r\n\r\n",(q,s)->assertEquals("HTTP/1.0",q.getProtocol())),200); }
    /** 값이 빈 Host 헤더를 허용하고 빈 값을 Adapter에 전달하여 200을 반환하는지 검증한다. */
    @Test void acceptsEmptyHost() throws Exception { status(process(GET.replace("localhost",""),(q,s)->assertEquals("",q.getHeader("Host"))),200); }
    /** 도메인·IPv4·포트가 있는 도메인·대괄호 IPv6의 Host 형식을 각각 허용하여 200을 반환하는지 검증한다. */
    @ParameterizedTest @ValueSource(strings={"localhost","127.0.0.1","localhost:8080","[::1]:8080"})
    void acceptsHostAuthority(String host) throws Exception { status(process(GET.replace("localhost",host),(q,s)->{}),200); }
    /** 공백·사용자 정보·숫자가 아닌 포트·경로가 포함된 Host를 Adapter 호출 없이 400으로 거부하는지 검증한다. */
    @ParameterizedTest @ValueSource(strings={"bad host","user@localhost","localhost:abc","localhost/path"})
    void rejectsHostAuthority(String host) throws Exception { status(process(GET.replace("localhost",host),(q,s)->fail()),400); }
    /** 프로젝트의 본문 미지원 정책에 따라 양수 Content-Length와 chunked 요청을 Adapter 호출 없이 501로 거부하는지 검증한다. */
    @ParameterizedTest @ValueSource(strings={"Content-Length: 1", "Transfer-Encoding: chunked"})
    void rejectsUnsupportedFeatures(String header) throws Exception { status(process(GET.replace("\r\n\r\n","\r\n"+header+"\r\n\r\n"),(q,s)->fail()),501); }
    /** 지원하지 않는 HTTP/1.2·HTTP/2.0 버전을 Adapter 호출 없이 505로 거부하는지 검증한다. */
    @ParameterizedTest @ValueSource(strings={"HTTP/1.2","HTTP/2.0"})
    void rejectsUnsupportedVersions(String version) throws Exception { status(process(GET.replace("HTTP/1.1",version),(q,s)->fail()),505); }
    /** Content-Length 0은 허용하고 경로·쿼리의 퍼센트 인코딩을 디코딩하지 않은 채 Adapter에 전달하는지 검증한다. */
    @Test void acceptsZeroLengthAndPreservesEncodedTarget() throws Exception {
        status(process("GET /hello%20world?name=a%20b HTTP/1.1\r\nHost: a\r\nContent-Length: 0\r\n\r\n",(q,s)-> {
            assertEquals("/hello%20world",q.getRequestURI()); assertEquals("name=a%20b",q.getQueryString());
        }),200);
    }
    /**
     * HEAD는 본문 없이 GET에 해당하는 길이를 쓰고, GET·HEAD의 204·205·304는 본문을 보내지 않는지 검증한다.
     * 205는 Content-Length 0을 쓰며 204·304에서는 Content-Length를 생략해야 한다.
     */
    @Test void suppressesBodyForHeadAndBodylessStatuses() throws Exception {
        String out=process(GET.replace("GET","HEAD"),(q,s)->s.setBody("안녕하세요\n"));
        assertEquals("",body(out)); assertTrue(out.contains("Content-Length: 16\r\n"));
        for (String method:List.of("GET","HEAD")) for(int code:new int[]{204,205,304}) {
            String noBody=process(GET.replace("GET",method),(q,s)->{s.setStatus(code);s.setBody("hidden");});
            status(noBody,code); assertEquals("",body(noBody));
            if(code==205) assertTrue(noBody.contains("Content-Length: 0\r\n")); else assertFalse(noBody.contains("Content-Length:"));
        }
    }
    /** Adapter가 설정한 유효한 최종 상태 404·409를 응답 상태 행에 그대로 반영하는지 검증한다. */
    @Test void preservesCustomFinalStatus() throws Exception { for(int code:new int[]{404,409}) status(process(GET,(q,s)->s.setStatus(code)),code); }
    /** 프로젝트의 최종 상태 검증 정책에 따라 101·999를 최종 응답으로 보내지 않고 500으로 바꾸는지 검증한다. */
    @Test void invalidFinalStatusProduces500() throws Exception { for(int code:new int[]{101,999}) status(process(GET,(q,s)->s.setStatus(code)),500); }
    /** 헤더 종료가 완성되기 전에 입력이 끝나면 Adapter를 호출하거나 응답을 쓰지 않는지 검증한다. */
    @Test void doesNotRespondToIncompleteHeaders() throws Exception { assertEquals("",process(GET.substring(0,GET.length()-1),(q,s)->fail())); }
    /** 연결당 한 요청 정책에 따라 연속된 두 요청 중 첫 요청에 대해서만 Adapter를 호출하는지 검증한다. */
    @Test void processesOnlyFirstRequest() throws Exception { var count=new AtomicInteger(); process(GET+GET,(q,s)->count.incrementAndGet()); assertEquals(1,count.get()); }
    /** 같은 Processor를 재사용해도 이전 응답의 사용자 헤더·본문이 다음 호출의 응답에 남지 않는지 검증한다. */
    @Test void doesNotLeakResponseStateBetweenCalls() throws Exception {
        var count=new AtomicInteger(); var processor=new Http11Processor((q,s)-> { if(count.incrementAndGet()==1){s.setHeader("X-First","yes");s.setBody("one");} });
        var first=new ByteArrayOutputStream(); var second=new ByteArrayOutputStream();
        processor.process(new ByteArrayInputStream(GET.getBytes(StandardCharsets.US_ASCII)),first);
        processor.process(new ByteArrayInputStream(GET.getBytes(StandardCharsets.US_ASCII)),second);
        assertTrue(first.toString().contains("x-first: yes")); assertFalse(second.toString().contains("x-first")); assertTrue(second.toString().contains("Content-Length: 0"));
    }
    /** Adapter의 일반 예외는 기존 헤더·본문을 버리고 500과 Internal Server Error 본문으로 응답하는지 검증한다. */
    @Test void adapterExceptionProduces500() throws Exception {
        for(Exception error:List.of(new IOException("test"),new RuntimeException("test"),new Exception("test"))) {
            String out=process(GET,(q,s)-> {s.setHeader("X-Leak","bad");s.setBody("old");throw error;});
            status(out,500); assertEquals("Internal Server Error\n",body(out)); assertFalse(out.contains("x-leak"));
        }
    }
    /** Adapter의 InterruptedIOException은 응답을 쓰지 않고 호출자에게 그대로 전파하는지 검증한다. */
    @Test void interruptedAdapterDoesNotWriteResponse() {
        var output=new ByteArrayOutputStream();
        assertThrows(InterruptedIOException.class,()->new Http11Processor((q,s)->{throw new InterruptedIOException();}).process(new ByteArrayInputStream(GET.getBytes()),output));
        assertEquals(0,output.size());
    }
    /** 입력 I/O 실패는 응답 없이 전파하고, 출력 I/O 실패도 오류 응답을 재시도하지 않고 한 번의 쓰기 후 전파하는지 검증한다. */
    @Test void propagatesTransportFailure() {
        var output=new ByteArrayOutputStream();
        assertThrows(IOException.class,()->new Http11Processor((q,s)->{}).process(new InputStream(){public int read() throws IOException {throw new IOException();}},output));
        assertEquals(0,output.size()); var writes=new AtomicInteger();
        assertThrows(IOException.class,()->new Http11Processor((q,s)->{}).process(new ByteArrayInputStream(GET.getBytes()),new OutputStream(){public void write(int b)throws IOException{writes.incrementAndGet();throw new IOException();}}));
        assertEquals(1,writes.get());
    }
}
